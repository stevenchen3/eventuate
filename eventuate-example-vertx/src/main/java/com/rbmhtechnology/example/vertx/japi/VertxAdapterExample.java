/*
 * Copyright 2015 - 2016 Red Bull Media House GmbH <http://www.redbullmediahouse.com> - all rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rbmhtechnology.example.vertx.japi;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import com.rbmhtechnology.eventuate.AbstractEventsourcedView;
import com.rbmhtechnology.eventuate.ApplicationVersion;
import com.rbmhtechnology.eventuate.ReplicationEndpoint;
import com.rbmhtechnology.eventuate.adapter.vertx.*;
import com.rbmhtechnology.eventuate.adapter.vertx.japi.rx.LogAdapterService;
import com.rbmhtechnology.eventuate.adapter.vertx.japi.rx.StorageProvider;
import com.rbmhtechnology.eventuate.log.EventLogWriter;
import com.rbmhtechnology.eventuate.log.leveldb.LeveldbEventLog;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.buffer.Buffer;
import javaslang.collection.List;
import rx.Observable;
import scala.collection.immutable.Map$;
import scala.collection.immutable.Set;

import java.io.File;
import java.time.Duration;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;

import static akka.pattern.Patterns.ask;
import static com.rbmhtechnology.example.vertx.japi.ScalaCollections.sequence;
import static com.rbmhtechnology.example.vertx.japi.ScalaCollections.set;
import static java.lang.System.out;
import static scala.compat.java8.JFunction.func;
import static scala.compat.java8.JFunction.proc;

public class VertxAdapterExample {
  private static final String LOG_A = "log_Rx_A";
  private static final String LOG_B = "log_Rx_B";
  private static final String PROCESSOR = "processor-verticle";
  private static final int EVENT_COUNT = 10;

  public static void main(final String[] args) {
    final ActorSystem system = ActorSystem.create("location");
    final Vertx vertx = Vertx.vertx();

    final ReplicationEndpoint endpoint = createReplicationEndpoint("id1", set(LOG_A, LOG_B),
      (String logId) -> LeveldbEventLog.props(logId, "log", true), system);

    final VertxEventbusAdapter adapter = VertxEventbusAdapter.create(AdapterConfig.of(
      LogAdapter.readFrom(LOG_A).sendTo(PROCESSOR).withConfirmedDelivery(Duration.ofSeconds(3)),
      LogAdapter.writeTo(LOG_B),
      LogAdapter.readFrom(LOG_B).publish()),
      endpoint, vertx, new DiskStorageProvider("target/progress/vertx-rx-java", vertx), system);

    deployVerticles(vertx).subscribe(
      res -> adapter.activate(),
      err -> out.println(String.format("Vertx startup failed with %s", err))
    );

    final EventLogWriter writer = new EventLogWriter("writer", endpoint.logs().apply(LOG_A), system);
    final ActorRef reader = system.actorOf(Props.create(EventLogReader.class,
      () -> new EventLogReader("reader", endpoint.logs().apply(LOG_B), EVENT_COUNT)));

    final String runId = UUID.randomUUID().toString().substring(0, 5);
    for (int i = 1; i <= EVENT_COUNT; i++) {
      final String event = "event[" + runId + "]-" + i;
      writer.write(sequence(event));
      sleep(100);
    }

    ask(reader, "notifyOnComplete", Duration.ofMinutes(5).toMillis())
      .onComplete(proc(result -> {
        sleep(500);
        vertx.close();
        system.terminate();
        out.println("--- finished ---");
      }), system.dispatcher());
  }

  public static class ProcessorVerticle extends AbstractVerticle {

    private final Random r = new Random();

    @Override
    public void start() throws Exception {
      final LogAdapterService<ConfirmableEvent> readService = LogAdapterService.create(LOG_A, PROCESSOR, vertx);
      final LogAdapterService<Event> writeService = LogAdapterService.create(LOG_B, vertx);

      readService.onEvent()
        .filter(this::shouldPass)
        .doOnNext(ev -> out.println(String.format("[v_processor] processed [%s]", ev.payload())))
        .flatMap(ev -> writeService.persist("*processed*" + ev.payload()).map(x -> ev))
        .subscribe(
          ConfirmableEvent::confirm,
          err -> out.println(String.format("[verticle] persist failed with: %s", err.getMessage()))
        );
    }

    private Boolean shouldPass(final ConfirmableEvent ev) {
      if (r.nextFloat() < 0.4) {
        out.println(String.format("[v_processor] dropped   [%s]", ev.payload()));
        return false;
      }
      return true;
    }
  }

  public static class ReaderVerticle extends AbstractVerticle {
    @Override
    public void start() throws Exception {
      final LogAdapterService<Event> readService = LogAdapterService.create(LOG_B, vertx);

      readService.onEvent()
        .subscribe(
          ev -> out.println(String.format("[%s]  received  [%s]", config().getString("name"), ev.payload()))
        );
    }
  }

  public static class EventLogReader extends AbstractEventsourcedView {

    private List<ActorRef> subscribers = List.empty();
    private int eventsRead = 0;

    public EventLogReader(String id, ActorRef eventLog, int eventCount) {
      super(id, eventLog);

      setOnCommand(ReceiveBuilder
        .matchEquals("notifyOnComplete", s -> subscribers = subscribers.prepend(sender()))
        .matchEquals("eventRead", e -> {
          eventsRead = eventsRead + 1;
          if (eventsRead == eventCount) {
            subscribers.forEach(s -> s.tell("finished", self()));
          }
        })
        .build());

      setOnEvent(ReceiveBuilder
        .matchAny(ev -> {
          out.println(String.format("[e_reader]    received  [%s]", ev));

          if (!recovering()) {
            self().tell("eventRead", self());
          }
        })
        .build());
    }
  }

  public static class DiskStorageProvider implements StorageProvider {
    private final Vertx vertx;
    private final String path;

    public DiskStorageProvider(String path, Vertx vertx) {
      this.vertx = vertx;
      this.path = path;

      new File(path).mkdirs();
    }

    @Override
    public Observable<Long> readProgress(String logName) {
      return vertx.fileSystem().readFileObservable(path(logName))
        .map(v -> Long.valueOf(v.toString()))
        .onErrorReturn(err -> 0L);
    }

    @Override
    public Observable<Long> writeProgress(String logName, Long sequenceNr) {
      return vertx.fileSystem().writeFileObservable(path(logName), Buffer.buffer(sequenceNr.toString()))
        .map(x -> sequenceNr);
    }

    private String path(final String logName) {
      return String.format("%s/progress-%s.txt", path, logName);
    }
  }

  private static Observable<String> deployVerticles(Vertx vertx) {
    return Observable.zip(
      deployVerticle(ProcessorVerticle.class, new JsonObject(), vertx),
      deployVerticle(ReaderVerticle.class, new JsonObject().put("name", "v_reader-1"), vertx),
      deployVerticle(ReaderVerticle.class, new JsonObject().put("name", "v_reader-2"), vertx),
      (i1, i2, i3) -> i1
    );
  }

  private static <T> Observable<String> deployVerticle(final Class<T> clazz, final JsonObject config, final Vertx vertx) {
    return vertx.deployVerticleObservable(clazz.getName(), new DeploymentOptions().setConfig(config));
  }

  private static ReplicationEndpoint createReplicationEndpoint(final String id,
                                                               final Set<String> logNames,
                                                               final Function<String, Props> logFactory,
                                                               final ActorSystem system) {
    return new ReplicationEndpoint(id, logNames, func(logFactory::apply), set(), Map$.MODULE$.empty(), "default",
      ApplicationVersion.apply("0.1"), system);
  }

  private static void sleep(int durationInMillis) {
    try {
      Thread.sleep(durationInMillis);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
