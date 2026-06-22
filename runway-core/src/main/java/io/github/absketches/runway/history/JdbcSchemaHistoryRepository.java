package io.github.absketches.runway.history;

import io.github.absketches.runway.MigrationDefinition;
import io.github.absketches.runway.MigrationException;
import io.github.absketches.runway.MigrationVersion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public final class JdbcSchemaHistoryRepository implements SchemaHistoryRepository {
    private final HistoryTableStatements statements;

    public JdbcSchemaHistoryRepository(HistoryTableStatements statements) {
        this.statements = statements;
    }

    @Override
    public void createIfMissing(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(statements.createIfMissing());
        }
    }

    @Override
    public List<AppliedMigration> findAll(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(statements.selectAll())) {
            List<AppliedMigration> migrations = new ArrayList<>();
            while (resultSet.next()) {
                String version = resultSet.getString("version");
                migrations.add(new AppliedMigration(
                    resultSet.getInt("installed_rank"),
                    MigrationVersion.of(version),
                    resultSet.getString("description"),
                    resultSet.getString("script"),
                    resultSet.getString("checksum"),
                    parseInstalledOn(resultSet.getString("installed_on")),
                    resultSet.getLong("execution_time_ms"),
                    resultSet.getBoolean("success"),
                    resultSet.getString("engine_version"),
                    resultSet.getString("codegen_version")
                ));
            }
            return List.copyOf(migrations);
        }
    }

    @Override
    public void recordSuccess(
        Connection connection,
        MigrationDefinition migration,
        long executionTimeMs,
        String engineVersion,
        String codegenVersion
    ) throws SQLException {
        record(connection, migration, executionTimeMs, engineVersion, codegenVersion, true);
    }

    @Override
    public void recordFailure(
        Connection connection,
        MigrationDefinition migration,
        long executionTimeMs,
        String engineVersion,
        String codegenVersion
    ) throws SQLException {
        record(connection, migration, executionTimeMs, engineVersion, codegenVersion, false);
    }

    private void record(
        Connection connection,
        MigrationDefinition migration,
        long executionTimeMs,
        String engineVersion,
        String codegenVersion,
        boolean success
    ) throws SQLException {
        try (PreparedStatement rankStatement = connection.prepareStatement(statements.nextInstalledRank());
             ResultSet rankResult = rankStatement.executeQuery()) {
            rankResult.next();
            int rank = rankResult.getInt(1);
            try (PreparedStatement statement = connection.prepareStatement(statements.insert())) {
                statement.setInt(1, rank);
                statement.setString(2, migration.version().value());
                statement.setString(3, migration.description());
                statement.setString(4, migration.script());
                statement.setString(5, migration.checksum());
                statement.setString(6, Instant.now().toString());
                statement.setLong(7, executionTimeMs);
                statement.setBoolean(8, success);
                statement.setString(9, engineVersion);
                statement.setString(10, codegenVersion);
                statement.executeUpdate();
            }
        }
    }

    private static Instant parseInstalledOn(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new MigrationException("Invalid ISO-8601 installed_on value in runway_schema_history: " + value, e);
        }
    }
}
