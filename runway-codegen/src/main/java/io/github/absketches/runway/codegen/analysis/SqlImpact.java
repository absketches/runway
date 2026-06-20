package io.github.absketches.runway.codegen.analysis;

import java.util.List;

public record SqlImpact(
    SqlStatementType type,
    String schemaObject,
    List<String> readTables,
    List<String> writtenTables,
    List<ColumnReference> readColumns,
    List<ColumnReference> writtenColumns,
    boolean analysisComplete
) {
    public SqlImpact {
        readTables = List.copyOf(readTables);
        writtenTables = List.copyOf(writtenTables);
        readColumns = List.copyOf(readColumns);
        writtenColumns = List.copyOf(writtenColumns);
    }
}
