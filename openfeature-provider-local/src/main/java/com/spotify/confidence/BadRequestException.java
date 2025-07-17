package com.spotify.confidence;

class BadRequestException extends RuntimeException {
  BadRequestException(String message) {
    super(message);
  }
}
