package io.github.absketches.runway.databases.sqlite;

import io.github.absketches.runway.MigrationLock;
import io.github.absketches.runway.MigrationException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

final class SqliteTableLock implements MigrationLock {
    private static final Duration STALE_LOCK_AFTER = Duration.ofHours(1);
    private static final int SQLITE_CONSTRAINT_PRIMARY_CODE = 19;
    private final Map<Connection, String> owners = new WeakHashMap<>();
    private final Duration acquireTimeout;
    private final Duration retryDelay;

    SqliteTableLock() {
        this(ACQUIRE_TIMEOUT, RETRY_DELAY);
    }

    SqliteTableLock(Duration acquireTimeout, Duration retryDelay) {
        this.acquireTimeout = acquireTimeout;
        this.retryDelay = retryDelay;
    }

    @Override
    public void acquire(Connection connection) throws SQLException {
        String owner = UUID.randomUUID().toString();
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                create table if not exists runway_schema_lock (
                    id integer primary key check (id = 1),
                    locked_at text not null,
                    owner text not null
                )
                """);
        }

        Instant deadline = Instant.now().plus(acquireTimeout);
        while (true) {
            deleteStaleLock(connection);
            if (!tryInsert(connection, owner)) {
                if (!Instant.now().isBefore(deadline)) {
                    break;
                }
                sleep();
                continue;
            }
            synchronized (owners) {
                owners.put(connection, owner);
            }
            return;
        }

        throw new MigrationException("Could not acquire SQLite migration lock within "
            + acquireTimeout.toSeconds() + " seconds. Another Runway migration is already running.");
    }

    @Override
    public void release(Connection connection) throws SQLException {
        String owner;
        synchronized (owners) {
            owner = owners.remove(connection);
        }
        if (owner == null) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement("delete from runway_schema_lock where id = 1 and owner = ?")) {
            statement.setString(1, owner);
            statement.executeUpdate();
        }
    }

    private boolean tryInsert(Connection connection, String owner) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            insert into runway_schema_lock (id, locked_at, owner)
            values (1, ?, ?)
            """)) {
            statement.setString(1, Instant.now().toString());
            statement.setString(2, owner);
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (isLockConflict(e)) {
                return false;
            }
            throw e;
        }
    }

    private static boolean isLockConflict(SQLException e) {
        String sqlState = e.getSQLState();
        return (sqlState != null && sqlState.startsWith("23"))
            || (e.getErrorCode() & 0xFF) == SQLITE_CONSTRAINT_PRIMARY_CODE;
    }

    private void deleteStaleLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            delete from runway_schema_lock
            where id = 1 and locked_at < ?
            """)) {
            statement.setString(1, Instant.now().minus(STALE_LOCK_AFTER).toString());
            statement.executeUpdate();
        }
    }

    private void sleep() {
        try {
            Thread.sleep(retryDelay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MigrationException("Interrupted while waiting for SQLite migration lock", e);
        }
    }
}
