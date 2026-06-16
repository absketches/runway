package io.github.absketches.runway.codegen;

import java.nio.file.Path;

record ParsedMigration(
    CodegenMigrationType type,
    String version,
    String description,
    Path path,
    String checksum,
    String sql
) {
}
