package io.github.absketches.runway;

import java.util.List;

public record MigrationRegistry(
    List<MigrationDefinition> migrations,
    String dialectName
) {
    public MigrationRegistry {
        migrations = List.copyOf(migrations);
        dialectName = dialectName == null ? "" : dialectName;
    }

    public MigrationRegistry(List<MigrationDefinition> migrations) {
        this(migrations, "");
    }
}
