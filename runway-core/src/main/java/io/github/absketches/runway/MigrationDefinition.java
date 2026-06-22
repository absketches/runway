package io.github.absketches.runway;

import java.util.Objects;
import java.util.List;
import java.io.InputStream;
import java.util.function.Function;

public record MigrationDefinition(
    MigrationVersion version,
    String description,
    String script,
    String checksum,
    Function<String, InputStream> resourceLoader,
    List<String> statementResources
) {
    public MigrationDefinition {
        Objects.requireNonNull(version, "version");
        description = Objects.requireNonNull(description, "description");
        script = Objects.requireNonNull(script, "script");
        checksum = Objects.requireNonNull(checksum, "checksum");
        resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
        statementResources = List.copyOf(statementResources);
        if (statementResources.isEmpty()) {
            throw new IllegalArgumentException("Migration must contain at least one statement");
        }
    }

    public static int compare(MigrationDefinition left, MigrationDefinition right) {
        return left.version().compareTo(right.version());
    }
}
