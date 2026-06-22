package io.github.absketches.runway.codegen.output;

import io.github.absketches.runway.codegen.analysis.ColumnReference;
import io.github.absketches.runway.codegen.analysis.SqlImpact;
import io.github.absketches.runway.codegen.analysis.SqlStatementType;
import io.github.absketches.runway.codegen.migration.ParsedMigration;
import io.github.absketches.runway.codegen.migration.ParsedStatement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MigrationImpactReportWriter {
    private static final String OBJECT_GROUP = "database objects";

    private MigrationImpactReportWriter() {
    }

    public static String write(
        List<ParsedMigration> migrations,
        Map<ParsedStatement, SqlImpact> analysis
    ) {
        List<StatementImpact> statements = statementImpacts(migrations, analysis);
        List<FileImpact> files = fileImpacts(migrations, statements);
        Map<String, List<ImpactKey>> impactGroups = impactGroups(statements);
        Map<ImpactKey, Integer> latestWriters = latestWriters(statements);

        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Runway Impact Report</title>
              <style>
                :root {
                  color-scheme: light dark;
                  --bg: #f8fafc;
                  --panel: #ffffff;
                  --text: #0f172a;
                  --muted: #64748b;
                  --border: #cbd5e1;
                  --header: #e2e8f0;
                  --active: #ecfdf5;
                  --partial: #fff7ed;
                  --merge: #fef2f2;
                  --data-change: #ecfeff;
                  --incomplete: #fffbeb;
                  --latest: #16a34a;
                  --old: #ea580c;
                  --read: #2563eb;
                  --data: #0891b2;
                  --unknown: #a16207;
                }
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  background: var(--bg);
                  color: var(--text);
                  font: 14px/1.45 system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                }
                header {
                  padding: 24px 28px 16px;
                  background: var(--panel);
                  border-bottom: 1px solid var(--border);
                  position: sticky;
                  top: 0;
                  z-index: 20;
                }
                h1, h2 { margin: 0; }
                h1 { font-size: 22px; }
                h2 { font-size: 18px; margin-top: 26px; }
                p { margin: 8px 0 0; color: var(--muted); max-width: 980px; }
                main { padding: 20px 28px 32px; }
                .controls {
                  display: flex;
                  flex-wrap: wrap;
                  gap: 10px;
                  align-items: center;
                  margin-top: 18px;
                }
                input, select, button {
                  min-height: 36px;
                  border: 1px solid var(--border);
                  border-radius: 6px;
                  padding: 6px 10px;
                  background: var(--panel);
                  color: var(--text);
                  font: inherit;
                }
                button { cursor: pointer; }
                button:disabled {
                  cursor: default;
                  color: var(--muted);
                  opacity: 0.7;
                }
                input { min-width: 300px; }
                .legend {
                  display: flex;
                  flex-wrap: wrap;
                  gap: 8px 14px;
                  margin-top: 14px;
                  color: var(--muted);
                }
                .section-bar {
                  display: flex;
                  justify-content: space-between;
                  align-items: center;
                  gap: 16px;
                  margin-top: 26px;
                  margin-bottom: 8px;
                }
                .section-bar h2 { margin-top: 0; }
                .table-wrap {
                  overflow: auto;
                  border: 1px solid var(--border);
                  border-radius: 8px;
                  background: var(--panel);
                  max-height: 72vh;
                }
                table {
                  border-collapse: separate;
                  border-spacing: 0;
                  width: max-content;
                  min-width: 100%%;
                }
                th, td {
                  border-right: 1px solid var(--border);
                  border-bottom: 1px solid var(--border);
                  padding: 7px 9px;
                  text-align: center;
                  white-space: nowrap;
                  vertical-align: middle;
                }
                th {
                  background: var(--header);
                  font-weight: 650;
                  position: sticky;
                  top: 0;
                  z-index: 5;
                }
                thead tr:nth-child(2) th { top: 34px; }
                thead tr:nth-child(3) th { top: 68px; }
                .file, .status {
                  position: sticky;
                  left: 0;
                  z-index: 4;
                  background: var(--panel);
                  text-align: left;
                }
                .status { left: 280px; min-width: 180px; }
                th.file, th.status { z-index: 8; background: var(--header); }
                .file { min-width: 280px; max-width: 420px; }
                .file code {
                  display: inline-block;
                  max-width: 390px;
                  overflow: hidden;
                  text-overflow: ellipsis;
                  vertical-align: bottom;
                }
                .cell { min-width: 54px; }
                .marker {
                  display: inline-flex;
                  align-items: center;
                  justify-content: center;
                  min-width: 22px;
                  height: 22px;
                  border-radius: 999px;
                  margin: 0 1px;
                  color: white;
                  font-size: 12px;
                  font-weight: 700;
                }
                .latest { background: var(--latest); }
                .old { background: var(--old); }
                .read { background: var(--read); }
                .data { background: var(--data); }
                .unknown { background: var(--unknown); }
                .active { background: var(--active); }
                .partial { background: var(--partial); }
                .merge { background: var(--merge); }
                .data-change { background: var(--data-change); }
                .incomplete { background: var(--incomplete); }
                .summary {
                  width: 100%%;
                  margin-top: 10px;
                  background: var(--panel);
                  border: 1px solid var(--border);
                  border-radius: 8px;
                  overflow: hidden;
                }
                .summary table {
                  width: 100%%;
                  min-width: 800px;
                }
                .summary th, .summary td { text-align: left; }
                .summary td.number, .summary th.number { text-align: right; }
                .merge-target div + div { margin-top: 4px; }
                .points { color: var(--muted); margin-left: 6px; }
                tbody tr[data-file-row] { cursor: pointer; }
                tbody tr.selected td {
                  background: #fef9c3;
                  box-shadow: inset 0 1px 0 #facc15, inset 0 -1px 0 #facc15;
                }
                tbody tr.selected td.file,
                tbody tr.selected td.status {
                  background: #fef08a;
                }
                .hidden { display: none; }
                .hidden-column { display: none; }
              </style>
            </head>
            <body>
              <header>
                <h1>Runway Impact Report</h1>
                <p>Rows are SQL files in chronological descending order. Table groups contain table columns. The database objects group contains indexes, views, triggers, and other schema objects.</p>
                <div class="controls">
                  <input id="search" type="search" placeholder="Filter by SQL file, table, column, object, or status">
                  <select id="status-filter" aria-label="Status filter">
                    <option value="">All statuses</option>
                    <option value="merge candidate">Merge candidate</option>
                    <option value="partial merge candidate">Partial merge candidate</option>
                    <option value="data changes">Data changes</option>
                    <option value="incomplete analysis">Incomplete analysis</option>
                    <option value="active">Active</option>
                  </select>
                </div>
                <div class="legend">
                  <span><span class="marker latest">W</span> latest schema write</span>
                  <span><span class="marker old">w</span> older schema write</span>
                  <span><span class="marker data">D</span> data change</span>
                  <span><span class="marker read">R</span> read</span>
                  <span><span class="marker unknown">?</span> incomplete analysis</span>
                </div>
              </header>
              <main>
                <div class="section-bar">
                  <h2>Table and Object Matrix</h2>
                  <button id="clear-selection" type="button" disabled>Clear selection</button>
                </div>
                %s
                <h2>Consolidation Candidates</h2>
                <p>A SQL file is a merge candidate only when every schema data point it touches is represented again by a later SQL file. This is a consolidation signal, not an instruction to delete historical migrations. DML is reported as data changes. Incomplete non-DML analysis reports runtime points as unknown.</p>
                %s
              </main>
              <script>
                const search = document.getElementById("search");
                const statusFilter = document.getElementById("status-filter");
                const clearSelection = document.getElementById("clear-selection");
                const rows = Array.from(document.querySelectorAll("[data-file-row]"));
                const summaryRows = Array.from(document.querySelectorAll("[data-summary-row]"));
                const columnHeaders = Array.from(document.querySelectorAll("[data-column-header]"));
                const columnCells = Array.from(document.querySelectorAll("[data-column-cell]"));
                const groupHeaders = Array.from(document.querySelectorAll("[data-group-header]"));
                const subgroupHeaders = Array.from(document.querySelectorAll("[data-subgroup-header]"));
                const allColumnIndexes = new Set(columnHeaders.map(header => Number(header.dataset.columnIndex)));

                function normalize(value) {
                  return (value || "").toLowerCase().replace(/[_\\s-]+/g, " ").trim();
                }

                function queryTokens() {
                  return normalize(search.value).split(" ").filter(Boolean);
                }

                function includesAll(haystack, tokens) {
                  return tokens.every(token => haystack.includes(token));
                }

                function matchingColumnIndexes(tokens) {
                  if (tokens.length === 0) {
                    return allColumnIndexes;
                  }
                  const visible = new Set();
                  for (const header of columnHeaders) {
                    if (includesAll(normalize(header.dataset.columnSearch), tokens)) {
                      visible.add(Number(header.dataset.columnIndex));
                    }
                  }
                  return visible.size === 0 ? allColumnIndexes : visible;
                }

                function syncHeaderSpan(header, visibleColumns) {
                  const start = Number(header.dataset.start);
                  const count = Number(header.dataset.count);
                  let visible = 0;
                  for (let index = start; index < start + count; index++) {
                    if (visibleColumns.has(index)) {
                      visible++;
                    }
                  }
                  header.colSpan = Math.max(visible, 1);
                  header.classList.toggle("hidden-column", visible === 0);
                }

                function updateSelectionState() {
                  clearSelection.disabled = rows.every(row => !row.classList.contains("selected"));
                }

                function applyFilters() {
                  const tokens = queryTokens();
                  const status = statusFilter.value;
                  const visibleColumns = matchingColumnIndexes(tokens);
                  const visibleFiles = new Set();
                  for (const header of columnHeaders) {
                    header.classList.toggle(
                      "hidden-column",
                      !visibleColumns.has(Number(header.dataset.columnIndex))
                    );
                  }
                  for (const cell of columnCells) {
                    cell.classList.toggle(
                      "hidden-column",
                      !visibleColumns.has(Number(cell.dataset.columnIndex))
                    );
                  }
                  for (const header of groupHeaders) {
                    syncHeaderSpan(header, visibleColumns);
                  }
                  for (const header of subgroupHeaders) {
                    syncHeaderSpan(header, visibleColumns);
                  }
                  for (const row of rows) {
                    const haystack = normalize(row.dataset.search);
                    const matchesText = tokens.length === 0 || includesAll(haystack, tokens);
                    const matchesStatus = !status || row.dataset.status === status;
                    const visible = matchesText && matchesStatus;
                    row.classList.toggle("hidden", !visible);
                    if (visible) {
                      visibleFiles.add(row.dataset.file);
                    }
                  }
                  for (const row of summaryRows) {
                    row.classList.toggle("hidden", !visibleFiles.has(row.dataset.file));
                  }
                }

                for (const row of rows) {
                  row.addEventListener("click", event => {
                    if (event.target.closest("a, button, input, select")) {
                      return;
                    }
                    row.classList.toggle("selected");
                    updateSelectionState();
                  });
                }

                clearSelection.addEventListener("click", () => {
                  for (const row of rows) {
                    row.classList.remove("selected");
                  }
                  updateSelectionState();
                });

                search.addEventListener("input", applyFilters);
                statusFilter.addEventListener("change", applyFilters);
              </script>
            </body>
            </html>
            """.formatted(
            matrix(files, impactGroups, latestWriters),
            consolidationTable(files, latestWriters)
        );
    }

    private static List<StatementImpact> statementImpacts(
        List<ParsedMigration> migrations,
        Map<ParsedStatement, SqlImpact> analysis
    ) {
        List<StatementImpact> statements = new ArrayList<>();
        for (int migrationIndex = 0; migrationIndex < migrations.size(); migrationIndex++) {
            ParsedMigration migration = migrations.get(migrationIndex);
            for (ParsedStatement statement : migration.statements()) {
                statements.add(new StatementImpact(migrationIndex, analysis.get(statement)));
            }
        }
        return List.copyOf(statements);
    }

    private static List<FileImpact> fileImpacts(
        List<ParsedMigration> migrations,
        List<StatementImpact> statements
    ) {
        List<FileImpact> files = new ArrayList<>();
        for (int migrationIndex = 0; migrationIndex < migrations.size(); migrationIndex++) {
            ParsedMigration migration = migrations.get(migrationIndex);
            int currentMigrationIndex = migrationIndex;
            List<StatementImpact> fileStatements = statements.stream()
                .filter(statement -> statement.migrationIndex == currentMigrationIndex)
                .toList();
            files.add(new FileImpact(migrationIndex, migration.path().getFileName().toString(), fileStatements));
        }
        return List.copyOf(files);
    }

    private static Map<String, List<ImpactKey>> impactGroups(List<StatementImpact> statements) {
        Map<String, LinkedHashSet<ImpactKey>> tableGroups = new LinkedHashMap<>();
        LinkedHashSet<ImpactKey> objectPoints = new LinkedHashSet<>();
        for (StatementImpact statement : statements) {
            for (ImpactKey point : readPoints(statement.impact())) {
                addPoint(tableGroups, objectPoints, point);
            }
            for (ImpactKey point : writePoints(statement.impact())) {
                addPoint(tableGroups, objectPoints, point);
            }
        }

        Map<String, List<ImpactKey>> grouped = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<ImpactKey>> entry : tableGroups.entrySet()) {
            grouped.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        if (!objectPoints.isEmpty()) {
            grouped.put(OBJECT_GROUP, List.copyOf(objectPoints));
        }
        return grouped;
    }

    private static void addPoint(
        Map<String, LinkedHashSet<ImpactKey>> tableGroups,
        LinkedHashSet<ImpactKey> objectPoints,
        ImpactKey point
    ) {
        if (OBJECT_GROUP.equals(point.group())) {
            objectPoints.add(point);
        } else {
            tableGroups.computeIfAbsent(point.group(), ignored -> new LinkedHashSet<>()).add(point);
        }
    }

    private static Map<ImpactKey, Integer> latestWriters(List<StatementImpact> statements) {
        Map<ImpactKey, Integer> latest = new LinkedHashMap<>();
        for (StatementImpact statement : statements) {
            for (ImpactKey point : schemaWritePoints(statement.impact())) {
                latest.put(point, statement.migrationIndex);
            }
        }
        return latest;
    }

    private static String matrix(
        List<FileImpact> files,
        Map<String, List<ImpactKey>> impactGroups,
        Map<ImpactKey, Integer> latestWriters
    ) {
        if (impactGroups.isEmpty()) {
            return "<p>No impact was detected.</p>\n";
        }

        StringBuilder table = new StringBuilder("<div class=\"table-wrap\"><table id=\"impact-matrix\">\n");
        table.append("<thead>\n<tr><th class=\"file\" rowspan=\"3\">SQL file</th><th class=\"status\" rowspan=\"3\">Status</th>");
        int start = 0;
        for (Map.Entry<String, List<ImpactKey>> entry : impactGroups.entrySet()) {
            table.append("<th data-group-header data-start=\"")
                .append(start)
                .append("\" data-count=\"")
                .append(entry.getValue().size())
                .append("\" data-search=\"")
                .append(html(groupSearch(entry.getKey(), entry.getValue())))
                .append("\" colspan=\"")
                .append(entry.getValue().size())
                .append("\">")
                .append(html(entry.getKey()))
                .append("</th>");
            start += entry.getValue().size();
        }
        table.append("</tr>\n<tr>");
        start = 0;
        for (Map.Entry<String, List<ImpactKey>> entry : impactGroups.entrySet()) {
            if (OBJECT_GROUP.equals(entry.getKey())) {
                for (ObjectSubgroup subgroup : objectSubgroups(entry.getValue())) {
                    table.append("<th data-subgroup-header data-start=\"")
                        .append(start)
                        .append("\" data-count=\"")
                        .append(subgroup.points().size())
                        .append("\" data-search=\"")
                        .append(html(subgroupSearch(entry.getKey(), subgroup)))
                        .append("\" colspan=\"")
                        .append(subgroup.points().size())
                        .append("\">")
                        .append(html(subgroup.name()))
                        .append("</th>");
                    start += subgroup.points().size();
                }
            } else {
                table.append("<th data-subgroup-header data-start=\"")
                    .append(start)
                    .append("\" data-count=\"")
                    .append(entry.getValue().size())
                    .append("\" data-search=\"")
                    .append(html(groupSearch(entry.getKey(), entry.getValue()) + " columns"))
                    .append("\" colspan=\"")
                    .append(entry.getValue().size())
                    .append("\">columns</th>");
                start += entry.getValue().size();
            }
        }
        table.append("</tr>\n<tr>");
        int columnIndex = 0;
        for (List<ImpactKey> points : impactGroups.values()) {
            for (ImpactKey point : points) {
                table.append("<th data-column-header data-column-index=\"")
                    .append(columnIndex)
                    .append("\" data-column-search=\"")
                    .append(html(columnSearch(point)))
                    .append("\">")
                    .append(html(point.name()))
                    .append("</th>");
                columnIndex++;
            }
        }
        table.append("</tr>\n</thead>\n<tbody>\n");

        for (int index = files.size() - 1; index >= 0; index--) {
            FileImpact file = files.get(index);
            String status = status(file, latestWriters);
            table.append("<tr data-file-row data-file=\"")
                .append(html(file.fileName()))
                .append("\" data-status=\"")
                .append(html(status))
                .append("\" data-search=\"")
                .append(html(searchText(files, file, impactGroups, latestWriters)))
                .append("\"><td class=\"file\"><code>")
                .append(html(file.fileName()))
                .append("</code></td><td class=\"status ")
                .append(statusClass(status))
                .append("\">")
                .append(html(status))
                .append("</td>");
            columnIndex = 0;
            for (List<ImpactKey> points : impactGroups.values()) {
                for (ImpactKey point : points) {
                    table.append("<td class=\"cell\" data-column-cell data-column-index=\"")
                        .append(columnIndex)
                        .append("\">")
                        .append(cell(files, file, point, latestWriters))
                        .append("</td>");
                    columnIndex++;
                }
            }
            table.append("</tr>\n");
        }

        return table.append("</tbody>\n</table></div>\n").toString();
    }

    private static String groupSearch(String group, List<ImpactKey> points) {
        StringBuilder text = new StringBuilder(group);
        for (ImpactKey point : points) {
            text.append(' ').append(point.subgroup()).append(' ').append(point.name());
        }
        return text.toString();
    }

    private static String subgroupSearch(String group, ObjectSubgroup subgroup) {
        StringBuilder text = new StringBuilder(group).append(' ').append(subgroup.name());
        for (ImpactKey point : subgroup.points()) {
            text.append(' ').append(point.name());
        }
        return text.toString();
    }

    private static String columnSearch(ImpactKey point) {
        String text = point.group() + " " + point.subgroup() + " " + point.name();
        if (!OBJECT_GROUP.equals(point.group())) {
            return text + " " + point.group().replace(" (table)", "") + "." + point.name();
        }
        return text;
    }

    private static String cell(
        List<FileImpact> files,
        FileImpact file,
        ImpactKey point,
        Map<ImpactKey, Integer> latestWriters
    ) {
        Map<String, String> markers = new LinkedHashMap<>();
        for (StatementImpact statement : file.statements()) {
            boolean schemaWrite = schemaWritePoints(statement.impact()).contains(point);
            boolean dataWrite = dataWritePoints(statement.impact()).contains(point);
            boolean read = readPoints(statement.impact()).contains(point);
            if (schemaWrite) {
                if (latestWriters.get(point) == file.migrationIndex()) {
                    markers.put("W", "Current schema write");
                } else {
                    markers.put("w", "Superseded by " + files.get(latestWriters.get(point)).fileName());
                }
            }
            if (dataWrite) {
                markers.put("D", "Data change");
            }
            if (read) {
                markers.put("R", "Read dependency");
            }
            if ((schemaWrite || dataWrite || read)
                && !statement.impact().type().isDml()
                && !statement.impact().analysisComplete()) {
                markers.put("?", "Incomplete analysis");
            }
        }
        if (markers.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> marker : markers.entrySet()) {
            result.append("<span class=\"marker ")
                .append(markerClass(marker.getKey()))
                .append("\" title=\"")
                .append(html(marker.getValue()))
                .append("\">")
                .append(marker.getKey())
                .append("</span>");
        }
        return result.toString();
    }

    private static String consolidationTable(
        List<FileImpact> files,
        Map<ImpactKey, Integer> latestWriters
    ) {
        StringBuilder table = new StringBuilder("""
            <div class="summary">
            <table id="consolidation-candidates">
            <thead>
            <tr><th>SQL file</th><th class="number">Schema data points</th><th class="number">Runtime write points</th><th class="number">Runtime read points</th><th class="number">Current</th><th class="number">Superseded</th><th>Merge with</th><th>Status</th></tr>
            </thead>
            <tbody>
            """);
        for (int index = files.size() - 1; index >= 0; index--) {
            FileImpact file = files.get(index);
            Set<ImpactKey> writes = schemaWritablePoints(file);
            long latest = writes.stream()
                .filter(point -> latestWriters.get(point) == file.migrationIndex())
                .count();
            long overridden = writes.size() - latest;
            String status = status(file, latestWriters);

            table.append("<tr data-summary-row data-file=\"")
                .append(html(file.fileName()))
                .append("\"><td><code>")
                .append(html(file.fileName()))
                .append("</code></td><td class=\"number\">")
                .append(writes.size())
                .append("</td><td class=\"number\">")
                .append(runtimePointCount(file.runtimeWritePoints(), file))
                .append("</td><td class=\"number\">")
                .append(runtimePointCount(file.runtimeReadPoints(), file))
                .append("</td><td class=\"number\">")
                .append(latest)
                .append("</td><td class=\"number\">")
                .append(overridden)
                .append("</td><td class=\"merge-target\">")
                .append(mergeTargets(file, files, latestWriters))
                .append("</td><td class=\"")
                .append(statusClass(status))
                .append("\">")
                .append(html(status))
                .append("</td></tr>\n");
        }
        return table.append("</tbody>\n</table>\n</div>\n").toString();
    }

    private static String status(FileImpact file, Map<ImpactKey, Integer> latestWriters) {
        Set<ImpactKey> writes = schemaWritablePoints(file);
        if (file.hasIncompleteNonDmlAnalysis()) {
            return "incomplete analysis";
        }
        if (file.hasDml()) {
            return "data changes";
        }
        if (writes.isEmpty()) {
            return "no writable data points";
        }
        boolean hasLatestWrite = writes.stream()
            .anyMatch(point -> latestWriters.get(point) == file.migrationIndex());
        if (!hasLatestWrite) {
            return "merge candidate";
        }
        boolean hasOverriddenWrite = writes.stream()
            .anyMatch(point -> latestWriters.get(point) != file.migrationIndex());
        if (hasOverriddenWrite) {
            return "partial merge candidate";
        }
        return "active";
    }

    private static Set<ImpactKey> schemaWritablePoints(FileImpact file) {
        Set<ImpactKey> writes = new LinkedHashSet<>();
        for (StatementImpact statement : file.statements()) {
            writes.addAll(schemaWritePoints(statement.impact()));
        }
        return writes;
    }

    private static String runtimePointCount(Set<ImpactKey> points, FileImpact file) {
        return file.hasIncompleteNonDmlAnalysis() ? "unknown" : Integer.toString(points.size());
    }

    private static String mergeTargets(
        FileImpact file,
        List<FileImpact> files,
        Map<ImpactKey, Integer> latestWriters
    ) {
        Map<String, List<String>> targets = new LinkedHashMap<>();
        for (ImpactKey point : schemaWritablePoints(file)) {
            Integer latest = latestWriters.get(point);
            if (latest != null && latest != file.migrationIndex()) {
                targets.computeIfAbsent(files.get(latest).fileName(), ignored -> new ArrayList<>())
                    .add(pointLabel(point));
            }
        }
        if (targets.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : targets.entrySet()) {
            result.append("<div><code>")
                .append(html(entry.getKey()))
                .append("</code><span class=\"points\">")
                .append(html(String.join(", ", entry.getValue())))
                .append("</span></div>");
        }
        return result.toString();
    }

    private static List<ImpactKey> readPoints(SqlImpact impact) {
        List<ImpactKey> points = new ArrayList<>();
        for (ColumnReference column : impact.readColumns()) {
            points.add(ImpactKey.column(column));
        }
        return List.copyOf(points);
    }

    private static List<ImpactKey> writePoints(SqlImpact impact) {
        List<ImpactKey> points = new ArrayList<>();
        for (ColumnReference column : impact.writtenColumns()) {
            points.add(ImpactKey.column(column));
        }
        if (!impact.schemaObject().isEmpty()) {
            points.add(ImpactKey.object(objectType(impact), impact.schemaObject()));
        }
        return List.copyOf(points);
    }

    private static List<ImpactKey> schemaWritePoints(SqlImpact impact) {
        return impact.type().isDml() ? List.of() : writePoints(impact);
    }

    private static List<ImpactKey> dataWritePoints(SqlImpact impact) {
        return impact.type().isDml() ? writePoints(impact) : List.of();
    }

    private static List<ObjectSubgroup> objectSubgroups(List<ImpactKey> points) {
        Map<String, List<ImpactKey>> subgroups = new LinkedHashMap<>();
        for (ImpactKey point : points) {
            subgroups.computeIfAbsent(point.subgroup(), ignored -> new ArrayList<>()).add(point);
        }
        List<ObjectSubgroup> result = new ArrayList<>();
        for (Map.Entry<String, List<ImpactKey>> entry : subgroups.entrySet()) {
            result.add(new ObjectSubgroup(entry.getKey(), List.copyOf(entry.getValue())));
        }
        return List.copyOf(result);
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

    private static String searchText(
        List<FileImpact> files,
        FileImpact file,
        Map<String, List<ImpactKey>> impactGroups,
        Map<ImpactKey, Integer> latestWriters
    ) {
        StringBuilder text = new StringBuilder(file.fileName()).append(' ').append(status(file, latestWriters));
        for (Map.Entry<String, List<ImpactKey>> entry : impactGroups.entrySet()) {
            for (ImpactKey point : entry.getValue()) {
                String value = cell(files, file, point, latestWriters);
                if (!value.isEmpty()) {
                    text.append(' ').append(entry.getKey()).append(' ').append(point.subgroup()).append(' ').append(point.name());
                }
            }
        }
        for (ImpactKey point : schemaWritablePoints(file)) {
            Integer latest = latestWriters.get(point);
            if (latest != null && latest != file.migrationIndex()) {
                text.append(' ').append(files.get(latest).fileName()).append(' ').append(pointLabel(point));
            }
        }
        return text.toString().toLowerCase();
    }

    private static String pointLabel(ImpactKey point) {
        if (OBJECT_GROUP.equals(point.group())) {
            return point.subgroup() + "." + point.name();
        }
        return point.group().replace(" (table)", "") + "." + point.name();
    }

    private static String markerClass(String marker) {
        return switch (marker) {
            case "W" -> "latest";
            case "w" -> "old";
            case "D" -> "data";
            case "R" -> "read";
            default -> "unknown";
        };
    }

    private static String statusClass(String status) {
        if (status.equals("merge candidate")) {
            return "merge";
        }
        if (status.equals("partial merge candidate")) {
            return "partial";
        }
        if (status.equals("data changes")) {
            return "data-change";
        }
        if (status.equals("incomplete analysis")) {
            return "incomplete";
        }
        return "active";
    }

    private static String html(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private record ImpactKey(String group, String subgroup, String name) {
        static ImpactKey column(ColumnReference column) {
            return new ImpactKey(column.table() + " (table)", "columns", column.name());
        }

        static ImpactKey object(String type, String name) {
            return new ImpactKey(OBJECT_GROUP, type, name);
        }
    }

    private record ObjectSubgroup(String name, List<ImpactKey> points) {
    }

    private record FileImpact(
        int migrationIndex,
        String fileName,
        List<StatementImpact> statements
    ) {
        private boolean hasDml() {
            return statements.stream().anyMatch(statement -> statement.impact().type().isDml());
        }

        private boolean hasIncompleteNonDmlAnalysis() {
            return statements.stream()
                .anyMatch(statement -> !statement.impact().type().isDml() && !statement.impact().analysisComplete());
        }

        private Set<ImpactKey> runtimeReadPoints() {
            Set<ImpactKey> points = new LinkedHashSet<>();
            for (StatementImpact statement : statements) {
                for (ColumnReference column : statement.impact().runtimeReadColumns()) {
                    points.add(ImpactKey.column(column));
                }
            }
            return points;
        }

        private Set<ImpactKey> runtimeWritePoints() {
            Set<ImpactKey> points = new LinkedHashSet<>();
            for (StatementImpact statement : statements) {
                for (ColumnReference column : statement.impact().runtimeWriteColumns()) {
                    points.add(ImpactKey.column(column));
                }
            }
            return points;
        }
    }

    private record StatementImpact(
        int migrationIndex,
        SqlImpact impact
    ) {
    }
}
