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

import io.vertx.rxjava.core.eventbus.Message;
import javaslang.collection.HashSet;
import javaslang.collection.Set;
import rx.Observable;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class Observables {

  static <T> Observable.Transformer<Message<T>, Message<T>> executeIdempotent(final Function<Message<T>, Observable<Message<T>>> f) {
    final AtomicReference<Set<T>> processing = new AtomicReference<>(HashSet.empty());
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
