package io.github.absketches.runway;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Runway {
    public static final String ENGINE_VERSION = RunwayVersion.VALUE;

    private final DataSource dataSource;
    private final DatabaseDialect dialect;
    private final MigrationRegistry migrations;
    private final SchemaHistoryRepository history;
    private final MigrationPlanService planner;

    private Runway(Builder builder) {
        this.dataSource = Objects.requireNonNull(builder.dataSource, "dataSource");
        this.dialect = Objects.requireNonNull(builder.dialect, "dialect");
        this.migrations = Objects.requireNonNull(builder.migrations, "migrations");
        this.history = new JdbcSchemaHistoryRepository(dialect.historyTableStatements());
        this.planner = new MigrationPlanService();
    }

    public static MigrationResult migrate(DataSource dataSource, DatabaseDialect dialect, MigrationRegistry migrations) {
        return builder().dataSource(dataSource).dialect(dialect).migrations(migrations).build().migrate();
    }

    public static Builder builder() {
        return new Builder();
    }

    public MigrationResult migrate() {
        return withConnection(connection -> {
            dialect.migrationLock().acquire(connection);
            try {
                history.createIfMissing(connection);
                List<AppliedMigration> applied = history.findAll(connection);
                MigrationPlan plan = planner.plan(migrations.migrations(), applied);
                if (!plan.valid()) {
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
        });
    }

    public MigrationResult validate() {
        return withConnection(connection -> {
            history.createIfMissing(connection);
            MigrationPlan plan = planner.plan(migrations.migrations(), history.findAll(connection));
            return new MigrationResult(plan.valid(), List.of(), plan.validationErrors());
        });
    }

    private void executeMigration(Connection connection, MigrationDefinition migration) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        long started = System.nanoTime();
        try {
            connection.setAutoCommit(false);
            for (SqlStatement sqlStatement : dialect.sqlScriptParser().parse(migration.sql(), migration.script())) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sqlStatement.sql());
                }
            }
            connection.commit();
            history.recordSuccess(connection, migration, elapsedMs(started), ENGINE_VERSION);
        } catch (SQLException | RuntimeException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            history.recordFailure(connection, migration, elapsedMs(started), ENGINE_VERSION);
            throw e;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private <T> T withConnection(JdbcOperation<T> operation) {
        try (Connection connection = dataSource.getConnection()) {
            return operation.run(connection);
        } catch (SQLException e) {
            throw new MigrationException("Runway database operation failed", e);
        }
    }

    @FunctionalInterface
    private interface JdbcOperation<T> {
        T run(Connection connection) throws SQLException;
    }

    public static final class Builder {
        private DataSource dataSource;
        private DatabaseDialect dialect;
        private MigrationRegistry migrations;

        public Builder dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public Builder dialect(DatabaseDialect dialect) {
            this.dialect = dialect;
            return this;
        }

        public Builder migrations(MigrationRegistry migrations) {
            this.migrations = migrations;
            return this;
        }

        public Runway build() {
            return new Runway(this);
        }
    }
}
