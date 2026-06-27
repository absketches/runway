package io.github.absketches.runway.codegen.output.report;

import io.github.absketches.runway.codegen.analysis.SqlImpact;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

record MigrationFileImpact(
    int migrationIndex,
    String version,
    String fileName,
    List<StatementImpactRow> statements
) {
    MigrationFileImpact {
        statements = List.copyOf(statements);
    }

    boolean hasDml() {
        return statements.stream().anyMatch(statement -> statement.impact().type().isDml());
    }

    boolean hasIncompleteNonDmlAnalysis() {
        return statements.stream()
            .anyMatch(statement -> !statement.impact().type().isDml() && !statement.impact().analysisComplete());
    }

    Set<ImpactPoint> schemaWritablePoints() {
        Set<ImpactPoint> points = new LinkedHashSet<>();
        for (StatementImpactRow statement : statements) {
            points.addAll(ImpactPoints.schemaWritePoints(statement.impact()));
        }
        return points;
    }

    Set<ImpactPoint> runtimeReadPoints() {
        Set<ImpactPoint> points = new LinkedHashSet<>();
        for (StatementImpactRow statement : statements) {
            points.addAll(ImpactPoints.runtimeReadPoints(statement.impact()));
        }
        return points;
    }

    Set<ImpactPoint> runtimeWritePoints() {
        Set<ImpactPoint> points = new LinkedHashSet<>();
        for (StatementImpactRow statement : statements) {
            points.addAll(ImpactPoints.runtimeWritePoints(statement.impact()));
        }
        return points;
    }

    List<String> tableNames() {
        Set<String> tables = new LinkedHashSet<>();
        for (StatementImpactRow statement : statements) {
            SqlImpact impact = statement.impact();
            tables.addAll(impact.readTables());
            tables.addAll(impact.writtenTables());
            tables.addAll(impact.runtimeReadTables());
            tables.addAll(impact.runtimeWriteTables());
            addTables(tables, ImpactPoints.readPoints(impact));
            addTables(tables, ImpactPoints.writePoints(impact));
            addTables(tables, ImpactPoints.runtimeReadPoints(impact));
            addTables(tables, ImpactPoints.runtimeWritePoints(impact));
        }
        return List.copyOf(tables);
    }

    List<String> objectTypes() {
        Set<String> types = new LinkedHashSet<>();
        for (String label : objectLabels()) {
            int delimiter = label.indexOf('.');
            if (delimiter > 0) {
                types.add(label.substring(0, delimiter));
            }
        }
        return List.copyOf(types);
    }

    List<String> objectLabels() {
        Set<String> objects = new LinkedHashSet<>();
        for (StatementImpactRow statement : statements) {
            for (ImpactPoint point : ImpactPoints.writePoints(statement.impact())) {
                if (point.isSchemaObject()) {
                    objects.add(point.label());
                }
            }
        }
        return List.copyOf(objects);
    }

    private static void addTables(Set<String> tables, List<ImpactPoint> points) {
        for (ImpactPoint point : points) {
            if (point.isTableColumn()) {
                tables.add(point.owner());
            }
        }
    }
}
