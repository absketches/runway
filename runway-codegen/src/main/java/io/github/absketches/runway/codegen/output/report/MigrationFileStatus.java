package io.github.absketches.runway.codegen.output.report;

enum MigrationFileStatus {
    MERGE_CANDIDATE("merge candidate", "merge"),
    PARTIAL_MERGE_CANDIDATE("partial merge candidate", "partial"),
    DATA_CHANGES("data changes", "data-change"),
    INCOMPLETE_ANALYSIS("incomplete analysis", "incomplete"),
    ACTIVE("active", "active"),
    NO_WRITABLE_DATA_POINTS("no writable data points", "active");

    private final String label;
    private final String cssClass;

    MigrationFileStatus(String label, String cssClass) {
        this.label = label;
        this.cssClass = cssClass;
    }

    String label() {
        return label;
    }

    String cssClass() {
        return cssClass;
    }
}
