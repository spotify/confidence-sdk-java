package com.spotify.confidence;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.function.Supplier;

interface Clock extends Supplier<Instant> {

  default Timestamp getTimestamp() {
    final Instant time = get();
    return Timestamp.newBuilder()
        .setSeconds(time.getEpochSecond())
        .setNanos(time.getNano())
        .build();
  }
}
