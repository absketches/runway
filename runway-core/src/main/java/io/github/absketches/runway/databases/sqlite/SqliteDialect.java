package io.github.absketches.runway.databases.sqlite;

import io.github.absketches.runway.DatabaseDialect;
import io.github.absketches.runway.MigrationLock;
import io.github.absketches.runway.history.HistoryTableStatements;

public final class SqliteDialect implements DatabaseDialect {
    public static final SqliteDialect INSTANCE = new SqliteDialect();

    private final MigrationLock migrationLock = new SqliteTableLock();
    private final HistoryTableStatements historyTableStatements = new SqliteHistoryTableStatements();

    private SqliteDialect() {
    }

    @Override
    public String name() {
        return "SQLite";
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
