package io.github.absketches.runway;

public record SqlStatement(
    String sql,
    int startLine,
    int endLine
) {
}
