package com.spotify.confidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

public class ConfidenceContextTest {

  private final FakeEventSenderEngine fakeEngine = new FakeEventSenderEngine(new FakeClock());
  private final ResolverClientTestUtils.FakeFlagResolverClient fakeFlagResolverClient =
      new ResolverClientTestUtils.FakeFlagResolverClient();

  @Test
  public void getContextContainsParentContextValues() {
    final Confidence root = new Confidence(null, fakeEngine, fakeFlagResolverClient);
    root.updateContextEntry("page", ConfidenceValue.of("http://.."));
    final EventSender confidence =
        root.withContext(ImmutableMap.of("pants", ConfidenceValue.of("yellow")));

    assertThat(confidence.getContext())
        .isEqualTo(
            ConfidenceValue.Struct.of(
                ImmutableMap.of(
                    "page",
                    ConfidenceValue.of("http://.."),
                    "pants",
                    ConfidenceValue.of("yellow"))));
  }

  @Test
  public void setContextOverwritesContext() {
    final Confidence root = new Confidence(null, fakeEngine, fakeFlagResolverClient);
    root.updateContextEntry("page", ConfidenceValue.of("http://.."));
    final EventSender confidence =
        root.withContext(ImmutableMap.of("pants", ConfidenceValue.of("yellow")));

    assertThat(confidence.getContext())
        .isEqualTo(
            ConfidenceValue.Struct.of(
                ImmutableMap.of(
                    "page",
                    ConfidenceValue.of("http://.."),
                    "pants",
                    ConfidenceValue.of("yellow"))));

    confidence.setContext(ImmutableMap.of("shirt", ConfidenceValue.of("blue")));
    assertThat(confidence.getContext())
        .isEqualTo(
            ConfidenceValue.Struct.of(
                ImmutableMap.of(
                    "page", ConfidenceValue.of("http://.."), "shirt", ConfidenceValue.of("blue"))));
  }

  @Test
  public void parentContextFieldCanBeOverridden() {
    final Confidence root = new Confidence(null, fakeEngine, fakeFlagResolverClient);
    root.updateContextEntry("pants-color", ConfidenceValue.of("yellow"));
    final EventSender confidence =
        root.withContext(ImmutableMap.of("pants-color", ConfidenceValue.of("blue")));

    // run assert on child
    assertThat(confidence.getContext())
        .isEqualTo(
            ConfidenceValue.Struct.of(ImmutableMap.of("pants-color", ConfidenceValue.of("blue"))));
    // run assert on parent
    assertThat(root.getContext())
        .isEqualTo(
            ConfidenceValue.Struct.of(
                ImmutableMap.of("pants-color", ConfidenceValue.of("yellow"))));
  }

  @Test
  public void parentContextFieldCanBeOverriddenOrRemoved() {
    final Confidence root = new Confidence(null, fakeEngine, fakeFlagResolverClient);
    root.updateContextEntry("pants-color", ConfidenceValue.of("yellow"));
    final EventSender confidence =
        root.withContext(ImmutableMap.of("shirt-color", ConfidenceValue.of("blue")));

    confidence.removeContextEntry("pants-color");

    assertThat(confidence.getContext().asMap().size()).isEqualTo(1);
    assertThat(confidence.getContext())
        .isEqualTo(
            ConfidenceValue.Struct.of(ImmutableMap.of("shirt-color", ConfidenceValue.of("blue"))));
  }
}
