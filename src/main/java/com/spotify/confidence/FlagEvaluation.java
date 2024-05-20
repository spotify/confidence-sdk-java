package com.spotify.confidence;

import java.util.Optional;
import lombok.Data;
import lombok.Getter;

@Data
@Getter
public class FlagEvaluation<T> {

  public T getValue() {
    return value;
  }

  private T value;

  @Override
  public String toString() {
    return "FlagEvaluation{"
        + "value="
        + value
        + ", variant='"
        + variant
        + '\''
        + ", reason='"
        + reason
        + '\''
        + ", errorType="
        + errorType
        + ", errorMessage='"
        + errorMessage.orElse("")
        + '\''
        + '}';
  }

  private String variant;
  private String reason;
  private Optional<ErrorType> errorType;
  private Optional<String> errorMessage;

  public FlagEvaluation(
      T value, String variant, String reason, ErrorType errorType, String errorMessage) {
    this.value = value;
    this.variant = variant;
    this.reason = reason;
    this.errorType = Optional.of(errorType);
    this.errorMessage = Optional.of(errorMessage);
  }

  public FlagEvaluation(T value, String variant, String reason) {
    this.value = value;
    this.variant = variant;
    this.reason = reason;
    this.errorType = Optional.empty();
    this.errorMessage = Optional.empty();
  }
}
