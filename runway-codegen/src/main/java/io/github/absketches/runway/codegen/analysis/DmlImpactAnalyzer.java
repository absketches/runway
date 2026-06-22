package io.github.absketches.runway.codegen.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DmlImpactAnalyzer {
    private static final Pattern INSERT = SqlRegexPatterns.pattern(
        "^(?:insert(?:\\s+(?:or\\s+\\w+|ignore))?|replace)\\s+into\\s+"
            + "(" + SqlRegexPatterns.IDENTIFIER + ")\\s*(?:\\(([^)]*)\\))?"
    );
    private static final Pattern UPDATE = SqlRegexPatterns.pattern(
        "^update\\s+(" + SqlRegexPatterns.IDENTIFIER + ")(?:\\s+(?:as\\s+)?([A-Za-z_][A-Za-z0-9_$]*))?\\s+set\\s+(.*)"
    );
    private static final Pattern DELETE = SqlRegexPatterns.pattern(
        "^delete\\s+from\\s+(" + SqlRegexPatterns.IDENTIFIER + ")(.*)"
    );

    private DmlImpactAnalyzer() {
    }

    static SqlImpact analyze(String statement) {
        Matcher matcher = INSERT.matcher(statement);
        if (matcher.find()) {
            String table = SqlIdentifierParser.identifier(matcher.group(1));
            List<String> columns = matcher.group(2) == null ? List.of() : SqlIdentifierParser.identifiers(matcher.group(2));
            LinkedHashSet<String> readTables = new LinkedHashSet<>();
            LinkedHashSet<ColumnReference> readColumns = new LinkedHashSet<>();
            SqlReadReferenceCollector.collectReads(statement, Map.of(), readTables, readColumns);
            return SqlImpactFactory.create(
                SqlStatementType.INSERT,
                "",
                List.copyOf(readTables),
                List.of(table),
                List.copyOf(readColumns),
                SqlImpactFactory.columnReferences(table, columns),
                List.copyOf(readTables),
                List.of(table),
                List.copyOf(readColumns),
                SqlImpactFactory.columnReferences(table, columns),
                matcher.group(2) != null && readTables.isEmpty()
            );
        }

        matcher = UPDATE.matcher(statement);
        if (matcher.find()) {
            String table = SqlIdentifierParser.identifier(matcher.group(1));
            List<ColumnReference> writtenColumns = SqlImpactFactory.columnReferences(table, updatedColumns(matcher.group(3)));
            String alias = matcher.group(2);
            Map<String, String> aliases = new LinkedHashMap<>();
            aliases.put(SqlIdentifierParser.tableName(table), table);
            if (alias != null && !SqlReadReferenceCollector.isTableAliasStopWord(alias.toLowerCase(Locale.ROOT))) {
                aliases.put(alias, table);
            }
            LinkedHashSet<String> readTables = new LinkedHashSet<>();
            LinkedHashSet<ColumnReference> readColumns = new LinkedHashSet<>();
            SqlReadReferenceCollector.collectReads(statement, aliases, readTables, readColumns);
            return SqlImpactFactory.create(
                SqlStatementType.UPDATE,
                "",
                List.copyOf(readTables),
                List.of(table),
                List.copyOf(readColumns),
                writtenColumns,
                List.copyOf(readTables),
                List.of(table),
                List.copyOf(readColumns),
                writtenColumns,
                false
            );
        }

        matcher = DELETE.matcher(statement);
        if (matcher.find()) {
            String table = SqlIdentifierParser.identifier(matcher.group(1));
            LinkedHashSet<String> readTables = new LinkedHashSet<>();
            LinkedHashSet<ColumnReference> readColumns = new LinkedHashSet<>();
            SqlReadReferenceCollector.collectReads(
                statement,
                Map.of(SqlIdentifierParser.tableName(table), table),
                readTables,
                readColumns
            );
            return SqlImpactFactory.create(
                SqlStatementType.DELETE,
                "",
                List.copyOf(readTables),
                List.of(table),
                List.copyOf(readColumns),
                List.of(),
                List.copyOf(readTables),
                List.of(table),
                List.copyOf(readColumns),
                List.of(),
                false
            );
        }

        return null;
    }

    private static List<String> updatedColumns(String setAndRest) {
        int end = SqlSyntaxScanner.firstKeywordOffset(setAndRest, " where ", " from ", " returning ", " order ", " limit ");
        String assignments = end < 0 ? setAndRest : setAndRest.substring(0, end);
        List<String> columns = new ArrayList<>();
        for (String assignment : SqlSyntaxScanner.splitTopLevel(assignments)) {
            int equals = assignment.indexOf('=');
            if (equals > 0) {
                columns.add(SqlIdentifierParser.identifier(assignment.substring(0, equals).strip()));
            }
        }
        return List.copyOf(columns);
    }
}
