package io.github.absketches.runway;

public interface DatabaseDialect {
    String name();

    MigrationLock migrationLock();

    HistoryTableStatements historyTableStatements();

    SqlScriptParser sqlScriptParser();

    String quoteIdentifier(String identifier);
}
