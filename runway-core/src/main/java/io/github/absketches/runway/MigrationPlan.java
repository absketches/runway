package io.github.absketches.runway;

import java.util.List;

public record MigrationPlan(
    List<MigrationDefinition> pending,
    List<ValidationError> validationErrors
) {
    public boolean valid() {
        return validationErrors.isEmpty();
    }
}
