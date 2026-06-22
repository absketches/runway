package io.github.absketches.runway.codegen.analysis;

public final class SqlImpactAnalyzer {
    private SqlImpactAnalyzer() {
    }

    public static SqlImpact analyze(String sql) {
        String statement = SqlSyntaxScanner.trimTrailingSemicolon(SqlSyntaxScanner.stripLeadingComments(sql).strip());
        if (statement.isEmpty()) {
            return SqlImpactFactory.unknown();
        }

        SqlImpact impact = DdlImpactAnalyzer.analyze(statement);
        if (impact != null) {
            return impact;
        }

        impact = TriggerImpactAnalyzer.analyze(statement);
        if (impact != null) {
            return impact;
        }

        impact = RoutineImpactAnalyzer.analyze(statement);
        if (impact != null) {
            return impact;
        }

        impact = ViewImpactAnalyzer.analyze(statement);
        if (impact != null) {
            return impact;
        }

        impact = DmlImpactAnalyzer.analyze(statement);
        if (impact != null) {
            return impact;
        }

        return SqlImpactFactory.unknown();
    }
}
