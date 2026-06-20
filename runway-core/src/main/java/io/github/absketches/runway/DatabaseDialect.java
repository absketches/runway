package io.github.absketches.runway;

import io.github.absketches.runway.history.HistoryTableStatements;

public interface DatabaseDialect {
    String name();

    MigrationLock migrationLock();

    HistoryTableStatements historyTableStatements();
}
