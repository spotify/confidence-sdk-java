package com.spotify.confidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ConfidenceContextTest {

  private final FakeEngine fakeEngine = new FakeEngine();

  @Test
  public void getContextContainsParentContextValues() {
    final Confidence root = new Confidence(null, fakeEngine);
    root.updateContext("page", ConfidenceValue.of("http://.."));
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
    final Confidence root = new Confidence(null, fakeEngine);
    root.updateContext("page", ConfidenceValue.of("http://.."));
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
    final Confidence root = new Confidence(null, fakeEngine);
    root.updateContext("pants-color", ConfidenceValue.of("yellow"));
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
    final Confidence root = new Confidence(null, fakeEngine);
    root.updateContext("pants-color", ConfidenceValue.of("yellow"));
    final EventSender confidence =
        root.withContext(ImmutableMap.of("shirt-color", ConfidenceValue.of("blue")));

    confidence.removeContext("pants-color");

    assertThat(confidence.getContext().asMap().size()).isEqualTo(1);
    assertThat(confidence.getContext())
        .isEqualTo(
            ConfidenceValue.Struct.of(ImmutableMap.of("shirt-color", ConfidenceValue.of("blue"))));
  }

  private static class FakeEngine implements EventSenderEngine {

    List<Event> events = new ArrayList<>();
    boolean closed;

    @Override
    public void close() throws IOException {
      this.closed = true;
    }

    @Override
    public void send(String name, ConfidenceValue.Struct context) {
      send(name, ConfidenceValue.Struct.EMPTY, context);
    }

    @Override
    public void send(String name, ConfidenceValue.Struct message, ConfidenceValue.Struct context) {
      events.add(new Event(name, message, context));
    }
  }
}
