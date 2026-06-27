package io.github.absketches.runway.codegen.output.report;

final class MigrationImpactReportAssets {

    private MigrationImpactReportAssets() {
    }

    static final String STYLES = """
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
          --chip: #eef2ff;
          --chip-text: #312e81;
          --warning: #a16207;
          --selected: #fef9c3;
        }
        * { box-sizing: border-box; }
        body {
          margin: 0;
          background: var(--bg);
          color: var(--text);
          font: 14px/1.45 system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
        }
        header {
          padding: 24px 28px 18px;
          background: var(--panel);
          border-bottom: 1px solid var(--border);
          position: sticky;
          top: 0;
          z-index: 10;
        }
        main { padding: 22px 28px 36px; }
        h1, h2, h3 { margin: 0; }
        h1 { font-size: 22px; }
        h2 { font-size: 18px; margin-top: 26px; }
        h3 { font-size: 15px; margin-top: 14px; }
        p { margin: 8px 0 0; color: var(--muted); max-width: 1040px; }
        button, input, select {
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
          opacity: 0.65;
        }
        input { min-width: 300px; }
        .overview {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
          gap: 10px;
          margin-top: 18px;
        }
        .metric {
          text-align: left;
          border: 1px solid var(--border);
          background: var(--panel);
          border-radius: 8px;
          padding: 12px;
        }
        .metric strong {
          display: block;
          font-size: 22px;
          line-height: 1.1;
        }
        .metric span {
          display: block;
          margin-top: 4px;
          color: var(--muted);
        }
        .filters {
          display: grid;
          grid-template-columns: minmax(260px, 2fr) minmax(180px, 1fr) repeat(2, minmax(220px, 1fr));
          gap: 10px;
          align-items: start;
          margin-top: 18px;
        }
        .filter-field label {
          display: block;
          margin-bottom: 4px;
          color: var(--muted);
          font-size: 12px;
          font-weight: 650;
        }
        .filter-field input,
        .filter-field select {
          width: 100%;
        }
        .filter-toolbar {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 8px;
          margin-bottom: 4px;
        }
        .filter-toolbar label {
          margin-bottom: 0;
        }
        .filter-clear {
          min-height: 0;
          padding: 0;
          border: 0;
          background: transparent;
          color: var(--chip-text);
          font-size: 12px;
          font-weight: 650;
        }
        .filter-options {
          max-height: 116px;
          overflow: auto;
          border: 1px solid var(--border);
          border-radius: 6px;
          background: var(--panel);
          padding: 5px;
        }
        .filter-choice {
          display: flex;
          align-items: center;
          gap: 7px;
          padding: 4px 5px;
          border-radius: 5px;
        }
        .filter-choice:hover {
          background: var(--header);
        }
        .filter-choice input {
          width: auto;
          min-width: 0;
          min-height: 0;
        }
        .empty-filter {
          padding: 5px;
          color: var(--muted);
        }
        .result-bar {
          display: flex;
          justify-content: space-between;
          align-items: center;
          gap: 12px;
          margin: 22px 0 8px;
        }
        .row-actions {
          display: flex;
          align-items: center;
          justify-content: flex-end;
          flex-wrap: wrap;
          gap: 8px;
        }
        .copy-status {
          min-width: 92px;
          color: var(--muted);
        }
        .table-wrap {
          overflow: auto;
          border: 1px solid var(--border);
          border-radius: 8px;
          background: var(--panel);
          max-height: 74vh;
        }
        table {
          border-collapse: separate;
          border-spacing: 0;
          width: 100%;
          min-width: 1200px;
        }
        th, td {
          border-right: 1px solid var(--border);
          border-bottom: 1px solid var(--border);
          padding: 8px 10px;
          text-align: left;
          vertical-align: top;
        }
        th {
          position: sticky;
          top: 0;
          z-index: 5;
          background: var(--header);
          font-weight: 700;
        }
        tbody tr[data-file-row] { cursor: pointer; }
        tbody tr.selected td {
          background: var(--selected);
          box-shadow: inset 0 1px 0 #facc15, inset 0 -1px 0 #facc15;
        }
        code {
          font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, "Liberation Mono", monospace;
        }
        .file code {
          display: inline-block;
          max-width: 320px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          vertical-align: bottom;
        }
        .status {
          display: inline-block;
          border-radius: 999px;
          padding: 3px 8px;
          font-weight: 650;
          white-space: nowrap;
        }
        .active { background: var(--active); }
        .partial { background: var(--partial); }
        .merge { background: var(--merge); }
        .data-change { background: var(--data-change); }
        .incomplete { background: var(--incomplete); }
        .chip-list {
          display: flex;
          flex-wrap: wrap;
          gap: 5px;
          max-width: 340px;
        }
        .chip {
          display: inline-flex;
          align-items: center;
          max-width: 260px;
          border-radius: 999px;
          background: var(--chip);
          color: var(--chip-text);
          padding: 3px 7px;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        .chip.schema { background: #dcfce7; color: #14532d; }
        .chip.data { background: #cffafe; color: #164e63; }
        .chip.read { background: #dbeafe; color: #1e3a8a; }
        .chip.object { background: #f3e8ff; color: #581c87; }
        .chip.warning { background: #fef3c7; color: var(--warning); }
        .more { color: var(--muted); }
        details {
          max-width: 360px;
        }
        summary {
          cursor: pointer;
          color: var(--chip-text);
          font-weight: 650;
        }
        .statement {
          margin-top: 8px;
          padding-top: 8px;
          border-top: 1px solid var(--border);
        }
        .statement:first-of-type {
          border-top: 0;
        }
        .statement-type {
          font-weight: 700;
          margin-bottom: 5px;
        }
        .hidden { display: none; }
        @media (max-width: 980px) {
          header { position: static; }
          .filters { grid-template-columns: 1fr; }
          input { min-width: 0; }
        }
        """;

    static final String SCRIPT = """
        const search = document.getElementById("search");
        const statusFilter = document.getElementById("status-filter");
        const tableFilter = document.getElementById("table-filter");
        const objectFilter = document.getElementById("object-filter");
        const clearFilters = document.getElementById("clear-filters");
        const copySelection = document.getElementById("copy-selection");
        const copyStatus = document.getElementById("copy-status");
        const clearSelection = document.getElementById("clear-selection");
        const resultCount = document.getElementById("result-count");
        const rows = Array.from(document.querySelectorAll("[data-file-row]"));
        const metrics = Array.from(document.querySelectorAll("[data-status-card]"));
        const copyColumns = [
          ["SQL file", "copyFile"],
          ["Version", "copyVersion"],
          ["Status", "copyStatus"],
          ["Tables", "copyTables"],
          ["Objects", "copyObjects"],
          ["Schema writes", "copySchemaWrites"],
          ["Runtime writes", "copyRuntimeWrites"],
          ["Runtime reads", "copyRuntimeReads"],
          ["Merge with", "copyMergeWith"],
          ["Warnings", "copyWarnings"],
          ["Details", "copyDetails"]
        ];

        function normalize(value) {
          return (value || "").toLowerCase().replace(/[_\\s-]+/g, " ").trim();
        }

        function tokens() {
          return normalize(search.value).split(" ").filter(Boolean);
        }

        function checkedValues(container) {
          return Array.from(container.querySelectorAll("input[type='checkbox']:checked"))
            .map(input => input.value);
        }

        function includesAll(haystack, values) {
          return values.every(value => haystack.includes(value));
        }

        function intersects(rowValue, selected) {
          if (selected.length === 0) {
            return true;
          }
          const values = (rowValue || "").split("|").filter(Boolean);
          return selected.some(value => values.includes(value));
        }

        function applyFilters() {
          const query = tokens();
          const status = statusFilter.value;
          const tables = checkedValues(tableFilter);
          const objects = checkedValues(objectFilter);
          let visible = 0;
          for (const row of rows) {
            const matchesSearch = query.length === 0 || includesAll(normalize(row.dataset.search), query);
            const matchesStatus = !status || row.dataset.status === status;
            const matchesTables = intersects(row.dataset.tables, tables);
            const matchesObjects = intersects(row.dataset.objects, objects);
            const show = matchesSearch && matchesStatus && matchesTables && matchesObjects;
            row.classList.toggle("hidden", !show);
            if (show) {
              visible++;
            }
          }
          resultCount.textContent = `Showing ${visible} of ${rows.length} files`;
        }

        function updateSelectionState() {
          const hasSelection = selectedRows().length > 0;
          clearSelection.disabled = !hasSelection;
          copySelection.disabled = !hasSelection;
        }

        function selectedRows() {
          return rows.filter(row => row.classList.contains("selected"));
        }

        for (const row of rows) {
          row.addEventListener("click", event => {
            if (event.target.closest("a, button, input, select, summary, details")) {
              return;
            }
            row.classList.toggle("selected");
            updateSelectionState();
          });
        }

        for (const metric of metrics) {
          metric.addEventListener("click", () => {
            statusFilter.value = metric.dataset.statusCard;
            applyFilters();
          });
        }

        clearSelection.addEventListener("click", () => {
          for (const row of rows) {
            row.classList.remove("selected");
          }
          copyStatus.textContent = "";
          updateSelectionState();
        });

        copySelection.addEventListener("click", async () => {
          const selected = selectedRows();
          if (selected.length === 0) {
            return;
          }
          const lines = [
            copyColumns.map(column => column[0]).join("\\t"),
            ...selected.map(row => copyColumns
              .map(column => tsvValue(row.dataset[column[1]]))
              .join("\\t"))
          ];
          try {
            await writeClipboard(lines.join("\\n"));
            copyStatus.textContent = `Copied ${selected.length} row${selected.length === 1 ? "" : "s"}`;
          } catch (error) {
            copyStatus.textContent = "Copy failed";
          }
        });

        function tsvValue(value) {
          return (value || "").replace(/[\\t\\r\\n]+/g, " ").trim();
        }

        async function writeClipboard(text) {
          if (navigator.clipboard && window.isSecureContext) {
            await navigator.clipboard.writeText(text);
            return;
          }
          const textarea = document.createElement("textarea");
          textarea.value = text;
          textarea.setAttribute("readonly", "");
          textarea.style.position = "fixed";
          textarea.style.left = "-9999px";
          document.body.appendChild(textarea);
          textarea.select();
          const copied = document.execCommand("copy");
          textarea.remove();
          if (!copied) {
            throw new Error("Unable to copy selected rows");
          }
        }

        clearFilters.addEventListener("click", () => {
          search.value = "";
          statusFilter.value = "";
          clearCheckedValues(tableFilter);
          clearCheckedValues(objectFilter);
          applyFilters();
        });

        function clearCheckedValues(container) {
          for (const input of container.querySelectorAll("input[type='checkbox']")) {
            input.checked = false;
          }
        }

        for (const button of document.querySelectorAll("[data-clear-filter]")) {
          button.addEventListener("click", () => {
            clearCheckedValues(document.getElementById(button.dataset.clearFilter));
            applyFilters();
          });
        }

        search.addEventListener("input", applyFilters);
        statusFilter.addEventListener("change", applyFilters);
        tableFilter.addEventListener("change", applyFilters);
        objectFilter.addEventListener("change", applyFilters);
        applyFilters();
        """;
}
