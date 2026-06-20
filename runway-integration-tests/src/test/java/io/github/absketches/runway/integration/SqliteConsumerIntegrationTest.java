package io.github.absketches.runway.integration;

import io.github.absketches.runway.MigrationException;
import io.github.absketches.runway.MigrationResult;
import io.github.absketches.runway.Migrations;
import io.github.absketches.runway.Runway;
import io.github.absketches.runway.integration.generated.GeneratedRunwayMigrations;
import io.github.absketches.runway.databases.sqlite.SqliteDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteConsumerIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void consumerProjectGeneratesRegistryAndMigratesSqliteDatabase() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("runway.sqlite"));

        MigrationResult first = Runway.migrate(
            dataSource,
            SqliteDialect.INSTANCE,
            GeneratedRunwayMigrations.registry()
        );

        assertTrue(first.success(), () -> first.validationErrors().toString());
        assertEquals(6, first.executed().size());

        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().executeUpdate("insert into users (id, name, email) values (1, 'Ada', 'ada@example.test')");
            connection.createStatement().executeUpdate("insert into audit_log (id, event_name, severity) values (1, 'created', 'WARN')");
            assertEquals(1, singleInt(connection, "select count(*) from sqlite_master where type = 'table' and name = 'audit_log'"));
            assertEquals(1, singleInt(connection, "select count(*) from sqlite_master where type = 'index' and name = 'audit_log_event_name_idx'"));
            assertEquals(1, singleInt(connection, "select count(*) from sqlite_master where type = 'table' and name = 'audit_metadata'"));
            assertEquals("runway", singleString(connection, "select metadata_value from audit_metadata where metadata_key = 'created_by'"));
            assertEquals(1, singleInt(connection, "select count(*) from user_names where name = 'Ada'"));
            assertEquals(1, singleInt(connection, "select event_count from audit_summary where severity = 'WARN'"));
            assertEquals(6, singleInt(connection, "select count(*) from runway_schema_history where success = 1"));
            assertEquals(
                expectedVersions(),
                strings(connection, "select version from runway_schema_history order by installed_rank")
            );
        }

        MigrationResult second = Runway.migrate(
            dataSource,
            SqliteDialect.INSTANCE,
            GeneratedRunwayMigrations.registry()
        );

        assertTrue(second.success(), () -> second.validationErrors().toString());
        assertEquals(0, second.executed().size());
    }

    @Test
    void failedMigrationRollsBackChangesAndRecordsFailure() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("failed.sqlite"));
        var registry = Migrations.builder()
            .versioned(
                "1",
                "failing migration",
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

    private static String singleString(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private static int singleInt(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static List<String> strings(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery(sql)) {
            List<String> values = new ArrayList<>();
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
            return values;
        }
    }

    private static List<String> expectedVersions() {
        List<String> versions = new ArrayList<>();
        versions.add("1");
        versions.add("2");
        versions.add("26");
        versions.add("79");
        versions.add("101");
        versions.add("102");
        return versions;
    }
}
