package com.spotify.confidence;

import java.io.IOException;
import java.util.Properties;

final class SdkUtils {

  private SdkUtils() {}

  static String getSdkVersion() {
    try {
      final Properties prop = new Properties();
      prop.load(SdkUtils.class.getResourceAsStream("/version.properties"));
      return prop.getProperty("version");
    } catch (IOException e) {
      throw new RuntimeException("Can't determine version of the SDK", e);
    }
  }
}
