package io.github.absketches.runway.databases.postgresql;

import io.github.absketches.runway.DatabaseDialect;
import io.github.absketches.runway.MigrationLock;
import io.github.absketches.runway.history.HistoryTableStatements;

public final class PostgreSqlDialect implements DatabaseDialect {
    public static final PostgreSqlDialect INSTANCE = new PostgreSqlDialect();

    private final MigrationLock migrationLock = new PostgreSqlAdvisoryLock();
    private final HistoryTableStatements historyTableStatements = new PostgreSqlHistoryTableStatements();

    private PostgreSqlDialect() {
    }

    @Override
    public String name() {
        return "PostgreSQL";
    }

    @Override
    public MigrationLock migrationLock() {
        return migrationLock;
    }

    @Override
    public HistoryTableStatements historyTableStatements() {
        return historyTableStatements;
    }
}
