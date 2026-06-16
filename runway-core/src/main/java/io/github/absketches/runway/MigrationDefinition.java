package io.github.absketches.runway;

import java.util.Objects;

public record MigrationDefinition(
    MigrationType type,
    MigrationVersion version,
    String description,
    String checksum,
    String sql
) {
    public MigrationDefinition {
        Objects.requireNonNull(type, "type");
        if (type != MigrationType.REPEATABLE) {
            Objects.requireNonNull(version, "version");
        }
        description = Objects.requireNonNull(description, "description");
        checksum = Objects.requireNonNull(checksum, "checksum");
        sql = Objects.requireNonNull(sql, "sql");
    }

    public String script() {
        if (type == MigrationType.REPEATABLE) {
            return "R__" + description.replace(' ', '_') + ".sql";
        }
        return "V" + version.value() + "__" + description.replace(' ', '_') + ".sql";
    }
}
