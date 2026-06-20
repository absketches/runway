package io.github.absketches.runway;

import io.github.absketches.runway.databases.sqlite.SqliteDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunwayTest {
    @TempDir
    Path tempDir;

    @Test
    void throwsWhenGeneratedSqlResourceIsMissing() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("runway.sqlite"));
        MigrationRegistry registry = Migrations.builder()
            .versioned(
                "1",
                "missing resource",
                "sha256:test",
                ignored -> null,
                List.of("/generated/runway/V1__missing_resource/statement-000.sql")
            )
            .build();

        MigrationException exception = assertThrows(
            MigrationException.class,
            () -> Runway.migrate(dataSource, SqliteDialect.INSTANCE, registry)
        );

        assertTrue(exception.getMessage().contains("SQL resource not found"));
    }

    @Test
    void throwsWhenGeneratedDialectDoesNotMatchRuntimeDialect() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("runway.sqlite"));
        MigrationRegistry registry = Migrations.builder("PostgreSQL").build();

        MigrationException exception = assertThrows(
            MigrationException.class,
            () -> Runway.migrate(dataSource, SqliteDialect.INSTANCE, registry)
        );

        assertTrue(exception.getMessage().contains("generated for PostgreSQL"));
        assertTrue(exception.getMessage().contains("runtime dialect is SQLite"));
    }
}
