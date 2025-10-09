package com.spotify.confidence;

import com.google.common.annotations.Beta;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Interface for sending events to Confidence with context. Extends {@link Contextual} to provide
 * context-aware event tracking capabilities.
 */
@Beta
public interface EventSender extends Contextual {
  /**
   * Tracks an event with associated data.
   *
   * @param eventName The name of the event to track
   * @param data The data associated with the event
   */
  public void track(@Nonnull String eventName, @Nonnull ConfidenceValue.Struct data);

  /**
   * Tracks an event without associated data.
   *
   * @param eventName The name of the event to track
   */
  public void track(@Nonnull String eventName);

  /** Flushes any pending events to ensure they are sent. */
  void flush();

  /**
   * Creates a new instance with the specified context.
   *
   * @param context The new context to set
   * @return A new instance with the specified context
   */
  @Nonnull
  @Override
  EventSender withContext(@Nonnull ConfidenceValue.Struct context);

  /**
   * Creates a new instance with the specified context map.
   *
   * @param context Map of context key-value pairs
   * @return A new instance with the specified context
   */
  @Nonnull
  @Override
  default EventSender withContext(@Nonnull Map<String, ConfidenceValue> context) {
    return withContext(ConfidenceValue.Struct.of(context));
  }
}
