package io.github.absketches.runway.databases.mysql;

import io.github.absketches.runway.history.HistoryTableStatements;

final class MySqlHistoryTableStatements extends HistoryTableStatements {
    @Override
    protected String type(ColumnType type) {
        return switch (type) {
            case INTEGER -> "integer";
            case BIGINT -> "bigint";
            case BOOLEAN -> "boolean";
            case TEXT_40 -> "varchar(40)";
            case TEXT_50 -> "varchar(50)";
            case TEXT_80 -> "varchar(80)";
            case TEXT_100 -> "varchar(100)";
            case TEXT_200 -> "varchar(200)";
            case TEXT_300 -> "varchar(300)";
        };
    }

    @Override
    protected String installedByDefault() {
        return "(current_user())";
    }
}
