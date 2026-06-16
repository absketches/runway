package io.github.absketches.runway.sqlite;

import io.github.absketches.runway.DatabaseDialect;
import io.github.absketches.runway.HistoryTableStatements;
import io.github.absketches.runway.MigrationLock;
import io.github.absketches.runway.SqlScriptParser;

public final class SqliteDialect implements DatabaseDialect {
    public static final SqliteDialect INSTANCE = new SqliteDialect();

    private final MigrationLock migrationLock = new SqliteTableLock();
    private final HistoryTableStatements historyTableStatements = new SqliteHistoryTableStatements();
    private final SqlScriptParser sqlScriptParser = new SqliteScriptParser();

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

    @Override
    public SqlScriptParser sqlScriptParser() {
        return sqlScriptParser;
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
