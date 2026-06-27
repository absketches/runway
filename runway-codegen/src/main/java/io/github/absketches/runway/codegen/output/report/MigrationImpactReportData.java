package io.github.absketches.runway.codegen.output.report;

import io.github.absketches.runway.codegen.analysis.SqlImpact;
import io.github.absketches.runway.codegen.migration.ParsedMigration;
import io.github.absketches.runway.codegen.migration.ParsedStatement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

record MigrationImpactReportData(
    List<MigrationFileImpact> files,
    List<StatementImpactRow> statements,
    Map<ImpactPoint, Integer> latestWriters,
    Set<String> tables,
    Set<String> objectTypes,
    MigrationReportOverview overview
) {
    MigrationImpactReportData {
        files = List.copyOf(files);
        statements = List.copyOf(statements);
        latestWriters = Collections.unmodifiableMap(new LinkedHashMap<>(latestWriters));
        tables = Collections.unmodifiableSet(new LinkedHashSet<>(tables));
        objectTypes = Collections.unmodifiableSet(new LinkedHashSet<>(objectTypes));
    }

    static MigrationImpactReportData from(
        List<ParsedMigration> migrations,
        Map<ParsedStatement, SqlImpact> analysis
    ) {
        List<StatementImpactRow> statements = statementRows(migrations, analysis);
        List<MigrationFileImpact> files = fileRows(migrations, statements);
        Map<ImpactPoint, Integer> latestWriters = latestWriters(statements);
        Set<String> tables = tables(files);
        Set<String> objectTypes = objectTypes(files);
        return new MigrationImpactReportData(
            files,
            statements,
            latestWriters,
            tables,
            objectTypes,
            overview(files, statements, latestWriters, tables)
        );
    }

    MigrationFileStatus status(MigrationFileImpact file) {
        return status(file, latestWriters);
    }

    private static List<StatementImpactRow> statementRows(
        List<ParsedMigration> migrations,
        Map<ParsedStatement, SqlImpact> analysis
    ) {
        List<StatementImpactRow> statements = new ArrayList<>();
        for (int migrationIndex = 0; migrationIndex < migrations.size(); migrationIndex++) {
            ParsedMigration migration = migrations.get(migrationIndex);
            for (ParsedStatement statement : migration.statements()) {
                statements.add(new StatementImpactRow(migrationIndex, analysis.get(statement)));
            }
        }
        return List.copyOf(statements);
    }

    private static List<MigrationFileImpact> fileRows(
        List<ParsedMigration> migrations,
        List<StatementImpactRow> statements
    ) {
        List<List<StatementImpactRow>> grouped = new ArrayList<>();
        for (int i = 0; i < migrations.size(); i++) {
            grouped.add(new ArrayList<>());
        }
        for (StatementImpactRow statement : statements) {
            grouped.get(statement.migrationIndex()).add(statement);
        }

        List<MigrationFileImpact> files = new ArrayList<>();
        for (int migrationIndex = 0; migrationIndex < migrations.size(); migrationIndex++) {
            ParsedMigration migration = migrations.get(migrationIndex);
            files.add(new MigrationFileImpact(
                migrationIndex,
                migration.version(),
                migration.path().getFileName().toString(),
                List.copyOf(grouped.get(migrationIndex))
            ));
        }
        return List.copyOf(files);
    }

    private static Map<ImpactPoint, Integer> latestWriters(List<StatementImpactRow> statements) {
        Map<ImpactPoint, Integer> latest = new LinkedHashMap<>();
        for (StatementImpactRow statement : statements) {
            for (ImpactPoint point : ImpactPoints.schemaWritePoints(statement.impact())) {
                latest.put(point, statement.migrationIndex());
            }
        }
        return latest;
    }

    private static Set<String> tables(List<MigrationFileImpact> files) {
        Set<String> tables = new LinkedHashSet<>();
        for (MigrationFileImpact file : files) {
            tables.addAll(file.tableNames());
        }
        return tables;
    }

    private static Set<String> objectTypes(List<MigrationFileImpact> files) {
        Set<String> types = new LinkedHashSet<>();
        for (MigrationFileImpact file : files) {
            types.addAll(file.objectTypes());
        }
        return types;
    }

    private static MigrationReportOverview overview(
        List<MigrationFileImpact> files,
        List<StatementImpactRow> statements,
        Map<ImpactPoint, Integer> latestWriters,
        Set<String> tables
    ) {
        int merge = 0;
        int partial = 0;
        int incomplete = 0;
        int data = 0;
        Set<ImpactPoint> schemaPoints = new LinkedHashSet<>();
        for (MigrationFileImpact file : files) {
            MigrationFileStatus status = status(file, latestWriters);
            if (status == MigrationFileStatus.MERGE_CANDIDATE) {
                merge++;
            } else if (status == MigrationFileStatus.PARTIAL_MERGE_CANDIDATE) {
                partial++;
            } else if (status == MigrationFileStatus.INCOMPLETE_ANALYSIS) {
                incomplete++;
            } else if (status == MigrationFileStatus.DATA_CHANGES) {
                data++;
            }
            schemaPoints.addAll(file.schemaWritablePoints());
        }
        return new MigrationReportOverview(
            files.size(),
            statements.size(),
            tables.size(),
            schemaPoints.size(),
            merge,
            partial,
            incomplete,
            data
        );
    }

    private static MigrationFileStatus status(
        MigrationFileImpact file,
        Map<ImpactPoint, Integer> latestWriters
    ) {
        Set<ImpactPoint> writes = file.schemaWritablePoints();
        if (file.hasIncompleteNonDmlAnalysis()) {
            return MigrationFileStatus.INCOMPLETE_ANALYSIS;
        }
        if (file.hasDml()) {
            return MigrationFileStatus.DATA_CHANGES;
        }
        if (writes.isEmpty()) {
            return MigrationFileStatus.NO_WRITABLE_DATA_POINTS;
        }
        boolean hasLatestWrite = writes.stream()
            .anyMatch(point -> latestWriters.get(point) == file.migrationIndex());
        if (!hasLatestWrite) {
            return MigrationFileStatus.MERGE_CANDIDATE;
        }
        boolean hasOverriddenWrite = writes.stream()
            .anyMatch(point -> latestWriters.get(point) != file.migrationIndex());
        if (hasOverriddenWrite) {
            return MigrationFileStatus.PARTIAL_MERGE_CANDIDATE;
        }
        return MigrationFileStatus.ACTIVE;
    }
}
