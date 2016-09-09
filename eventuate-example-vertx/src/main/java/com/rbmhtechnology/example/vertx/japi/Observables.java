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
