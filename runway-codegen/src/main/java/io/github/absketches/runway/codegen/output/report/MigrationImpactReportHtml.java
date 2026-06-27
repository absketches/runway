package io.github.absketches.runway.codegen.output.report;

import io.github.absketches.runway.codegen.analysis.SqlImpact;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MigrationImpactReportHtml {
    private MigrationImpactReportHtml() {
    }

    static String render(MigrationImpactReportData report) {
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Runway Impact Report</title>
              <style>
            %s
              </style>
            </head>
            <body>
              <header>
                <h1>Runway Impact Report</h1>
                <p>This report renders every migration file and the schema points it touches.</p>
                %s
                %s
              </header>
              <main>
                <div class="result-bar">
                  <h2>Migration Files</h2>
                  <div class="row-actions">
                    <span id="result-count"></span>
                    <button id="copy-selection" type="button" disabled>Copy selected</button>
                    <button id="clear-selection" type="button" disabled>Clear selection</button>
                    <button id="clear-filters" type="button">Clear filters</button>
                    <span id="copy-status" class="copy-status"></span>
                  </div>
                </div>
                %s
              </main>
              <script>
            %s
              </script>
            </body>
            </html>
            """.formatted(
            MigrationImpactReportAssets.STYLES,
            overview(report.overview()),
            filters(report),
            resultsTable(report),
            MigrationImpactReportAssets.SCRIPT
        );
    }

    private static String overview(MigrationReportOverview overview) {
        return """
            <div class="overview">
              %s
              %s
              %s
              %s
              %s
              %s
              %s
              %s
            </div>
            """.formatted(
            metric("Migration files", overview.files(), null),
            metric("Statements", overview.statements(), null),
            metric("Tables", overview.tables(), null),
            metric("Schema points", overview.schemaPoints(), null),
            metric("Merge candidates", overview.mergeCandidates(), MigrationFileStatus.MERGE_CANDIDATE),
            metric("Partial candidates", overview.partialCandidates(), MigrationFileStatus.PARTIAL_MERGE_CANDIDATE),
            metric("Incomplete analysis", overview.incompleteFiles(), MigrationFileStatus.INCOMPLETE_ANALYSIS),
            metric("Data changes", overview.dataChangeFiles(), MigrationFileStatus.DATA_CHANGES)
        );
    }

    private static String metric(String label, int count, MigrationFileStatus status) {
        if (status == null) {
            return """
                <div class="metric">
                  <strong>%s</strong>
                  <span>%s</span>
                </div>
                """.formatted(count, html(label));
        }
        return """
            <button class="metric" type="button" data-status-card="%s">
              <strong>%s</strong>
              <span>%s</span>
            </button>
            """.formatted(html(status.label()), count, html(label));
    }

    private static String filters(MigrationImpactReportData report) {
        return """
            <div class="filters">
              <div class="filter-field">
                <label for="search">File, table, object, or warning</label>
                <input id="search" type="search" placeholder="Search migration history">
              </div>
              <div class="filter-field">
                <label for="status-filter">Status</label>
                <select id="status-filter">
                  <option value="">All statuses</option>
                  %s
                </select>
              </div>
              <div class="filter-field">
                <div class="filter-toolbar">
                  <label id="table-filter-label">Tables</label>
                  <button class="filter-clear" type="button" data-clear-filter="table-filter">Clear</button>
                </div>
                <div id="table-filter" class="filter-options" role="group" aria-labelledby="table-filter-label">
                  %s
                </div>
              </div>
              <div class="filter-field">
                <div class="filter-toolbar">
                  <label id="object-filter-label">Object types</label>
                  <button class="filter-clear" type="button" data-clear-filter="object-filter">Clear</button>
                </div>
                <div id="object-filter" class="filter-options" role="group" aria-labelledby="object-filter-label">
                  %s
                </div>
              </div>
            </div>
            """.formatted(statusOptions(), checkboxes(report.tables()), checkboxes(report.objectTypes()));
    }

    private static String statusOptions() {
        StringBuilder options = new StringBuilder();
        for (MigrationFileStatus status : MigrationFileStatus.values()) {
            options.append("<option value=\"")
                .append(html(status.label()))
                .append("\">")
                .append(html(status.label()))
                .append("</option>\n");
        }
        return options.toString();
    }

    private static String checkboxes(Iterable<String> values) {
        StringBuilder options = new StringBuilder();
        for (String value : values) {
            options.append("<label class=\"filter-choice\"><input type=\"checkbox\" value=\"")
                .append(html(value))
                .append("\"><span>")
                .append(html(value))
                .append("</span></label>\n");
        }
        if (options.isEmpty()) {
            return "<div class=\"empty-filter\">No values</div>";
        }
        return options.toString();
    }

    private static String resultsTable(MigrationImpactReportData report) {
        if (report.files().isEmpty()) {
            return "<p>No migration files were found.</p>\n";
        }

        StringBuilder table = new StringBuilder("""
            <div class="table-wrap">
            <table id="migration-files">
            <thead>
            <tr>
              <th>SQL file</th>
              <th>Version</th>
              <th>Status</th>
              <th>Tables</th>
              <th>Objects</th>
              <th>Schema writes</th>
              <th>Runtime writes</th>
              <th>Runtime reads</th>
              <th>Merge with</th>
              <th>Warnings</th>
              <th>Details</th>
            </tr>
            </thead>
            <tbody>
            """);
        for (int index = report.files().size() - 1; index >= 0; index--) {
            MigrationFileImpact file = report.files().get(index);
            MigrationFileStatus status = report.status(file);
            List<String> schemaWrites = ImpactPoints.labels(file.schemaWritablePoints());
            List<String> runtimeWrites = runtimeCopyValues(file.runtimeWritePoints(), file);
            List<String> runtimeReads = runtimeCopyValues(file.runtimeReadPoints(), file);
            List<String> warningLabels = warningLabels(file);
            table.append("<tr data-file-row data-file=\"")
                .append(html(file.fileName()))
                .append("\" data-status=\"")
                .append(html(status.label()))
                .append("\" data-tables=\"")
                .append(html(String.join("|", file.tableNames())))
                .append("\" data-objects=\"")
                .append(html(String.join("|", file.objectTypes())))
                .append("\" data-search=\"")
                .append(html(searchText(file, report)))
                .append("\" data-copy-file=\"")
                .append(html(file.fileName()))
                .append("\" data-copy-version=\"")
                .append(html(file.version()))
                .append("\" data-copy-status=\"")
                .append(html(status.label()))
                .append("\" data-copy-tables=\"")
                .append(html(joinValues(file.tableNames())))
                .append("\" data-copy-objects=\"")
                .append(html(joinValues(file.objectLabels())))
                .append("\" data-copy-schema-writes=\"")
                .append(html(joinValues(schemaWrites)))
                .append("\" data-copy-runtime-writes=\"")
                .append(html(joinValues(runtimeWrites)))
                .append("\" data-copy-runtime-reads=\"")
                .append(html(joinValues(runtimeReads)))
                .append("\" data-copy-merge-with=\"")
                .append(html(joinValues(mergeTargetLabels(file, report))))
                .append("\" data-copy-warnings=\"")
                .append(html(joinValues(warningLabels)))
                .append("\" data-copy-details=\"")
                .append(html(statementCount(file)))
                .append("\">")
                .append("<td class=\"file\"><code>")
                .append(html(file.fileName()))
                .append("</code></td><td>")
                .append(html(file.version()))
                .append("</td><td><span class=\"status ")
                .append(status.cssClass())
                .append("\">")
                .append(html(status.label()))
                .append("</span></td><td>")
                .append(chips(file.tableNames(), ""))
                .append("</td><td>")
                .append(chips(file.objectLabels(), "object"))
                .append("</td><td>")
                .append(chips(schemaWrites, "schema"))
                .append("</td><td>")
                .append(runtimeChips(file.runtimeWritePoints(), file, "data"))
                .append("</td><td>")
                .append(runtimeChips(file.runtimeReadPoints(), file, "read"))
                .append("</td><td>")
                .append(mergeTargets(file, report))
                .append("</td><td>")
                .append(warnings(file))
                .append("</td><td>")
                .append(details(file))
                .append("</td></tr>\n");
        }
        return table.append("</tbody>\n</table>\n</div>\n").toString();
    }

    private static String runtimeChips(Collection<ImpactPoint> points, MigrationFileImpact file, String cssClass) {
        if (file.hasIncompleteNonDmlAnalysis()) {
            return chips(List.of("unknown"), "warning");
        }
        return chips(ImpactPoints.labels(points), cssClass);
    }

    private static String chips(List<String> values, String cssClass) {
        if (values.isEmpty()) {
            return "";
        }
        StringBuilder chips = new StringBuilder("<div class=\"chip-list\">");
        int limit = Math.min(values.size(), 6);
        for (int i = 0; i < limit; i++) {
            chips.append("<span class=\"chip");
            if (!cssClass.isEmpty()) {
                chips.append(' ').append(cssClass);
            }
            chips.append("\" title=\"")
                .append(html(values.get(i)))
                .append("\">")
                .append(html(values.get(i)))
                .append("</span>");
        }
        if (values.size() > limit) {
            chips.append("<span class=\"more\">+")
                .append(values.size() - limit)
                .append(" more</span>");
        }
        return chips.append("</div>").toString();
    }

    private static String warnings(MigrationFileImpact file) {
        return chips(warningLabels(file), "warning");
    }

    private static List<String> warningLabels(MigrationFileImpact file) {
        List<String> warnings = new ArrayList<>();
        if (file.hasIncompleteNonDmlAnalysis()) {
            warnings.add("incomplete analysis");
        }
        if (file.hasDml()) {
            warnings.add("contains DML");
        }
        return List.copyOf(warnings);
    }

    private static String details(MigrationFileImpact file) {
        StringBuilder details = new StringBuilder("<details><summary>")
            .append(statementCount(file))
            .append("</summary>");
        int statementNumber = 1;
        for (StatementImpactRow statement : file.statements()) {
            SqlImpact impact = statement.impact();
            details.append("<div class=\"statement\"><div class=\"statement-type\">")
                .append(statementNumber++)
                .append(". ")
                .append(html(impact.type().name()))
                .append(impact.analysisComplete() ? "" : " <span class=\"chip warning\">incomplete</span>")
                .append("</div>");
            appendDetail(details, "Schema writes", ImpactPoints.labels(ImpactPoints.schemaWritePoints(impact)), "schema");
            appendDetail(details, "Runtime writes", ImpactPoints.labels(ImpactPoints.runtimeWritePoints(impact)), "data");
            appendDetail(details, "Runtime reads", ImpactPoints.labels(ImpactPoints.runtimeReadPoints(impact)), "read");
            appendDetail(details, "Reads", ImpactPoints.labels(ImpactPoints.readPoints(impact)), "read");
            details.append("</div>");
        }
        return details.append("</details>").toString();
    }

    private static void appendDetail(StringBuilder details, String label, List<String> points, String cssClass) {
        if (!points.isEmpty()) {
            details.append("<h3>")
                .append(html(label))
                .append("</h3>")
                .append(chips(points, cssClass));
        }
    }

    private static String mergeTargets(MigrationFileImpact file, MigrationImpactReportData report) {
        Map<String, List<String>> targets = mergeTargetPoints(file, report);
        if (targets.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : targets.entrySet()) {
            result.append("<div><code>")
                .append(html(entry.getKey()))
                .append("</code>")
                .append(chips(entry.getValue(), "schema"))
                .append("</div>");
        }
        return result.toString();
    }

    private static List<String> mergeTargetLabels(MigrationFileImpact file, MigrationImpactReportData report) {
        List<String> targets = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : mergeTargetPoints(file, report).entrySet()) {
            targets.add(entry.getKey() + ": " + String.join(", ", entry.getValue()));
        }
        return List.copyOf(targets);
    }

    private static Map<String, List<String>> mergeTargetPoints(MigrationFileImpact file, MigrationImpactReportData report) {
        Map<String, List<String>> targets = new LinkedHashMap<>();
        for (ImpactPoint point : file.schemaWritablePoints()) {
            Integer latest = report.latestWriters().get(point);
            if (latest != null && latest != file.migrationIndex()) {
                targets.computeIfAbsent(report.files().get(latest).fileName(), ignored -> new ArrayList<>())
                    .add(point.label());
            }
        }
        return targets;
    }

    private static List<String> runtimeCopyValues(Collection<ImpactPoint> points, MigrationFileImpact file) {
        if (file.hasIncompleteNonDmlAnalysis()) {
            return List.of("unknown");
        }
        return ImpactPoints.labels(points);
    }

    private static String statementCount(MigrationFileImpact file) {
        return file.statements().size() + (file.statements().size() == 1 ? " statement" : " statements");
    }

    private static String joinValues(List<String> values) {
        return String.join(", ", values);
    }

    private static String searchText(MigrationFileImpact file, MigrationImpactReportData report) {
        StringBuilder text = new StringBuilder(file.fileName())
            .append(' ')
            .append(file.version())
            .append(' ')
            .append(report.status(file).label());
        appendSearch(text, file.tableNames());
        appendSearch(text, file.objectLabels());
        appendSearch(text, ImpactPoints.labels(file.schemaWritablePoints()));
        appendSearch(text, ImpactPoints.labels(file.runtimeWritePoints()));
        appendSearch(text, ImpactPoints.labels(file.runtimeReadPoints()));
        for (ImpactPoint point : file.schemaWritablePoints()) {
            Integer latest = report.latestWriters().get(point);
            if (latest != null && latest != file.migrationIndex()) {
                text.append(' ').append(report.files().get(latest).fileName()).append(' ').append(point.label());
            }
        }
        if (file.hasIncompleteNonDmlAnalysis()) {
            text.append(" incomplete analysis");
        }
        if (file.hasDml()) {
            text.append(" contains dml");
        }
        return text.toString().toLowerCase();
    }

    private static void appendSearch(StringBuilder text, List<String> values) {
        for (String value : values) {
            text.append(' ').append(value);
        }
    }

    private static String html(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
