package com.spotify.confidence;

public class Exceptions {

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

  public static class IllegalValueType extends Exception {
    public IllegalValueType(String message) {
      super(message);
    }
  }

  public static class IncompatibleValueType extends Exception {
    public IncompatibleValueType(String message) {
      super(message);
    }
  }
}
