package io.github.absketches.runway.planning;

import io.github.absketches.runway.MigrationDefinition;
import io.github.absketches.runway.ValidationError;

import java.util.List;

public record MigrationPlan(
    List<MigrationDefinition> pending,
    List<ValidationError> validationErrors
) {
    public boolean valid() {
        return validationErrors.isEmpty();
    }
}
