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
    final Confidence root = Confidence.create(fakeEngine, fakeFlagResolverClient);
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
    final Confidence root = Confidence.create(fakeEngine, fakeFlagResolverClient);
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
    final Confidence root = Confidence.create(fakeEngine, fakeFlagResolverClient);
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
    final Confidence root = Confidence.create(fakeEngine, fakeFlagResolverClient);
    root.updateContextEntry("pants-color", ConfidenceValue.of("yellow"));
    final EventSender confidence =
        root.withContext(ImmutableMap.of("shirt-color", ConfidenceValue.of("blue")));

    confidence.removeContextEntry("pants-color");

    assertThat(confidence.getContext().asMap().size()).isEqualTo(1);
    assertThat(confidence.getContext())
        .isEqualTo(
            ConfidenceValue.Struct.of(ImmutableMap.of("shirt-color", ConfidenceValue.of("blue"))));
  }

  @Test
  public void multiLevelContexts() {
    final Confidence root = Confidence.create(fakeEngine, fakeFlagResolverClient);
    final int numberOfLevels = 9;
    Confidence last = root;
    for (int i = 0; i < numberOfLevels; i++) {
      last =
          last.withContext(
              ImmutableMap.of(
                  "level", ConfidenceValue.of(i), "level_" + i, ConfidenceValue.of("i=" + i)));
    }

    assertThat(last.getContext().asMap().size()).isEqualTo(numberOfLevels + 1);
    assertThat(last.getContext().asMap())
        .isEqualTo(
            ImmutableMap.of(
                "level", ConfidenceValue.of(numberOfLevels - 1),
                "level_0", ConfidenceValue.of("i=0"),
                "level_1", ConfidenceValue.of("i=1"),
                "level_2", ConfidenceValue.of("i=2"),
                "level_3", ConfidenceValue.of("i=3"),
                "level_4", ConfidenceValue.of("i=4"),
                "level_5", ConfidenceValue.of("i=5"),
                "level_6", ConfidenceValue.of("i=6"),
                "level_7", ConfidenceValue.of("i=7"),
                "level_8", ConfidenceValue.of("i=8")));
  }
}
