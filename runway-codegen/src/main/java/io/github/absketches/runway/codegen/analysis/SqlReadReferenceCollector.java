package io.github.absketches.runway.codegen.analysis;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SqlReadReferenceCollector {
    private static final Pattern TABLE_REFERENCE = SqlRegexPatterns.pattern(
        "\\b(from|join)\\s+(" + SqlRegexPatterns.IDENTIFIER + ")(?:\\s+(?:as\\s+)?([A-Za-z_][A-Za-z0-9_$]*))?"
    );
    private static final Pattern QUALIFIED_COLUMN = SqlRegexPatterns.pattern(
        "(" + SqlRegexPatterns.IDENTIFIER_PART + ")\\.(" + SqlRegexPatterns.IDENTIFIER_PART + ")"
    );
    private static final Set<String> TABLE_ALIAS_STOP_WORDS = Set.of(
        "where", "join", "left", "right", "inner", "outer", "full", "cross", "on", "group", "order", "limit",
        "set", "values", "returning", "union", "having"
    );

    private SqlReadReferenceCollector() {
    }

    static boolean isTableAliasStopWord(String value) {
        return TABLE_ALIAS_STOP_WORDS.contains(value.toLowerCase(Locale.ROOT));
    }

    static void collectReads(
        String sql,
        Map<String, String> knownAliases,
        Set<String> tables,
        Set<ColumnReference> columns
    ) {
        Map<String, String> aliases = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : knownAliases.entrySet()) {
            aliases.put(SqlIdentifierParser.aliasKey(entry.getKey()), entry.getValue());
        }
        tables.addAll(knownAliases.values());
        Matcher tableMatcher = TABLE_REFERENCE.matcher(sql);
        while (tableMatcher.find()) {
            String table = SqlIdentifierParser.identifier(tableMatcher.group(2));
            tables.add(table);
            aliases.put(SqlIdentifierParser.aliasKey(table), table);
            String alias = tableMatcher.group(3);
            if (alias != null && !isTableAliasStopWord(alias)) {
                aliases.put(SqlIdentifierParser.aliasKey(alias), table);
            }
        }

        Matcher columnMatcher = QUALIFIED_COLUMN.matcher(sql);
        while (columnMatcher.find()) {
            String table = aliases.get(SqlIdentifierParser.aliasKey(columnMatcher.group(1)));
            if (table != null) {
                columns.add(new ColumnReference(table, SqlIdentifierParser.identifier(columnMatcher.group(2))));
            }
        }
    }
}
