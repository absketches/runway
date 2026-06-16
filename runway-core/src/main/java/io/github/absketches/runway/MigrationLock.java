package io.github.absketches.runway;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;

public interface MigrationLock {
    Duration ACQUIRE_TIMEOUT = Duration.ofSeconds(30);
    Duration RETRY_DELAY = Duration.ofSeconds(1);

    void acquire(Connection connection) throws SQLException;

    void release(Connection connection) throws SQLException;
}
