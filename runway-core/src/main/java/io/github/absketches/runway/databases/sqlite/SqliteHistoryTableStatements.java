package io.github.absketches.runway.databases.sqlite;

import io.github.absketches.runway.history.HistoryTableStatements;

final class SqliteHistoryTableStatements extends HistoryTableStatements {
    @Override
    protected String type(ColumnType type) {
        return switch (type) {
            case INTEGER, BIGINT, BOOLEAN -> "integer";
            case TEXT_40, TEXT_50, TEXT_80, TEXT_100, TEXT_200, TEXT_300 -> "text";
        };
    }

    @Override
    protected String installedByDefault() {
        return "'unknown'";
    }
}
