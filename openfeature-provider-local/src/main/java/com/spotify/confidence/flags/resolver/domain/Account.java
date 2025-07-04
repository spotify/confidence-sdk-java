package com.spotify.confidence.flags.resolver.domain;

import java.util.Optional;

public record Account(String name, Optional<Region> region) {

  public Account(String name, Region region) {
    this(name, Optional.of(region));
  }

  public Account(String name) {
    this(name, Optional.empty());
  }

  public Region regionOrThrow() {
    return region.orElseThrow();
  }
}
