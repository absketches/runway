package io.github.absketches.runway.codegen;

import io.github.absketches.runway.codegen.analysis.SqlImpact;
import io.github.absketches.runway.codegen.analysis.SqlImpactAnalyzer;
import io.github.absketches.runway.codegen.migration.ChecksumCalculator;
import io.github.absketches.runway.codegen.migration.MigrationFilenameParser;
import io.github.absketches.runway.codegen.migration.ParsedMigration;
import io.github.absketches.runway.codegen.migration.ParsedMigrationName;
import io.github.absketches.runway.codegen.migration.ParsedStatement;
import io.github.absketches.runway.codegen.migration.VersionKey;
import io.github.absketches.runway.codegen.output.JavaSourceWriter;
import io.github.absketches.runway.codegen.output.MigrationImpactReportWriter;
import io.github.absketches.runway.codegen.sql.SqlNormalizer;
import io.github.absketches.runway.codegen.sql.split.SqlStatementSplitter;
import io.github.absketches.runway.codegen.sql.validation.RunwaySqlFeatureValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RunwayGenerator {
    void generate(CodegenOptions options) {
        try {
            if (!Files.isDirectory(options.input())) {
                throw new CodegenException("Input directory does not exist: " + options.input());
            }
            List<ParsedMigration> migrations;
            try (var files = Files.list(options.input())) {
                migrations = files
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .map(path -> readMigration(path, options))
                    .sorted(RunwayGenerator::compare)
                    .toList();
            }
            validateDuplicates(migrations);
            Map<ParsedStatement, SqlImpact> analysis =
                options.impactOutput() != null
                    ? analyze(migrations)
                    : Map.of();

            Path packageDirectory = options.sourceOutput().resolve(options.packageName().replace('.', '/'));
            Path resourceDirectory = options.resourceOutput()
                .resolve(options.packageName().replace('.', '/'))
                .resolve("runway");
            clearGeneratedJava(packageDirectory);
            deleteDirectory(resourceDirectory);
            Files.createDirectories(packageDirectory);
            writeGeneratedSource(
                packageDirectory.resolve(options.className() + ".java"),
                JavaSourceWriter.writeCatalog(
                    options.packageName(),
                    options.className(),
                    options.dialect().runtimeName(),
                    RunwayCodegenVersion.VALUE,
                    migrations
                )
            );
            writeMigrationSources(options, packageDirectory, migrations);
            writeResources(options, migrations);
            writeNativeImageMetadata(options);
            writeImpactReport(options, migrations, analysis);
        } catch (IOException e) {
            throw new CodegenException("Failed to generate Runway migration registry", e);
        }
    }

    private ParsedMigration readMigration(Path path, CodegenOptions options) {
        try {
            ParsedMigrationName parsed = MigrationFilenameParser.parse(path);
            String fileName = path.getFileName().toString();
            String sql = SqlNormalizer.normalize(Files.readAllBytes(path), fileName);
            String checksum = ChecksumCalculator.sha256(sql);
            List<ParsedStatement> statements = new ArrayList<>();
            var splitStatements = SqlStatementSplitter.split(sql, fileName, options.dialect());
            if (splitStatements.isEmpty()) {
                throw new CodegenException("Migration contains no executable SQL: " + fileName);
            }
            String migrationDirectory = resourceDirectoryName(path);
            for (int index = 0; index < splitStatements.size(); index++) {
                var statement = splitStatements.get(index);
                RunwaySqlFeatureValidator.validate(statement.sql(), fileName);
                String statementSql = terminated(statement.sql());
                String resourcePath = "/" + options.packageName().replace('.', '/')
                    + "/runway/" + migrationDirectory + "/statement-%03d.sql".formatted(index);
                statements.add(new ParsedStatement(
                    statementSql,
                    resourcePath
                ));
            }
            return new ParsedMigration(parsed.version(), parsed.description(), path, checksum, statements);
        } catch (IOException e) {
            throw new CodegenException("Failed to read migration: " + path, e);
        } catch (RuntimeException e) {
            if (e instanceof CodegenException) {
                throw e;
            }
            throw new CodegenException("Failed to parse migration: " + path, e);
        }
    }

    private static void writeResources(CodegenOptions options, List<ParsedMigration> migrations) throws IOException {
        for (ParsedMigration migration : migrations) {
            for (ParsedStatement statement : migration.statements()) {
                Path resource = options.resourceOutput().resolve(statement.resourcePath().substring(1));
                Files.createDirectories(resource.getParent());
                Files.writeString(resource, statement.sql(), StandardCharsets.UTF_8);
            }
        }
    }

    private static void writeMigrationSources(
        CodegenOptions options,
        Path packageDirectory,
        List<ParsedMigration> migrations
    ) throws IOException {
        for (ParsedMigration migration : migrations) {
            Path source = packageDirectory.resolve(JavaSourceWriter.migrationClassName(migration) + ".java");
            writeGeneratedSource(
                source,
                JavaSourceWriter.writeMigration(options.packageName(), migration)
            );
        }
    }

    private static void writeGeneratedSource(Path path, String source) throws IOException {
        if (Files.exists(path) && !isGeneratedJava(path)) {
            throw new CodegenException("Refusing to overwrite non-Runway Java source: " + path);
        }
        Files.writeString(path, source, StandardCharsets.UTF_8);
    }

    private static void writeImpactReport(
        CodegenOptions options,
        List<ParsedMigration> migrations,
        Map<ParsedStatement, SqlImpact> analysis
    ) throws IOException {
        if (options.impactOutput() == null) {
            return;
        }
        Path parent = options.impactOutput().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(
            options.impactOutput(),
            MigrationImpactReportWriter.write(migrations, analysis),
            StandardCharsets.UTF_8
        );
        Path report = options.impactOutput().toAbsolutePath().normalize();
        System.out.println("Runway codegen generated impact analysis: " + report.toUri());
    }

    private static void writeNativeImageMetadata(CodegenOptions options) throws IOException {
        Path metadata = options.resourceOutput()
            .resolve("META-INF/native-image")
            .resolve("io.github.absketches")
            .resolve("runway")
            .resolve(options.packageName() + "." + options.className())
            .resolve("reachability-metadata.json");
        Files.createDirectories(metadata.getParent());
        Files.writeString(metadata, """
            {
              "resources": [
                {
                  "condition": {
                    "typeReached": "%s.%s"
                  },
                  "glob": "%s/runway/**"
                }
              ]
            }
            """.formatted(
            options.packageName(),
            options.className(),
            options.packageName().replace('.', '/')
        ), StandardCharsets.UTF_8);
    }

    private static Map<ParsedStatement, SqlImpact> analyze(List<ParsedMigration> migrations) {
        Map<ParsedStatement, SqlImpact> analysis = new LinkedHashMap<>();
        for (ParsedMigration migration : migrations) {
            for (ParsedStatement statement : migration.statements()) {
                analysis.put(statement, SqlImpactAnalyzer.analyze(statement.sql()));
            }
        }
        return Map.copyOf(analysis);
    }

    private static String resourceDirectoryName(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.substring(0, fileName.length() - ".sql".length())
            .replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String terminated(String sql) {
        String value = sql.stripTrailing();
        if (!value.endsWith(";")) {
            value += ";";
        }
        return value + "\n";
    }

    private static int compare(ParsedMigration left, ParsedMigration right) {
        Comparator<ParsedMigration> comparator = Comparator
            .comparing((ParsedMigration migration) -> VersionKey.parse(migration.version()))
            .thenComparing(ParsedMigration::description)
            .thenComparing(migration -> migration.path().getFileName().toString());
        return comparator.compare(left, right);
    }

    private static void validateDuplicates(List<ParsedMigration> migrations) {
        Set<String> versions = new HashSet<>();
        Set<String> classNames = new HashSet<>();
        for (ParsedMigration migration : migrations) {
            if (!versions.add(migration.version())) {
                throw new CodegenException("Duplicate versioned migration version: " + migration.version());
            }
            if (!classNames.add(JavaSourceWriter.migrationClassName(migration))) {
                throw new CodegenException(
                    "Migration names generate the same Java class: " + migration.path().getFileName()
                );
            }
        }
    }

    private static void clearGeneratedJava(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(RunwayGenerator::isGeneratedJava).toList()) {
                Files.delete(file);
            }
        }
    }

    private static boolean isGeneratedJava(Path path) {
        if (!path.getFileName().toString().endsWith(".java")) {
            return false;
        }
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JavaSourceWriter.generatedMarker().equals(reader.readLine());
        } catch (IOException e) {
            return false;
        }
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

}
