package io.github.absketches.runway.codegen;

record ParsedMigrationName(
    CodegenMigrationType type,
    String version,
    String description
) {
}
