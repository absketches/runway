package io.github.absketches.runway.codegen.output.report;

import io.github.absketches.runway.codegen.analysis.ColumnReference;

record ImpactPoint(
    Kind kind,
    String owner,
    String name
) {
    static ImpactPoint column(ColumnReference column) {
        return new ImpactPoint(Kind.TABLE_COLUMN, column.table(), column.name());
    }

    static ImpactPoint object(String type, String name) {
        return new ImpactPoint(Kind.SCHEMA_OBJECT, type, name);
    }

    boolean isTableColumn() {
        return kind == Kind.TABLE_COLUMN;
    }

    boolean isSchemaObject() {
        return kind == Kind.SCHEMA_OBJECT;
    }

    String label() {
        return owner + "." + name;
    }

    enum Kind {
        TABLE_COLUMN,
        SCHEMA_OBJECT
    }
}
