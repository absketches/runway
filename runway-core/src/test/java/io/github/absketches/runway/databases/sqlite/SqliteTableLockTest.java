package io.github.absketches.runway.databases.sqlite;

import io.github.absketches.runway.MigrationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqliteTableLockTest {
    @TempDir
    Path tempDir;

    @Test
    void activeLockFailsWithClearException() throws Exception {
        SqliteTableLock lock = new SqliteTableLock(Duration.ofMillis(25), Duration.ofMillis(5));
        try (Connection first = connection();
             Connection second = connection()) {
            lock.acquire(first);

            assertThrows(MigrationException.class, () -> lock.acquire(second));

            lock.release(first);
            lock.acquire(second);
            lock.release(second);
        }
    }

    @Test
    void staleLockIsReplacedAndReleased() throws Exception {
        try (Connection connection = connection()) {
            createStaleLock(connection);

            SqliteTableLock lock = new SqliteTableLock(Duration.ofMillis(25), Duration.ofMillis(5));
            lock.acquire(connection);

            assertEquals(1, countLocks(connection));

            lock.release(connection);
            assertEquals(0, countLocks(connection));
        }
    }

    @Test
    void schemaErrorsAreNotReportedAsLockContention() throws Exception {
        try (Connection connection = connection()) {
            try (var statement = connection.createStatement()) {
                statement.execute("""
                    create table runway_schema_lock (
                        id integer primary key check (id = 1),
                        locked_at text not null
                    )
                    """);
            }

            SqliteTableLock lock = new SqliteTableLock(Duration.ofMillis(25), Duration.ofMillis(5));

            assertThrows(SQLException.class, () -> lock.acquire(connection));
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("runway.sqlite"));
    }

    private static void createStaleLock(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                create table runway_schema_lock (
                    id integer primary key check (id = 1),
                    locked_at text not null,
                    owner text not null
                )
                """);
        }
        try (var statement = connection.prepareStatement("""
            insert into runway_schema_lock (id, locked_at, owner)
            values (1, ?, 'stale-owner')
            """)) {
            statement.setString(1, Instant.now().minus(2, ChronoUnit.HOURS).toString());
            statement.executeUpdate();
        }
    }

    private static int countLocks(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("select count(*) from runway_schema_lock")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
