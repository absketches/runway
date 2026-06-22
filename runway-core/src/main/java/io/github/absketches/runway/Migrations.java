package io.github.absketches.runway;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class Migrations {
    private Migrations() {
    }

    public static Builder builder() {
        return new Builder("", "unknown");
    }

    public static Builder builder(String dialectName) {
        return new Builder(dialectName, "unknown");
    }

    public static Builder builder(String dialectName, String codegenVersion) {
        return new Builder(dialectName, codegenVersion);
    }

    public static final class Builder {
        private final List<MigrationDefinition> migrations = new ArrayList<>();
        private final String dialectName;
        private final String codegenVersion;

        private Builder(String dialectName, String codegenVersion) {
            this.dialectName = dialectName == null ? "" : dialectName;
            this.codegenVersion = codegenVersion == null || codegenVersion.isBlank() ? "unknown" : codegenVersion;
        }

        public Builder versioned(
            String version,
            String description,
            String script,
            String checksum,
            Function<String, InputStream> resourceLoader,
            List<String> statementResources
        ) {
            migrations.add(new MigrationDefinition(
                MigrationVersion.of(version),
                description,
                script,
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
            return new MigrationRegistry(sorted, dialectName, codegenVersion);
        }
    }
}
