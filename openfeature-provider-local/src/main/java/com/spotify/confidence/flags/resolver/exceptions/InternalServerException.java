package com.spotify.confidence.flags.resolver.exceptions;

public class InternalServerException extends RuntimeException {
  public InternalServerException(String message) {
    super(message);
  }
}
