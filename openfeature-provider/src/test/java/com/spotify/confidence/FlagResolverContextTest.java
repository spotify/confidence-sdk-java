package com.spotify.confidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Struct;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import dev.openfeature.sdk.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FlagResolverContextTest {
  private FakeFlagResolver fakeFlagResolver;
  private Client client;
  private Confidence confidence;

  @BeforeEach
  void beforeEach() {
    final FakeEventSenderEngine fakeEventSender = new FakeEventSenderEngine(new FakeClock());
    this.fakeFlagResolver = new FakeFlagResolver();
    final FlagResolverClientImpl flagResolver = new FlagResolverClientImpl(fakeFlagResolver);
    this.confidence = Confidence.create(fakeEventSender, flagResolver, "clientKey");
    final FeatureProvider featureProvider = new ConfidenceFeatureProvider(confidence);

    final OpenFeatureAPI openFeatureAPI = OpenFeatureAPI.getInstance();
    openFeatureAPI.setProvider(featureProvider);

    client = openFeatureAPI.getClient();
  }

  @Test
  public void resolveWithOpenFeatureContextIncludesAllContext() {
    final EvaluationContext SAMPLE_CONTEXT =
        new MutableContext("my-targeting-key", Map.of("my-key", new Value(true)));
    confidence.updateContextEntry("my-context", ConfidenceValue.of(2));
    client.getBooleanDetails("flag.prop-A", true, SAMPLE_CONTEXT);
    assertThat(fakeFlagResolver.context.containsFields("my-context")).isTrue();
    assertThat(fakeFlagResolver.context.containsFields("my-key")).isTrue();
    assertThat(fakeFlagResolver.context.getFieldsMap().get("my-context").getNumberValue())
        .isEqualTo(2);
  }
}

class FakeFlagResolver implements FlagResolver {
  Struct context;

  @Override
  public void close() {}

  @Override
  public CompletableFuture<ResolveFlagsResponse> resolve(
      String flag, Struct context, Boolean isProvider) {
    this.context = context;
    return null;
  }
}
