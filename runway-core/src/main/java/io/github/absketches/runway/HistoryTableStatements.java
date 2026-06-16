package io.github.absketches.runway;

public interface HistoryTableStatements {
    String createIfMissing();

    String selectAll();

    String nextInstalledRank();

    String insert();
}
