package io.github.absketches.runway.codegen.output.report;

import io.github.absketches.runway.codegen.analysis.SqlImpact;
import io.github.absketches.runway.codegen.migration.ParsedMigration;
import io.github.absketches.runway.codegen.migration.ParsedStatement;

import java.util.List;
import java.util.Map;

public final class MigrationImpactReportWriter {
    private MigrationImpactReportWriter() {
    }

    public static String write(
        List<ParsedMigration> migrations,
        Map<ParsedStatement, SqlImpact> analysis
    ) {
        return MigrationImpactReportHtml.render(MigrationImpactReportData.from(migrations, analysis));
    }
}
