package io.github.absketches.runway.databases.mysql;

import io.github.absketches.runway.DatabaseDialect;
import io.github.absketches.runway.MigrationLock;
import io.github.absketches.runway.history.HistoryTableStatements;

abstract class MySqlCompatibleDialect implements DatabaseDialect {
    private final String name;
    private final MigrationLock migrationLock = new MySqlNamedLock();
    private final HistoryTableStatements historyTableStatements = new MySqlHistoryTableStatements();

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
}
