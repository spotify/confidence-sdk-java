package com.spotify.confidence.common;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.function.Supplier;

public interface Clock extends Supplier<Instant> {

  default Timestamp getTimestamp() {
    final Instant time = get();
    return Timestamp.newBuilder()
        .setSeconds(time.getEpochSecond())
        .setNanos(time.getNano())
        .build();
  }
}
