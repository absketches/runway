package io.github.absketches.runway.integration;

import io.github.absketches.runway.MigrationException;
import io.github.absketches.runway.Migrations;
import io.github.absketches.runway.Runway;
import io.github.absketches.runway.databases.sqlite.SqliteDialect;
import io.github.absketches.runway.integration.generated.sqlite.GeneratedRunwayMigrations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import static io.github.absketches.runway.integration.ConsumerIntegrationSupport.singleInt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteConsumerIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void consumerProjectGeneratesRegistryAndMigratesSqliteDatabase() throws Exception {
        ConsumerIntegrationSupport.assertGeneratedMigrationsApply(
            ConsumerIntegrationSupport.dataSource("jdbc:sqlite:" + tempDir.resolve("runway.sqlite")),
            SqliteDialect.INSTANCE,
            GeneratedRunwayMigrations.registry(),
            "select count(*) from sqlite_master where type = 'index' and name = 'audit_log_event_name_idx'"
        );
    }

    @Test
    void failedMigrationRollsBackChangesAndRecordsFailure() throws Exception {
        var dataSource = ConsumerIntegrationSupport.dataSource("jdbc:sqlite:" + tempDir.resolve("failed.sqlite"));
        var registry = Migrations.builder()
            .versioned(
                "1",
                "failing migration",
                "V1__failing_migration.sql",
                "sha256:test",
                resourcePath -> new ByteArrayInputStream((switch (resourcePath) {
                    case "/create.sql" -> "create table should_rollback (id integer primary key);\n";
                    case "/fail.sql" -> "insert into missing_table (id) values (1);\n";
                    default -> throw new IllegalArgumentException("Unexpected resource: " + resourcePath);
                }).getBytes(StandardCharsets.UTF_8)),
                List.of(
                    "/create.sql",
                    "/fail.sql"
                )
            )
            .build();

        assertThrows(
            MigrationException.class,
            () -> Runway.migrate(dataSource, SqliteDialect.INSTANCE, registry)
        );

        try (Connection connection = dataSource.getConnection()) {
            assertEquals(0, singleInt(
                connection,
                "select count(*) from sqlite_master where type = 'table' and name = 'should_rollback'"
            ));
            assertEquals(1, singleInt(connection, "select count(*) from runway_schema_history where success = 0"));
        }

        MigrationException retry = assertThrows(
            MigrationException.class,
            () -> Runway.migrate(dataSource, SqliteDialect.INSTANCE, registry)
        );
        assertTrue(retry.getMessage().contains("Migration previously failed"));
    }
}
