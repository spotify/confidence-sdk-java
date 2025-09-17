package com.spotify.confidence;

import com.google.protobuf.Struct;
import com.spotify.confidence.shaded.flags.resolver.v1.Sdk;
import java.util.List;

public class NoopFlagLogger implements FlagLogger {

  @Override
  public void logResolve(
      String resolveId,
      Struct evaluationContext,
      Sdk sdk,
      AccountClient accountClient,
      List<ResolvedValue> values) {
    // no-op
  }

  @Override
  public void logAssigns(
      String resolveId, Sdk sdk, List<FlagToApply> flagsToApply, AccountClient accountClient) {
    // no-op
  }
}
