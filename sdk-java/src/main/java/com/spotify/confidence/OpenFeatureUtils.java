package com.spotify.confidence;

import com.google.common.annotations.Beta;
import com.google.protobuf.Struct;
import com.google.protobuf.util.Values;
import dev.openfeature.sdk.EvaluationContext;
import io.grpc.netty.shaded.io.netty.util.internal.StringUtil;

@Beta
public class OpenFeatureUtils {

  static final String TARGETING_KEY = "targeting_key";

  public static ConfidenceValue.Struct convert(EvaluationContext evaluationContext) {
    return ConfidenceValue.Struct.fromProto(convertToProto(evaluationContext));
  }

  static Struct convertToProto(EvaluationContext evaluationContext) {
    final Struct.Builder protoEvaluationContext = Struct.newBuilder();
    evaluationContext
        .asMap()
        .forEach(
            (mapKey, mapValue) -> {
              protoEvaluationContext.putFields(mapKey, TypeMapper.from(mapValue));
            });
    // add targeting key as a regular value to proto struct
    if (!StringUtil.isNullOrEmpty(evaluationContext.getTargetingKey())) {
      protoEvaluationContext.putFields(
          TARGETING_KEY, Values.of(evaluationContext.getTargetingKey()));
    }
    return protoEvaluationContext.build();
  }
}
