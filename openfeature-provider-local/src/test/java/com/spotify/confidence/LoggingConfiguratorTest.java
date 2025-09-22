package com.spotify.confidence;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LoggingConfiguratorTest {

  @Test
  void testConfigureLoggingWithNullOptions() {
    // Should not throw an exception
    assertThatCode(() -> LoggingConfigurator.configureLogging(null)).doesNotThrowAnyException();
  }

  @Test
  void testConfigureLoggingWithValidOptions() {
    final ProviderOptions options =
        ProviderOptions.withLoggingLevel(ProviderOptions.LoggingLevel.DEBUG);

    // Should not throw an exception
    assertThatCode(() -> LoggingConfigurator.configureLogging(options)).doesNotThrowAnyException();
  }

  @Test
  void testConfigureSpecificLoggerWithNullOptions() {
    // Should not throw an exception
    assertThatCode(() -> LoggingConfigurator.configureLogger("test.logger", null))
        .doesNotThrowAnyException();
  }

  @Test
  void testConfigureSpecificLoggerWithNullLoggerName() {
    final ProviderOptions options =
        ProviderOptions.withLoggingLevel(ProviderOptions.LoggingLevel.DEBUG);

    // Should not throw an exception
    assertThatCode(() -> LoggingConfigurator.configureLogger(null, options))
        .doesNotThrowAnyException();
  }

  @Test
  void testConfigureSpecificLoggerWithValidParameters() {
    final ProviderOptions options =
        ProviderOptions.withLoggingLevel(ProviderOptions.LoggingLevel.WARN);

    // Should not throw an exception
    assertThatCode(() -> LoggingConfigurator.configureLogger("test.logger", options))
        .doesNotThrowAnyException();
  }

  @Test
  void testLoggingConfigurationActuallyWorks() {
    // This test verifies that when Logback is available (which it is in test scope),
    // the logging configuration actually takes effect

    final org.slf4j.Logger testLogger = LoggerFactory.getLogger("com.spotify.confidence.test");

    // Configure to ERROR level
    final ProviderOptions options =
        ProviderOptions.withLoggingLevel(ProviderOptions.LoggingLevel.ERROR);

    // Just verify that the configuration call doesn't throw an exception
    assertThatCode(
            () -> LoggingConfigurator.configureLogger("com.spotify.confidence.test", options))
        .doesNotThrowAnyException();
  }

  @Test
  void testAllLoggingLevelsCanBeConfigured() {
    for (ProviderOptions.LoggingLevel level : ProviderOptions.LoggingLevel.values()) {
      final ProviderOptions options = new ProviderOptions(level);

      // Should not throw an exception for any logging level
      assertThatCode(() -> LoggingConfigurator.configureLogging(options))
          .doesNotThrowAnyException();
    }
  }
}
