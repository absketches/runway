package io.github.absketches.runway;

import java.util.List;

public record MigrationResult(
    boolean success,
    List<MigrationDefinition> executed,
    List<ValidationError> validationErrors
) {
}
