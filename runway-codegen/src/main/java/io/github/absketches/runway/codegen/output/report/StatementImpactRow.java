package io.github.absketches.runway.codegen.output.report;

import io.github.absketches.runway.codegen.analysis.SqlImpact;

record StatementImpactRow(
    int migrationIndex,
    SqlImpact impact
) {
}
