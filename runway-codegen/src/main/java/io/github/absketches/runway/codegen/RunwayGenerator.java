package io.github.absketches.runway.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RunwayGenerator {
    public Path generate(Path input, Path output, String packageName, String className) {
        try {
            if (!Files.isDirectory(input)) {
                throw new CodegenException("Input directory does not exist: " + input);
            }
            List<ParsedMigration> migrations;
            try (var files = Files.list(input)) {
                migrations = files
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .map(this::readMigration)
                    .sorted(RunwayGenerator::compare)
                    .toList();
            }
            validateDuplicates(migrations);

            Path packageDirectory = output.resolve(packageName.replace('.', '/'));
            Files.createDirectories(packageDirectory);
            Path generated = packageDirectory.resolve(className + ".java");
            Files.writeString(generated, JavaSourceWriter.write(packageName, className, migrations));
            return generated;
        } catch (IOException e) {
            throw new CodegenException("Failed to generate Runway migration registry", e);
        }
    }

    private ParsedMigration readMigration(Path path) {
        try {
            ParsedMigrationName parsed = MigrationFilenameParser.parse(path);
            String sql = SqlNormalizer.normalize(Files.readAllBytes(path), path.getFileName().toString());
            String checksum = ChecksumCalculator.sha256(sql);
            return new ParsedMigration(parsed.type(), parsed.version(), parsed.description(), path, checksum, sql);
        } catch (IOException e) {
            throw new CodegenException("Failed to read migration: " + path, e);
        }
    }

    private static int compare(ParsedMigration left, ParsedMigration right) {
        Comparator<ParsedMigration> comparator = Comparator
            .comparing((ParsedMigration migration) -> migration.type() == CodegenMigrationType.REPEATABLE ? 1 : 0)
            .thenComparing(migration -> migration.version() == null ? VersionKey.ZERO : VersionKey.parse(migration.version()))
            .thenComparing(ParsedMigration::description)
            .thenComparing(migration -> migration.path().getFileName().toString());
        return comparator.compare(left, right);
    }

    private static void validateDuplicates(List<ParsedMigration> migrations) {
        Set<String> versions = new HashSet<>();
        Set<String> repeatables = new HashSet<>();
        for (ParsedMigration migration : migrations) {
            if (migration.type() == CodegenMigrationType.VERSIONED && !versions.add(migration.version())) {
                throw new CodegenException("Duplicate versioned migration version: " + migration.version());
            }
            if (migration.type() == CodegenMigrationType.REPEATABLE && !repeatables.add(migration.description())) {
                throw new CodegenException("Duplicate repeatable migration description: " + migration.description());
            }
        }
    }

}
