package com.spotify.confidence;

import java.lang.reflect.Method;
import org.slf4j.LoggerFactory;

/**
 * Utility class for configuring logging levels based on ProviderOptions.
 *
 * <p>This class provides functionality to configure SLF4J loggers based on the logging level
 * specified in {@link ProviderOptions}. It attempts to configure Logback loggers when available,
 * but gracefully falls back if Logback is not present in the classpath.
 *
 * <p>Note: This configurator currently supports Logback. If you're using a different SLF4J
 * implementation, the logging level configuration may not take effect, but the provider will still
 * function normally.
 *
 * @since 0.2.4
 */
class LoggingConfigurator {

  // Package names for Confidence SDK components that should have their logging configured
  private static final String[] CONFIDENCE_LOGGER_PACKAGES = {"com.spotify.confidence"};

  // Cached reflection objects for performance
  private static Class<?> logbackLevelClass = null;
  private static Class<?> logbackLoggerClass = null;
  private static Method setLevelMethod = null;
  private static boolean logbackAvailable = false;

  static {
    initializeLogbackSupport();
  }

  /**
   * Configures logging for all Confidence SDK components based on the provided options.
   *
   * @param options the provider options containing logging configuration
   */
  static void configureLogging(ProviderOptions options) {
    if (options == null || !logbackAvailable) {
      return;
    }

    for (String packageName : CONFIDENCE_LOGGER_PACKAGES) {
      configureLoggerForPackage(packageName, options.getLoggingLevel());
    }
  }

  /**
   * Configures logging for a specific logger based on the provided options.
   *
   * @param loggerName the name of the logger to configure
   * @param options the provider options containing logging configuration
   */
  static void configureLogger(String loggerName, ProviderOptions options) {
    if (options == null || loggerName == null || !logbackAvailable) {
      return;
    }

    configureSpecificLogger(loggerName, options.getLoggingLevel());
  }

  private static void initializeLogbackSupport() {
    try {
      logbackLevelClass = Class.forName("ch.qos.logback.classic.Level");
      logbackLoggerClass = Class.forName("ch.qos.logback.classic.Logger");
      setLevelMethod = logbackLoggerClass.getMethod("setLevel", logbackLevelClass);
      logbackAvailable = true;
    } catch (ClassNotFoundException | NoSuchMethodException e) {
      // Logback is not available, logging configuration will be skipped
      logbackAvailable = false;
    }
  }

  private static void configureLoggerForPackage(
      String packageName, ProviderOptions.LoggingLevel loggingLevel) {
    try {
      final org.slf4j.Logger slf4jLogger = LoggerFactory.getLogger(packageName);
      if (logbackLoggerClass.isInstance(slf4jLogger)) {
        final Object logbackLevel = mapToLogbackLevel(loggingLevel);
        if (logbackLevel != null) {
          setLevelMethod.invoke(slf4jLogger, logbackLevel);
        }
      }
    } catch (Exception e) {
      // Silently ignore if we can't configure logging
    }
  }

  private static void configureSpecificLogger(
      String loggerName, ProviderOptions.LoggingLevel loggingLevel) {
    try {
      final org.slf4j.Logger slf4jLogger = LoggerFactory.getLogger(loggerName);
      if (logbackLoggerClass.isInstance(slf4jLogger)) {
        final Object logbackLevel = mapToLogbackLevel(loggingLevel);
        if (logbackLevel != null) {
          setLevelMethod.invoke(slf4jLogger, logbackLevel);
        }
      }
    } catch (Exception e) {
      // Silently ignore if we can't configure logging
    }
  }

  private static Object mapToLogbackLevel(ProviderOptions.LoggingLevel loggingLevel) {
    if (loggingLevel == null || !logbackAvailable) {
      return null;
    }

    try {
      switch (loggingLevel) {
        case ALL:
          return logbackLevelClass.getField("ALL").get(null);
        case TRACE:
          return logbackLevelClass.getField("TRACE").get(null);
        case DEBUG:
          return logbackLevelClass.getField("DEBUG").get(null);
        case INFO:
          return logbackLevelClass.getField("INFO").get(null);
        case WARN:
          return logbackLevelClass.getField("WARN").get(null);
        case ERROR:
          return logbackLevelClass.getField("ERROR").get(null);
        case OFF:
          return logbackLevelClass.getField("OFF").get(null);
        default:
          return logbackLevelClass.getField("INFO").get(null);
      }
    } catch (Exception e) {
      return null;
    }
  }
}
