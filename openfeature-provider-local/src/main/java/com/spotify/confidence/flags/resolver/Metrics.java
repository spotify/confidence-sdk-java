package com.spotify.confidence.flags.resolver;

import java.time.Duration;

public interface Metrics {
  void markResolveTokenSize(int sizeInBytes);

  void markFlagApplyTime(Duration elapsed);

  void markAppliedFlagCount(int flagsCount);

  void markUnauthorizedRequest(String clientSecret);

  void failedMaterializationLoad(Throwable rootCause);

  void markAccountMismatch(String accountName);
}
