package io.github.absketches.runway;

import java.util.Objects;
import java.util.List;
import java.io.InputStream;
import java.util.function.Function;

public record MigrationDefinition(
    MigrationVersion version,
    String description,
    String checksum,
    Function<String, InputStream> resourceLoader,
    List<String> statementResources
) {
    public MigrationDefinition {
        Objects.requireNonNull(version, "version");
        description = Objects.requireNonNull(description, "description");
        checksum = Objects.requireNonNull(checksum, "checksum");
        resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
        statementResources = List.copyOf(statementResources);
        if (statementResources.isEmpty()) {
            throw new IllegalArgumentException("Migration must contain at least one statement");
        }
    }

    public String script() {
        return "V" + version.value() + "__" + description.replace(' ', '_') + ".sql";
    }

    public static int compare(MigrationDefinition left, MigrationDefinition right) {
        return left.version().compareTo(right.version());
    }
}
