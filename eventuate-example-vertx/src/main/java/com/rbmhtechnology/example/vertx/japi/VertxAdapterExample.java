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
import com.rbmhtechnology.eventuate.adapter.vertx.VertxAdapterSystem;
import com.rbmhtechnology.eventuate.adapter.vertx.api.VertxAdapterSystemConfig;
import com.rbmhtechnology.eventuate.adapter.vertx.japi.ConfirmationType;
import com.rbmhtechnology.eventuate.adapter.vertx.japi.VertxAdapterConfig;
import com.rbmhtechnology.eventuate.adapter.vertx.japi.rx.StorageProvider;
import com.rbmhtechnology.eventuate.log.EventLogWriter;
import com.rbmhtechnology.eventuate.log.leveldb.LeveldbEventLog;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.buffer.Buffer;
import io.vertx.rxjava.core.eventbus.Message;
import javaslang.collection.HashSet;
import javaslang.collection.List;
import rx.Observable;
import scala.collection.immutable.Map$;
import scala.collection.immutable.Set;

import java.io.File;
import java.time.Duration;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static akka.pattern.Patterns.ask;
import static com.rbmhtechnology.example.vertx.japi.ScalaCollections.sequence;
import static com.rbmhtechnology.example.vertx.japi.ScalaCollections.set;
import static java.lang.System.out;
import static scala.compat.java8.JFunction.func;
import static scala.compat.java8.JFunction.proc;

public class VertxAdapterExample {

  private static class Endpoints {
    static final String PROCESSOR = "eb-address:logA-processor";
    static final String PUBLISH_RECEIVER = "eb-address:logB-publish-receiver";
    static final String WRITER = "eb-address:logB-writer";
  }

  private static class LogNames {
    static final String LOG_A = "log_Rx_A";
    static final String LOG_B = "log_Rx_B";
  }

  private static final int EVENT_COUNT = 10;

  public static void main(final String[] args) {
    final ActorSystem system = ActorSystem.create("location");
    final Vertx vertx = Vertx.vertx();

    final ReplicationEndpoint endpoint = createReplicationEndpoint("id1", set(LogNames.LOG_A, LogNames.LOG_B),
      (String logId) -> LeveldbEventLog.props(logId, "log", true), system);
    final ActorRef logA = endpoint.logs().apply(LogNames.LOG_A);
    final ActorRef logB = endpoint.logs().apply(LogNames.LOG_B);

    final VertxAdapterSystem adapterSystem = VertxAdapterSystem.create(VertxAdapterSystemConfig.create(
      VertxAdapterConfig.fromLog(logA)
        .sendTo(Endpoints.PROCESSOR)
        .atLeastOnce(ConfirmationType.Batch.withSize(2), Duration.ofSeconds(2))
        .as("logA-processor"),
      VertxAdapterConfig.fromEndpoints(Endpoints.WRITER)
        .writeTo(logB)
        .as("logB-writer"),
      VertxAdapterConfig.fromLog(logB)
        .publishTo(Endpoints.PUBLISH_RECEIVER)
        .as("logB-publisher")
    ), vertx, new DiskStorageProvider("target/progress/vertx-rx-java", vertx), system);

    deployVerticles(vertx).subscribe(
      res -> {
        endpoint.activate();
        adapterSystem.start();
      },
      err -> out.println(String.format("Vertx startup failed with %s", err))
    );

    final EventLogWriter writer = new EventLogWriter("writer", logA, system);
    final ActorRef reader = system.actorOf(Props.create(EventLogReader.class,
      () -> new EventLogReader("reader", logB, EVENT_COUNT)));

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
      vertx.eventBus().<String>consumer(Endpoints.PROCESSOR).toObservable()
        .filter(this::shouldPass)
        .compose(executeIdempotent(this::persist))
        .subscribe(
          m -> m.reply(null),
          err -> out.println(String.format("[verticle] persist failed with: %s", err.getMessage()))
        );
    }

    private <T> Boolean shouldPass(final Message<T> m) {
      if (r.nextFloat() < 0.4) {
        out.println(String.format("[v_processor] dropped   [%s]", m.body()));
        return false;
      }
      return true;
    }

    private Observable<Message<String>> persist(final Message<String> m) {
      out.println(String.format("[v_processor] processed [%s]", m.body()));
      return vertx.eventBus().<String>sendObservable(Endpoints.WRITER, "*processed*" + m.body())
        .map(x -> m);
    }

    private <T> Observable.Transformer<Message<T>, Message<T>> executeIdempotent(final Function<Message<T>, Observable<Message<T>>> f) {
      final AtomicReference<javaslang.collection.Set<T>> processing = new AtomicReference<>(HashSet.empty());
      final AtomicReference<javaslang.collection.Set<T>> processed = new AtomicReference<>(HashSet.empty());

      return obs -> obs.flatMap(m -> {
        if (processed.get().contains(m.body())) {
          return Observable.just(m);
        } else if (processing.get().contains(m.body())) {
          return Observable.empty();
        }

        processing.getAndUpdate(p -> p.add(m.body()));
        return f.apply(m)
          .doOnNext(e -> {
            processing.getAndUpdate(p -> p.remove(m.body()));
            processed.getAndUpdate(p -> p.add(m.body()));
          })
          .doOnError(e -> processing.getAndUpdate(p -> p.remove(m.body())))
          .onErrorResumeNext(err -> Observable.empty());
      });
    }
  }

  public static class ReaderVerticle extends AbstractVerticle {
    @Override
    public void start() throws Exception {
      vertx.eventBus().<String>consumer(Endpoints.PUBLISH_RECEIVER).toObservable()
        .subscribe(
          m -> out.println(String.format("[%s]  received  [%s]", config().getString("name"), m.body()))
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
