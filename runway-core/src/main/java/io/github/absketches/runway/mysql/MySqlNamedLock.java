package io.github.absketches.runway.mysql;

import io.github.absketches.runway.MigrationException;
import io.github.absketches.runway.MigrationLock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

final class MySqlNamedLock implements MigrationLock {
    private static final String LOCK_NAME = "io.github.absketches.runway.schema_history";

    @Override
    public void acquire(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select get_lock(?, ?)")) {
            statement.setString(1, LOCK_NAME);
            statement.setLong(2, ACQUIRE_TIMEOUT.toSeconds());
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                int acquired = resultSet.getInt(1);
                if (acquired != 1) {
                    throw new MigrationException("Could not acquire MySQL migration lock within "
                        + ACQUIRE_TIMEOUT.toSeconds() + " seconds: " + LOCK_NAME);
                }
            }
        }
    }

    @Override
    public void release(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select release_lock(?)")) {
            statement.setString(1, LOCK_NAME);
            statement.execute();
        }
    }
}
