package io.github.absketches.runway;

record MigrationKey(
    MigrationType type,
    MigrationVersion version,
    String description
) {
    static MigrationKey from(MigrationDefinition migration) {
        return new MigrationKey(migration.type(), migration.version(), migration.description());
    }

    static MigrationKey from(AppliedMigration migration) {
        return new MigrationKey(migration.type(), migration.version(), migration.description());
    }
}
