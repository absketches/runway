package io.github.absketches.runway;

import io.github.absketches.runway.history.AppliedMigration;
import io.github.absketches.runway.history.JdbcSchemaHistoryRepository;
import io.github.absketches.runway.history.SchemaHistoryRepository;
import io.github.absketches.runway.planning.MigrationPlan;
import io.github.absketches.runway.planning.MigrationPlanService;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Runway {
    public static final String ENGINE_VERSION = RunwayVersion.VALUE;

    private final DataSource dataSource;
    private final DatabaseDialect dialect;
    private final MigrationRegistry migrations;
    private final SchemaHistoryRepository history;

    private Runway(DataSource dataSource, DatabaseDialect dialect, MigrationRegistry migrations) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.migrations = Objects.requireNonNull(migrations, "migrations");
        this.history = new JdbcSchemaHistoryRepository(dialect.historyTableStatements());
    }

    public static MigrationResult migrate(DataSource dataSource, DatabaseDialect dialect, MigrationRegistry migrations) {
        return new Runway(dataSource, dialect, migrations).migrate();
    }

    private MigrationResult migrate() {
        validateDialect();
        try (Connection connection = dataSource.getConnection()) {
            dialect.migrationLock().acquire(connection);
            try {
                history.createIfMissing(connection);
                List<AppliedMigration> applied = history.findAll(connection);
                MigrationPlan plan = MigrationPlanService.plan(migrations.migrations(), applied);
                if (!plan.valid()) {
                    throwIfFailedMigrationExists(plan);
                    return new MigrationResult(false, List.of(), plan.validationErrors());
                }

                List<MigrationDefinition> executed = new ArrayList<>();
                for (MigrationDefinition migration : plan.pending()) {
                    executeMigration(connection, migration);
                    executed.add(migration);
                }
                return new MigrationResult(true, List.copyOf(executed), List.of());
            } finally {
                dialect.migrationLock().release(connection);
            }
        } catch (SQLException e) {
            throw new MigrationException("Runway database operation failed", e);
        }
    }

    private void validateDialect() {
        String registryDialect = migrations.dialectName();
        if (registryDialect == null || registryDialect.isBlank()) {
            return;
        }
        if (!registryDialect.equals(dialect.name())) {
            throw new MigrationException(
                "Runway migration registry was generated for " + registryDialect
                    + " but runtime dialect is " + dialect.name()
            );
        }
    }

    private static void throwIfFailedMigrationExists(MigrationPlan plan) {
        for (ValidationError error : plan.validationErrors()) {
            if (error.code() == ValidationErrorCode.FAILED_MIGRATION) {
                throw new MigrationException(error.message());
            }
        }
    }

    private void executeMigration(Connection connection, MigrationDefinition migration) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        long started = System.nanoTime();
        try {
            connection.setAutoCommit(false);
            for (String resourcePath : migration.statementResources()) {
                String sql = readStatement(migration, resourcePath);
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            }
            history.recordSuccess(connection, migration, elapsedMs(started), ENGINE_VERSION);
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            try {
                history.recordFailure(connection, migration, elapsedMs(started), ENGINE_VERSION);
                connection.commit();
            } catch (SQLException historyFailure) {
                e.addSuppressed(historyFailure);
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    historyFailure.addSuppressed(rollbackFailure);
                }
            }
            throw e;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private static String readStatement(MigrationDefinition migration, String resourcePath) {
        try (InputStream input = migration.resourceLoader().apply(resourcePath)) {
            if (input == null) {
                throw new MigrationException(
                    "SQL resource not found for " + migration.script() + ": " + resourcePath
                );
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MigrationException(
                "Failed to read SQL resource for " + migration.script() + ": " + resourcePath,
                e
            );
        }
    }

    private static long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

}
