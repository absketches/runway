package io.github.absketches.runway.codegen.migration;

import java.nio.file.Path;
import java.util.List;

public record ParsedMigration(
    String version,
    String description,
    Path path,
    String checksum,
    List<ParsedStatement> statements
) {
    public ParsedMigration {
        statements = List.copyOf(statements);
    }
}
