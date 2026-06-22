package io.github.absketches.runway.codegen.analysis;

import java.util.List;

public record SqlImpact(
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
    boolean analysisComplete
) {
    public SqlImpact {
        readTables = List.copyOf(readTables);
        writtenTables = List.copyOf(writtenTables);
        readColumns = List.copyOf(readColumns);
        writtenColumns = List.copyOf(writtenColumns);
        runtimeReadTables = List.copyOf(runtimeReadTables);
        runtimeWriteTables = List.copyOf(runtimeWriteTables);
        runtimeReadColumns = List.copyOf(runtimeReadColumns);
        runtimeWriteColumns = List.copyOf(runtimeWriteColumns);
    }
}
