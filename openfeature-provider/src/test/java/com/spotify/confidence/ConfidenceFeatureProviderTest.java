package com.spotify.confidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfidenceFeatureProviderTest {

  private Confidence root;
  private FakeEventSenderEngine fakeEngine;
  private ResolverClientTestUtils.FakeFlagResolverClient fakeFlagResolverClient;

  @BeforeEach
  public void setup() {
    fakeEngine = new FakeEventSenderEngine(new FakeClock());
    fakeFlagResolverClient = new ResolverClientTestUtils.FakeFlagResolverClient();
    root = Confidence.create(fakeEngine, fakeFlagResolverClient);
  }

  @Test
  public void testCloseChildShouldReturnDefaultsFromOpenFeatureApi() throws IOException {
    final Confidence child = root.withContext(Map.of("child-key", ConfidenceValue.of("child")));
    OpenFeatureAPI.getInstance().setProvider(new ConfidenceFeatureProvider(child));
    child.close();
    final boolean defaultValue = false;
    final FlagEvaluationDetails<Boolean> booleanDetails =
        OpenFeatureAPI.getInstance()
            .getClient()
            .getBooleanDetails(
                "some-flag",
                defaultValue,
                new ImmutableContext("some-key", Map.of("some", new Value("value"))));
    assertThat(booleanDetails.getValue()).isEqualTo(defaultValue);
    assertThat(booleanDetails.getReason()).isEqualTo(Reason.ERROR.name());
    assertThat(booleanDetails.getErrorCode()).isEqualTo(ErrorCode.GENERAL);
    assertThat(booleanDetails.getErrorMessage()).isEqualTo("Resource closed");
  }
}
