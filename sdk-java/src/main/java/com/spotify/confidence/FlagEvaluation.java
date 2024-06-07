package com.spotify.confidence;

import java.util.Optional;
import javax.annotation.Nonnull;

public class FlagEvaluation<T> {

  private T value;
  private String variant;
  private String reason;
  private Optional<ErrorType> errorType;
  private Optional<String> errorMessage;

  public FlagEvaluation(
      @Nonnull T value,
      @Nonnull String variant,
      @Nonnull String reason,
      @Nonnull ErrorType errorType,
      @Nonnull String errorMessage) {
    this.value = value;
    this.variant = variant;
    this.reason = reason;
    this.errorType = Optional.of(errorType);
    this.errorMessage = Optional.of(errorMessage);
  }

  public FlagEvaluation(@Nonnull T value, @Nonnull String variant, @Nonnull String reason) {
    this.value = value;
    this.variant = variant;
    this.reason = reason;
    this.errorType = Optional.empty();
    this.errorMessage = Optional.empty();
  }

  public T getValue() {
    return value;
  }

  public void setValue(T value) {
    this.value = value;
  }

  public String getVariant() {
    return variant;
  }

  public void setVariant(String variant) {
    this.variant = variant;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public Optional<ErrorType> getErrorType() {
    return errorType;
  }

  public void setErrorType(ErrorType errorType) {
    this.errorType = Optional.of(errorType);
  }

  public Optional<String> getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = Optional.of(errorMessage);
  }

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
}
