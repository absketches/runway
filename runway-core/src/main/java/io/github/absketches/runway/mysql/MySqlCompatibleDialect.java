package io.github.absketches.runway.mysql;

import io.github.absketches.runway.DatabaseDialect;
import io.github.absketches.runway.HistoryTableStatements;
import io.github.absketches.runway.MigrationLock;
import io.github.absketches.runway.SqlScriptParser;

abstract class MySqlCompatibleDialect implements DatabaseDialect {
    private final String name;
    private final MigrationLock migrationLock = new MySqlNamedLock();
    private final HistoryTableStatements historyTableStatements = new MySqlHistoryTableStatements();
    private final SqlScriptParser sqlScriptParser = new MySqlScriptParser();

    MySqlCompatibleDialect(String name) {
        this.name = name;
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final MigrationLock migrationLock() {
        return migrationLock;
    }

    @Override
    public final HistoryTableStatements historyTableStatements() {
        return historyTableStatements;
    }

    @Override
    public final SqlScriptParser sqlScriptParser() {
        return sqlScriptParser;
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}
