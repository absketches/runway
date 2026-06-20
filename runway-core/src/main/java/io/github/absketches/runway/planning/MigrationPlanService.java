package io.github.absketches.runway.planning;

import io.github.absketches.runway.MigrationDefinition;
import io.github.absketches.runway.MigrationVersion;
import io.github.absketches.runway.ValidationError;
import io.github.absketches.runway.ValidationErrorCode;
import io.github.absketches.runway.history.AppliedMigration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MigrationPlanService {
    private MigrationPlanService() {
    }

    public static MigrationPlan plan(
        List<MigrationDefinition> available,
        List<AppliedMigration> applied
    ) {
        List<ValidationError> errors = new ArrayList<>();
        List<MigrationDefinition> sorted = available.stream().sorted(MigrationDefinition::compare).toList();

        validateDuplicateAvailable(sorted, errors);

        Map<MigrationVersion, MigrationDefinition> availableByVersion = new HashMap<>();
        for (MigrationDefinition migration : sorted) {
            availableByVersion.put(migration.version(), migration);
        }

        Set<MigrationVersion> appliedSuccessful = new HashSet<>();
        MigrationVersion latestAppliedVersion = null;
        for (AppliedMigration appliedMigration : applied) {
            if (!appliedMigration.success()) {
                errors.add(new ValidationError(
                    ValidationErrorCode.FAILED_MIGRATION,
                    "Migration previously failed: " + appliedMigration.script()
                ));
                continue;
            }

            appliedSuccessful.add(appliedMigration.version());
            MigrationDefinition availableMigration = availableByVersion.get(appliedMigration.version());
            if (availableMigration == null) {
                errors.add(new ValidationError(
                    ValidationErrorCode.MISSING_MIGRATION,
                    "Applied migration is missing from the registry: " + appliedMigration.script()
                ));
            } else if (!availableMigration.checksum().equals(appliedMigration.checksum())) {
                errors.add(new ValidationError(
                    ValidationErrorCode.CHECKSUM_MISMATCH,
                    "Checksum mismatch for " + appliedMigration.script()
                ));
            }

            if (latestAppliedVersion == null || appliedMigration.version().compareTo(latestAppliedVersion) > 0) {
                latestAppliedVersion = appliedMigration.version();
            }
        }

        List<MigrationDefinition> pending = new ArrayList<>();
        for (MigrationDefinition migration : sorted) {
            if (!appliedSuccessful.contains(migration.version())) {
                if (latestAppliedVersion != null
                    && migration.version().compareTo(latestAppliedVersion) < 0) {
                    errors.add(new ValidationError(
                        ValidationErrorCode.OUT_OF_ORDER,
                        "Pending migration " + migration.script() + " is older than latest applied version "
                            + latestAppliedVersion
                    ));
                } else {
                    pending.add(migration);
                }
            }
        }

        if (!errors.isEmpty()) {
            pending = List.of();
        }
        return new MigrationPlan(List.copyOf(pending), List.copyOf(errors));
    }

    private static void validateDuplicateAvailable(List<MigrationDefinition> available, List<ValidationError> errors) {
        Set<MigrationVersion> seen = new HashSet<>();
        for (MigrationDefinition migration : available) {
            if (!seen.add(migration.version())) {
                errors.add(new ValidationError(
                    ValidationErrorCode.DUPLICATE_MIGRATION,
                    "Duplicate migration in registry: " + migration.script()
                ));
            }
        }
    }

}
