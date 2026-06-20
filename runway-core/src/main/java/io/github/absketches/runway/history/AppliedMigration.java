package io.github.absketches.runway.history;

import io.github.absketches.runway.MigrationVersion;

import java.time.Instant;

public record AppliedMigration(
    int installedRank,
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
