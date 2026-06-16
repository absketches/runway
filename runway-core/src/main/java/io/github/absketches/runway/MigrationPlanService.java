package io.github.absketches.runway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MigrationPlanService {

    public MigrationPlan plan(
        List<MigrationDefinition> available,
        List<AppliedMigration> applied
    ) {
        List<ValidationError> errors = new ArrayList<>();
        List<MigrationDefinition> sorted = available.stream().sorted(Migrations::compare).toList();

        validateDuplicateAvailable(sorted, errors);

        Map<MigrationKey, MigrationDefinition> availableByIdentity = new HashMap<>();
        for (MigrationDefinition migration : sorted) {
            availableByIdentity.put(MigrationKey.from(migration), migration);
        }

        Set<MigrationKey> appliedSuccessful = new HashSet<>();
        MigrationVersion latestAppliedVersion = null;
        for (AppliedMigration appliedMigration : applied) {
            if (!appliedMigration.success()) {
                errors.add(new ValidationError(
                    ValidationErrorCode.FAILED_MIGRATION,
                    "Migration previously failed: " + appliedMigration.script()
                ));
                continue;
            }

            MigrationKey key = MigrationKey.from(appliedMigration);
            appliedSuccessful.add(key);
            MigrationDefinition availableMigration = availableByIdentity.get(key);
            if (availableMigration == null && appliedMigration.type() != MigrationType.REPEATABLE) {
                errors.add(new ValidationError(
                    ValidationErrorCode.MISSING_MIGRATION,
                    "Applied migration is missing from the registry: " + appliedMigration.script()
                ));
            } else if (availableMigration != null
                && appliedMigration.type() != MigrationType.REPEATABLE
                && !availableMigration.checksum().equals(appliedMigration.checksum())) {
                errors.add(new ValidationError(
                    ValidationErrorCode.CHECKSUM_MISMATCH,
                    "Checksum mismatch for " + appliedMigration.script()
                ));
            }

            if (appliedMigration.version() != null
                && appliedMigration.type() != MigrationType.REPEATABLE
                && (latestAppliedVersion == null || appliedMigration.version().compareTo(latestAppliedVersion) > 0)) {
                latestAppliedVersion = appliedMigration.version();
            }
        }

        List<MigrationDefinition> pending = new ArrayList<>();
        for (MigrationDefinition migration : sorted) {
            MigrationKey key = MigrationKey.from(migration);
            if (migration.type() == MigrationType.REPEATABLE) {
                AppliedMigration previous = findApplied(applied, migration);
                if (previous == null || !previous.checksum().equals(migration.checksum())) {
                    pending.add(migration);
                }
            } else if (!appliedSuccessful.contains(key)) {
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
        Set<MigrationKey> seen = new HashSet<>();
        for (MigrationDefinition migration : available) {
            MigrationKey key = MigrationKey.from(migration);
            if (!seen.add(key)) {
                errors.add(new ValidationError(
                    ValidationErrorCode.DUPLICATE_MIGRATION,
                    "Duplicate migration in registry: " + migration.script()
                ));
            }
        }
    }

    private static AppliedMigration findApplied(List<AppliedMigration> applied, MigrationDefinition migration) {
        MigrationKey key = MigrationKey.from(migration);
        return applied.stream()
            .filter(candidate -> candidate.success()
                && MigrationKey.from(candidate).equals(key))
            .findFirst()
            .orElse(null);
    }

}
