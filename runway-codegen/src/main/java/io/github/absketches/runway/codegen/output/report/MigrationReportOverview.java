package io.github.absketches.runway.codegen.output.report;

record MigrationReportOverview(
    int files,
    int statements,
    int tables,
    int schemaPoints,
    int mergeCandidates,
    int partialCandidates,
    int incompleteFiles,
    int dataChangeFiles
) {
}
