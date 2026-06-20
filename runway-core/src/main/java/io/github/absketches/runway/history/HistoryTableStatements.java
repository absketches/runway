package io.github.absketches.runway.history;

public interface HistoryTableStatements {
    String createIfMissing();

    String selectAll();

    String nextInstalledRank();

    String insert();
}
