package io.github.absketches.runway;

import java.time.Instant;

public record AppliedMigration(
    int installedRank,
    MigrationType type,
    MigrationVersion version,
    String description,
    String script,
    String checksum,
    Instant installedOn,
    long executionTimeMs,
    boolean success,
    String engineVersion
) {
}
