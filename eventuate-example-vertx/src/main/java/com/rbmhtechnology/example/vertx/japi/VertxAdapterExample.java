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

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.rbmhtechnology.eventuate.ApplicationVersion;
import com.rbmhtechnology.eventuate.ReplicationEndpoint;
import com.rbmhtechnology.eventuate.adapter.vertx.AdapterConfig;
import com.rbmhtechnology.eventuate.adapter.vertx.Event;
import com.rbmhtechnology.eventuate.adapter.vertx.LogAdapter;
import com.rbmhtechnology.eventuate.adapter.vertx.VertxEventbusAdapter;
import com.rbmhtechnology.eventuate.adapter.vertx.japi.rx.LogAdapterService;
import com.rbmhtechnology.eventuate.adapter.vertx.japi.rx.StorageProvider;
import com.rbmhtechnology.eventuate.log.EventLogWriter;
import com.rbmhtechnology.eventuate.log.leveldb.LeveldbEventLog;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.Vertx;
import rx.Observable;
import scala.collection.immutable.*;
import scala.runtime.AbstractFunction1;

import java.util.UUID;

public class VertxAdapterExample {

  public static void main(final String[] args) throws InterruptedException {
    final ActorSystem system = ActorSystem.create("location");
    final Vertx vertx = Vertx.vertx();

    final UUID runId = UUID.randomUUID();
    final String logName = "logB";

    final ReplicationEndpoint endpoint = new ReplicationEndpoint("id1", set(logName),
      new AbstractFunction1<String, Props>() {
        public Props apply(String logId) {
          return LeveldbEventLog.props(logId, "log", true);
        }
      }, set(), Map$.MODULE$.empty(), "default", ApplicationVersion.apply("0.1"), system);

    final EventLogWriter writer = new EventLogWriter("writer", endpoint.logs().get(logName).get(), system);
    final VertxEventbusAdapter adapter =
      VertxEventbusAdapter.create(AdapterConfig.of(LogAdapter.readFrom(logName).publish()), endpoint, vertx, new FakeStorageProvider(), system);

    vertx.deployVerticle(TestVerticle.class.getName(), res -> {
      if (res.failed()) {
        System.out.println(String.format("Vertx startup failed with %s", res.cause()));
      } else {
        adapter.activate();
      }
    });

    for (int i = 1; i <= 100; i++) {
      final String event = "event[" + runId + "]-" + i;
      writer.write(seq(event));
      Thread.sleep(100);
    }

    vertx.close();
    system.terminate();
  }

  public static class TestVerticle extends AbstractVerticle {

    @Override
    public void start() throws Exception {
      final LogAdapterService<Event> service = LogAdapterService.create("logB", vertx);

      service.onEvent()
        .subscribe(ev ->
          System.out.println(String.format("verticle received event: %s", ev.payload()))
        );
    }
  }

  public static class FakeStorageProvider implements StorageProvider {

    @Override
    public Observable<Long> readProgress(String logName) {
      return Observable.just(0L);
    }

    @Override
    public Observable<Long> writeProgress(String logName, Long sequenceNr) {
      return Observable.just(sequenceNr);
    }
  }

  @SafeVarargs
  private static <T> List<T> list(final T... objects) {
    List<T> l = List$.MODULE$.empty();

    for (T object : objects) {
      l = l.$colon$colon(object);
    }
    return l;
  }

  @SafeVarargs
  private static <T> Set<T> set(final T... objects) {
    return list(objects).toSet();
  }

  @SafeVarargs
  private static <T> Seq<T> seq(final T... objects) {
    return list(objects).toSeq();
  }
}
