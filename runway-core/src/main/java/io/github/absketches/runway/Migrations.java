package io.github.absketches.runway;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Migrations {
    private Migrations() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<MigrationDefinition> migrations = new ArrayList<>();

        public Builder versioned(String version, String description, String checksum, String sql) {
            migrations.add(new MigrationDefinition(
                MigrationType.VERSIONED,
                MigrationVersion.of(version),
                description,
                checksum,
                sql
            ));
            return this;
        }

        public Builder repeatable(String description, String checksum, String sql) {
            migrations.add(new MigrationDefinition(
                MigrationType.REPEATABLE,
                null,
                description,
                checksum,
                sql
            ));
            return this;
        }

        public MigrationRegistry build() {
            List<MigrationDefinition> sorted = migrations.stream()
                .sorted(Migrations::compare)
                .toList();
            return () -> sorted;
        }
    }

    public static int compare(MigrationDefinition left, MigrationDefinition right) {
        Comparator<MigrationDefinition> comparator = Comparator
            .comparing((MigrationDefinition migration) -> migration.type() == MigrationType.REPEATABLE ? 1 : 0)
            .thenComparing(migration -> migration.version() == null ? MigrationVersion.of("0") : migration.version())
            .thenComparing(MigrationDefinition::description);
        return comparator.compare(left, right);
    }
}
