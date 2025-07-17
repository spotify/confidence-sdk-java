package com.spotify.confidence;

class InternalServerException extends RuntimeException {
  InternalServerException(String message) {
    super(message);
  }
}
