package com.rbmhtechnology.example.vertx.japi;

import com.rbmhtechnology.eventuate.adapter.vertx.japi.rx.StorageProvider;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.buffer.Buffer;
import rx.Observable;

import java.io.File;

public class DiskStorageProvider implements StorageProvider {
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
