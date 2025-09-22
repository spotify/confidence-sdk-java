package com.spotify.confidence;

/**
 * Configuration options for the OpenFeature local resolve provider.
 *
 * <p>This class provides configuration options that control the behavior of the {@link
 * OpenFeatureLocalResolveProvider}, including console logging levels and other provider-specific
 * settings.
 *
 * @since 0.2.4
 */
public class ProviderOptions {

  /**
   * Enumeration of available console logging levels for the provider.
   *
   * <p>The console logging level controls which log messages are output to the console by the
   * provider and its underlying components. The default level is INFO, which includes all levels
   * above debug (INFO, WARN, ERROR).
   *
   * <p>Note: This setting only affects console logging output. It does not impact the assign and
   * resolve logs that are sent as network requests to the Confidence service for telemetry
   * purposes.
   */
  public enum LoggingLevel {
    /** All logging levels enabled, including TRACE and DEBUG messages */
    ALL,
    /** TRACE level and above (TRACE, DEBUG, INFO, WARN, ERROR) */
    TRACE,
    /** DEBUG level and above (DEBUG, INFO, WARN, ERROR) */
    DEBUG,
    /** INFO level and above (INFO, WARN, ERROR) - this is the default */
    INFO,
    /** WARN level and above (WARN, ERROR) */
    WARN,
    /** ERROR level only */
    ERROR,
    /** No logging output */
    OFF
  }

  private final LoggingLevel loggingLevel;

  /**
   * Creates provider options with the specified logging level.
   *
   * @param loggingLevel the logging level to use for the provider
   */
  public ProviderOptions(LoggingLevel loggingLevel) {
    this.loggingLevel = loggingLevel != null ? loggingLevel : LoggingLevel.INFO;
  }

  /**
   * Creates provider options with default settings.
   *
   * <p>The default logging level is INFO, which includes all levels above debug (INFO, WARN,
   * ERROR).
   */
  public ProviderOptions() {
    this(LoggingLevel.INFO);
  }

  /**
   * Gets the configured logging level.
   *
   * @return the logging level
   */
  public LoggingLevel getLoggingLevel() {
    return loggingLevel;
  }

  /**
   * Creates a new ProviderOptions instance with the specified logging level.
   *
   * @param loggingLevel the logging level to use
   * @return a new ProviderOptions instance
   */
  public static ProviderOptions withLoggingLevel(LoggingLevel loggingLevel) {
    return new ProviderOptions(loggingLevel);
  }

  /**
   * Creates a new ProviderOptions instance with default settings.
   *
   * <p>The default logging level is INFO, which includes all levels above debug (INFO, WARN,
   * ERROR).
   *
   * @return a new ProviderOptions instance with default settings
   */
  public static ProviderOptions defaults() {
    return new ProviderOptions();
  }

  @Override
  public String toString() {
    return "ProviderOptions{" + "loggingLevel=" + loggingLevel + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final ProviderOptions that = (ProviderOptions) o;
    return loggingLevel == that.loggingLevel;
  }

  @Override
  public int hashCode() {
    return loggingLevel != null ? loggingLevel.hashCode() : 0;
  }
}
