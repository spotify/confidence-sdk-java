package com.spotify.confidence;

import java.util.Optional;

record Account(String name, Optional<Region> region) {

  Account(String name, Region region) {
    this(name, Optional.of(region));
  }

  Account(String name) {
    this(name, Optional.empty());
  }
}
