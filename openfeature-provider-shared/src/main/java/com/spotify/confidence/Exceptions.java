package com.spotify.confidence;

class Exceptions {

  static class ValueNotFound extends Exception {
    public ValueNotFound(String message) {
      super(message);
    }
  }

  static class IllegalValuePath extends Exception {
    public IllegalValuePath(String message) {
      super(message);
    }
  }

  static class ParseError extends RuntimeException {
    public ParseError(String message) {
      super(message);
    }
  }

  static class InvalidContextInMessaageError extends RuntimeException {
    public InvalidContextInMessaageError(String message) {
      super(message);
    }
  }

  static class IllegalValueType extends Exception {
    public IllegalValueType(String message) {
      super(message);
    }
  }

  static class IncompatibleValueType extends Exception {
    public IncompatibleValueType(String message) {
      super(message);
    }
  }
}
