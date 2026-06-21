package io.github.absketches.runway.integration;

import io.github.absketches.runway.DatabaseDialect;
import io.github.absketches.runway.MigrationRegistry;
import io.github.absketches.runway.MigrationResult;
import io.github.absketches.runway.Runway;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConsumerIntegrationSupport {
    private ConsumerIntegrationSupport() {
    }

    static DataSource dataSource(String jdbcUrl) {
        return dataSource(jdbcUrl, null, null);
    }

    static DataSource dataSource(String jdbcUrl, String username, String password) {
        return new DriverManagerDataSource(jdbcUrl, username, password);
    }

    static void assertGeneratedMigrationsApply(
        DataSource dataSource,
        DatabaseDialect dialect,
        MigrationRegistry registry,
        String indexExistsSql
    ) throws Exception {
        MigrationResult first = Runway.migrate(dataSource, dialect, registry);

        assertTrue(first.success(), () -> first.validationErrors().toString());
        assertEquals(17, first.executed().size());

        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement()
                .executeUpdate("""
                    insert into users (id, name, email, time_zone, nickname, preferred_language)
                    values (1, 'Ada', 'ada@example.test', 'UTC', 'Ada', 'en')
                    """);
            connection.createStatement()
                .executeUpdate("insert into audit_log (id, event_name, severity) values (1, 'created', 'WARN')");

            assertEquals(1, singleInt(connection, indexExistsSql));
            assertEquals(
                "runway",
                singleString(connection, "select metadata_value from audit_metadata where metadata_key = 'created_by'")
            );
            assertEquals(1, singleInt(connection, "select count(*) from user_names where name = 'Ada'"));
            assertEquals(1, singleInt(connection, "select count(*) from user_ids where name = 'Ada'"));
            assertEquals(1, singleInt(connection, "select count(*) from user_name_filter where id = 1"));
            assertEquals(1, singleInt(connection, "select count(*) from audit_event_names where event_name = 'created'"));
            assertEquals(1, singleInt(connection, "select count(*) from audit_events where event_name = 'created'"));
            assertEquals(1, singleInt(connection, "select severity_count from audit_severities where severity = 'WARN'"));
            assertEquals(1, singleInt(connection, "select event_count from audit_summary where severity = 'WARN'"));
            assertEquals(17, singleInt(connection, "select count(*) from runway_schema_history"));
            assertEquals(0, singleInt(connection, "select count(*) from runway_schema_history where not success"));
            assertEquals(
                expectedVersions(),
                strings(connection, "select version from runway_schema_history order by installed_rank")
            );
        }

        MigrationResult second = Runway.migrate(dataSource, dialect, registry);

        assertTrue(second.success(), () -> second.validationErrors().toString());
        assertEquals(0, second.executed().size());
    }

    static int singleInt(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static String singleString(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
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
        return List.of(
            "1", "2", "3", "4", "26", "79", "90", "91", "92", "101", "102", "103", "104", "105", "106", "107",
            "108"
        );
    }

    private record DriverManagerDataSource(String jdbcUrl, String username, String password) implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            if (username == null) {
                return DriverManager.getConnection(jdbcUrl);
            }
            return DriverManager.getConnection(jdbcUrl, username, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(jdbcUrl, username, password);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return DriverManager.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            DriverManager.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            DriverManager.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return DriverManager.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Not a wrapper for " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
