package io.github.absketches.runway.codegen.analysis;

public record ColumnReference(
    String table,
    String name
) {
    public String qualifiedName() {
        return table + "." + name;
    }
}
