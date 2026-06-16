package io.github.absketches.runway.postgresql;

import io.github.absketches.runway.DatabaseDialect;
import io.github.absketches.runway.HistoryTableStatements;
import io.github.absketches.runway.MigrationLock;
import io.github.absketches.runway.SqlScriptParser;

public final class PostgreSqlDialect implements DatabaseDialect {
    public static final PostgreSqlDialect INSTANCE = new PostgreSqlDialect();

    private final MigrationLock migrationLock = new PostgreSqlAdvisoryLock();
    private final HistoryTableStatements historyTableStatements = new PostgreSqlHistoryTableStatements();
    private final SqlScriptParser sqlScriptParser = new PostgreSqlScriptParser();

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

    @Override
    public SqlScriptParser sqlScriptParser() {
        return sqlScriptParser;
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
