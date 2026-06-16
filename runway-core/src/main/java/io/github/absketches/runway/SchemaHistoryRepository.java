package io.github.absketches.runway;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface SchemaHistoryRepository {
    void createIfMissing(Connection connection) throws SQLException;

    List<AppliedMigration> findAll(Connection connection) throws SQLException;

    void recordSuccess(Connection connection, MigrationDefinition migration, long executionTimeMs, String engineVersion) throws SQLException;

    void recordFailure(Connection connection, MigrationDefinition migration, long executionTimeMs, String engineVersion) throws SQLException;
}
