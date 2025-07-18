package com.spotify.confidence;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class SemanticVersion implements Comparable<SemanticVersion> {

  private static final Pattern VERSION_PATTERN =
      Pattern.compile("^(\\d{1,3})(\\.\\d{1,3})(\\.\\d{1,3})?(\\.\\d{1,10})?$");

  private final int major;
  private final int minor;
  private final int patch;
  private final int tag;

  private SemanticVersion(int major, int minor, int patch, int tag) {
    this.major = major;
    this.minor = minor;
    this.patch = patch;
    this.tag = tag;
  }

  /**
   * Builds a Semantic version from String
   *
   * @param version String representing semantic version
   * @return instance of Semantic version
   * @throws IllegalArgumentException in case an invalid Semver is provided
   */
  static SemanticVersion fromVersionString(final String version) {
    if (version == null || version.isEmpty()) {
      throw new IllegalArgumentException("Invalid version, version must be non-empty and not null");
    }

    final String[] split = version.split("-");

    if (split.length == 0) {
      throw new IllegalArgumentException("Invalid semantic version string: " + version);
    }

    final String tokenBeforeHyphens = split[0];

    final Matcher matcher = VERSION_PATTERN.matcher(tokenBeforeHyphens);
    if (!matcher.find()) {
      throw new IllegalArgumentException("Invalid semantic version string: " + version);
    }

    final int major = parseVersionSegment(matcher.group(1), -1);
    final int minor = parseVersionSegment(matcher.group(2), 0);
    final int patch = parseVersionSegment(matcher.group(3), 0);
    final int tag = parseVersionSegment(matcher.group(4), 0);

    if (major < 0 || minor < 0 || patch < 0 || tag < 0) {
      throw new IllegalArgumentException("Invalid semantic version string: " + version);
    }

    return new SemanticVersion(major, minor, patch, tag);
  }

  static boolean isValid(final String version) {
    try {
      fromVersionString(version);
    } catch (IllegalArgumentException ex) {
      return false;
    }

    return true;
  }

  static int parseVersionSegment(final String segment, int defaultValue) {
    return Optional.ofNullable(emptyToNull(segment))
        .map(s -> s.replace(".", ""))
        .map(Integer::parseInt)
        .orElse(defaultValue);
  }

  @Override
  public int compareTo(final SemanticVersion other) {
    int result = major - other.major;
    if (result == 0) {
      result = minor - other.minor;
      if (result == 0) {
        result = patch - other.patch;
        if (result == 0) {
          result = tag - other.tag;
        }
      }
    }
    return result;
  }

  @Override
  public String toString() {
    return String.format("%d.%d.%d.%d", major, minor, patch, tag);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SemanticVersion)) {
      return false;
    }
    final SemanticVersion that = (SemanticVersion) o;
    return major == that.major && minor == that.minor && patch == that.patch && tag == that.tag;
  }

  @Override
  public int hashCode() {
    return Objects.hash(major, minor, patch, tag);
  }

  private static String emptyToNull(final String string) {
    return string == null || string.isEmpty() ? null : string;
  }
}
