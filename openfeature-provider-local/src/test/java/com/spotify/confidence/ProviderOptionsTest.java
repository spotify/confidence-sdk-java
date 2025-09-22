package com.spotify.confidence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProviderOptionsTest {

  @Test
  void testDefaultOptions() {
    final ProviderOptions options = new ProviderOptions();
    assertThat(options.getLoggingLevel()).isEqualTo(ProviderOptions.LoggingLevel.INFO);
  }

  @Test
  void testWithLoggingLevel() {
    final ProviderOptions options =
        ProviderOptions.withLoggingLevel(ProviderOptions.LoggingLevel.DEBUG);
    assertThat(options.getLoggingLevel()).isEqualTo(ProviderOptions.LoggingLevel.DEBUG);
  }

  @Test
  void testDefaults() {
    final ProviderOptions options = ProviderOptions.defaults();
    assertThat(options.getLoggingLevel()).isEqualTo(ProviderOptions.LoggingLevel.INFO);
  }

  @Test
  void testConstructorWithLoggingLevel() {
    final ProviderOptions options = new ProviderOptions(ProviderOptions.LoggingLevel.ERROR);
    assertThat(options.getLoggingLevel()).isEqualTo(ProviderOptions.LoggingLevel.ERROR);
  }

  @Test
  void testConstructorWithNullLoggingLevel() {
    final ProviderOptions options = new ProviderOptions(null);
    assertThat(options.getLoggingLevel()).isEqualTo(ProviderOptions.LoggingLevel.INFO);
  }

  @Test
  void testEqualsAndHashCode() {
    final ProviderOptions options1 = new ProviderOptions(ProviderOptions.LoggingLevel.DEBUG);
    final ProviderOptions options2 = new ProviderOptions(ProviderOptions.LoggingLevel.DEBUG);
    final ProviderOptions options3 = new ProviderOptions(ProviderOptions.LoggingLevel.INFO);

    assertThat(options1).isEqualTo(options2);
    assertThat(options1).isNotEqualTo(options3);
    assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
    assertThat(options1.hashCode()).isNotEqualTo(options3.hashCode());
  }

  @Test
  void testToString() {
    final ProviderOptions options = new ProviderOptions(ProviderOptions.LoggingLevel.WARN);
    assertThat(options.toString()).contains("WARN");
  }

  @Test
  void testAllLoggingLevels() {
    for (ProviderOptions.LoggingLevel level : ProviderOptions.LoggingLevel.values()) {
      final ProviderOptions options = new ProviderOptions(level);
      assertThat(options.getLoggingLevel()).isEqualTo(level);
    }
  }
}
