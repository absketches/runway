package io.github.absketches.runway;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class Migrations {
    private Migrations() {
    }

    public static Builder builder() {
        return new Builder("");
    }

    public static Builder builder(String dialectName) {
        return new Builder(dialectName);
    }

    public static final class Builder {
        private final List<MigrationDefinition> migrations = new ArrayList<>();
        private final String dialectName;

        private Builder(String dialectName) {
            this.dialectName = dialectName == null ? "" : dialectName;
        }

        public Builder versioned(
            String version,
            String description,
            String checksum,
            Function<String, InputStream> resourceLoader,
            List<String> statementResources
        ) {
            migrations.add(new MigrationDefinition(
                MigrationVersion.of(version),
                description,
                checksum,
                resourceLoader,
                statementResources
            ));
            return this;
        }

        public MigrationRegistry build() {
            List<MigrationDefinition> sorted = migrations.stream()
                .sorted(MigrationDefinition::compare)
                .toList();
            return new MigrationRegistry(sorted, dialectName);
        }
    }
}
