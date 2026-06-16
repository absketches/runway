package io.github.absketches.runway;

public record ValidationError(
    ValidationErrorCode code,
    String message
) {
}
