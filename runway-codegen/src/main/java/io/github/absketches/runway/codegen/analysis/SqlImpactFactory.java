package io.github.absketches.runway.codegen.analysis;

import java.util.List;

final class SqlImpactFactory {
    private SqlImpactFactory() {
    }

    static List<ColumnReference> columnReferences(String table, List<String> columns) {
        return columns.stream().map(column -> new ColumnReference(table, column)).toList();
    }

    static SqlImpact create(
        SqlStatementType type,
        String schemaObject,
        List<String> readTables,
        List<String> writtenTables,
        List<ColumnReference> readColumns,
        List<ColumnReference> writtenColumns,
        boolean complete
    ) {
        return create(
            type,
            schemaObject,
            readTables,
            writtenTables,
            readColumns,
            writtenColumns,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            complete
        );
    }

    static SqlImpact create(
        SqlStatementType type,
        String schemaObject,
        List<String> readTables,
        List<String> writtenTables,
        List<ColumnReference> readColumns,
        List<ColumnReference> writtenColumns,
        List<String> runtimeReadTables,
        List<String> runtimeWriteTables,
        List<ColumnReference> runtimeReadColumns,
        List<ColumnReference> runtimeWriteColumns,
        boolean complete
    ) {
        return new SqlImpact(
            type,
            schemaObject,
            readTables,
            writtenTables,
            readColumns,
            writtenColumns,
            runtimeReadTables,
            runtimeWriteTables,
            runtimeReadColumns,
            runtimeWriteColumns,
            complete
        );
    }

    static SqlImpact unknown() {
        return create(
            SqlStatementType.UNKNOWN,
            "",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            false
        );
    }
}
