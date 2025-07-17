package com.spotify.confidence;

class UnauthenticatedException extends RuntimeException {
  UnauthenticatedException(String message) {
    super(message);
  }
}
