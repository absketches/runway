package io.github.absketches.runway.history;

import java.util.List;
import java.util.stream.Collectors;

public abstract class HistoryTableStatements {
    private static final String TABLE = "runway_schema_history";
    private static final List<Column> COLUMNS = List.of(
        Column.INSTALLED_RANK,
        Column.VERSION,
        Column.DESCRIPTION,
        Column.SCRIPT,
        Column.CHECKSUM,
        Column.INSTALLED_BY,
        Column.INSTALLED_ON,
        Column.EXECUTION_TIME_MS,
        Column.SUCCESS,
        Column.ENGINE_VERSION,
        Column.CODEGEN_VERSION
    );
    private static final List<Column> SELECT_COLUMNS = COLUMNS.stream()
        .filter(Column::selected)
        .toList();
    private static final List<Column> INSERT_COLUMNS = COLUMNS.stream()
        .filter(Column::inserted)
        .toList();

    public final String createIfMissing() {
        return "create table if not exists " + TABLE + " (\n"
            + COLUMNS.stream()
                .map(this::definition)
                .collect(Collectors.joining(",\n"))
            + "\n)";
    }

    public final String selectAll() {
        return "select " + names(SELECT_COLUMNS)
            + "\nfrom " + TABLE
            + "\norder by installed_rank";
    }

    public final String nextInstalledRank() {
        return "select coalesce(max(installed_rank), 0) + 1 from " + TABLE;
    }

    public final String insert() {
        return "insert into " + TABLE + " ("
            + names(INSERT_COLUMNS)
            + ") values ("
            + placeholders(INSERT_COLUMNS)
            + ")";
    }

    protected abstract String type(ColumnType type);

    protected abstract String installedByDefault();

    private String definition(Column column) {
        StringBuilder definition = new StringBuilder("    ")
            .append(column.columnName)
            .append(' ')
            .append(type(column.type));
        if (column.notNull) {
            definition.append(" not null");
        }
        if (column.primaryKey) {
            definition.append(" primary key");
        }
        if (column == Column.INSTALLED_BY) {
            definition.append(" default ").append(installedByDefault());
        }
        return definition.toString();
    }

    private static String names(List<Column> columns) {
        return columns.stream()
            .map(Column::columnName)
            .collect(Collectors.joining(", "));
    }

    private static String placeholders(List<Column> columns) {
        return columns.stream()
            .map(column -> "?")
            .collect(Collectors.joining(", "));
    }

    protected enum ColumnType {
        INTEGER,
        BIGINT,
        BOOLEAN,
        TEXT_40,
        TEXT_50,
        TEXT_80,
        TEXT_100,
        TEXT_200,
        TEXT_300
    }

    private enum Column {
        INSTALLED_RANK("installed_rank", ColumnType.INTEGER, true, true, true, true),
        VERSION("version", ColumnType.TEXT_100, false, false, true, true),
        DESCRIPTION("description", ColumnType.TEXT_200, true, false, true, true),
        SCRIPT("script", ColumnType.TEXT_300, true, false, true, true),
        CHECKSUM("checksum", ColumnType.TEXT_80, true, false, true, true),
        INSTALLED_BY("installed_by", ColumnType.TEXT_100, true, false, false, false),
        INSTALLED_ON("installed_on", ColumnType.TEXT_40, true, false, true, true),
        EXECUTION_TIME_MS("execution_time_ms", ColumnType.BIGINT, true, false, true, true),
        SUCCESS("success", ColumnType.BOOLEAN, true, false, true, true),
        ENGINE_VERSION("engine_version", ColumnType.TEXT_50, true, false, true, true),
        CODEGEN_VERSION("codegen_version", ColumnType.TEXT_50, true, false, true, true);

        private final String columnName;
        private final ColumnType type;
        private final boolean notNull;
        private final boolean primaryKey;
        private final boolean selected;
        private final boolean inserted;

        Column(
            String columnName,
            ColumnType type,
            boolean notNull,
            boolean primaryKey,
            boolean selected,
            boolean inserted
        ) {
            this.columnName = columnName;
            this.type = type;
            this.notNull = notNull;
            this.primaryKey = primaryKey;
            this.selected = selected;
            this.inserted = inserted;
        }

        private String columnName() {
            return columnName;
        }

        private boolean selected() {
            return selected;
        }

        private boolean inserted() {
            return inserted;
        }
    }
}
