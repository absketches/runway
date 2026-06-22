package io.github.absketches.runway;

import java.util.List;

public record MigrationRegistry(
    List<MigrationDefinition> migrations,
    String dialectName,
    String codegenVersion
) {
    public MigrationRegistry {
        migrations = List.copyOf(migrations);
        dialectName = dialectName == null ? "" : dialectName;
        codegenVersion = codegenVersion == null || codegenVersion.isBlank() ? "unknown" : codegenVersion;
    }

    public MigrationRegistry(List<MigrationDefinition> migrations) {
        this(migrations, "", "unknown");
    }

    public MigrationRegistry(List<MigrationDefinition> migrations, String dialectName) {
        this(migrations, dialectName, "unknown");
    }
}
