package com.spotify.confidence;

public class ConfidenceExceptions {

  public static class ValueNotFound extends Exception {
    public ValueNotFound(String message) {
      super(message);
    }
  }

  public static class IllegalValuePath extends Exception {
    public IllegalValuePath(String message) {
      super(message);
    }
  }

  public static class ParseError extends RuntimeException {
    public ParseError(String message) {
      super(message);
    }
  }
}
