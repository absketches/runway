package io.github.absketches.runway.codegen.output.report;

import io.github.absketches.runway.codegen.analysis.ColumnReference;
import io.github.absketches.runway.codegen.analysis.SqlImpact;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class ImpactPoints {
    private ImpactPoints() {
    }

    static List<ImpactPoint> schemaWritePoints(SqlImpact impact) {
        return impact.type().isDml() ? List.of() : writePoints(impact);
    }

    static List<ImpactPoint> runtimeWritePoints(SqlImpact impact) {
        return columnPoints(impact.runtimeWriteColumns());
    }

    static List<ImpactPoint> runtimeReadPoints(SqlImpact impact) {
        return columnPoints(impact.runtimeReadColumns());
    }

    static List<ImpactPoint> readPoints(SqlImpact impact) {
        return columnPoints(impact.readColumns());
    }

    static List<ImpactPoint> writePoints(SqlImpact impact) {
        List<ImpactPoint> points = new ArrayList<>(columnPoints(impact.writtenColumns()));
        if (!impact.schemaObject().isEmpty()) {
            points.add(ImpactPoint.object(objectType(impact), impact.schemaObject()));
        }
        return List.copyOf(points);
    }

    static List<String> labels(Collection<ImpactPoint> points) {
        List<String> labels = new ArrayList<>();
        for (ImpactPoint point : points) {
            labels.add(point.label());
        }
        return List.copyOf(labels);
    }

    private static List<ImpactPoint> columnPoints(List<ColumnReference> columns) {
        List<ImpactPoint> points = new ArrayList<>();
        for (ColumnReference column : columns) {
            points.add(ImpactPoint.column(column));
        }
        return List.copyOf(points);
    }

    private static String objectType(SqlImpact impact) {
        return switch (impact.type()) {
            case CREATE_INDEX, DROP_INDEX -> "indexes";
            case CREATE_TRIGGER, DROP_TRIGGER -> "triggers";
            case CREATE_FUNCTION, ALTER_FUNCTION, DROP_FUNCTION -> "functions";
            case CREATE_PROCEDURE, ALTER_PROCEDURE, DROP_PROCEDURE -> "procedures";
            case CREATE_VIEW, DROP_VIEW -> "views";
            default -> "objects";
        };
    }
}
