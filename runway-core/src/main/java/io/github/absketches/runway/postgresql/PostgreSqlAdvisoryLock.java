package io.github.absketches.runway.postgresql;

import io.github.absketches.runway.MigrationException;
import io.github.absketches.runway.MigrationLock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

final class PostgreSqlAdvisoryLock implements MigrationLock {
    private static final long LOCK_ID = 0x52756e776179L;
    private final Duration acquireTimeout;
    private final Duration retryDelay;

    PostgreSqlAdvisoryLock() {
        this(ACQUIRE_TIMEOUT, RETRY_DELAY);
    }

    PostgreSqlAdvisoryLock(Duration acquireTimeout, Duration retryDelay) {
        this.acquireTimeout = acquireTimeout;
        this.retryDelay = retryDelay;
    }

    @Override
    public void acquire(Connection connection) throws SQLException {
        Instant deadline = Instant.now().plus(acquireTimeout);
        while (!Instant.now().isAfter(deadline)) {
            if (tryAcquire(connection)) {
                return;
            }
            sleep();
        }
        throw new MigrationException("Could not acquire PostgreSQL migration lock within " + acquireTimeout.toSeconds() + " seconds.");
    }

    private boolean tryAcquire(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select pg_try_advisory_lock(?)")) {
            statement.setLong(1, LOCK_ID);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    @Override
    public void release(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select pg_advisory_unlock(?)")) {
            statement.setLong(1, LOCK_ID);
            statement.execute();
        }
    }

    private void sleep() {
        try {
            Thread.sleep(retryDelay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MigrationException("Interrupted while waiting for PostgreSQL migration lock", e);
        }
    }
}
