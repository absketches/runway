package io.github.absketches.runway.codegen.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SqlImpactAnalyzer {
    private static final String IDENTIFIER = "(?:[`\"\\[]?[A-Za-z_][A-Za-z0-9_$]*[`\"\\]]?)(?:\\.(?:[`\"\\[]?[A-Za-z_][A-Za-z0-9_$]*[`\"\\]]?))?";
    private static final Pattern CREATE_TABLE = pattern("^create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?(" + IDENTIFIER + ")\\s*\\((.*)\\)");
    private static final Pattern ALTER_TABLE = pattern("^alter\\s+table\\s+(" + IDENTIFIER + ")\\s+(.*)");
    private static final Pattern DROP_TABLE = pattern("^drop\\s+table\\s+(?:if\\s+exists\\s+)?(" + IDENTIFIER + ")");
    private static final Pattern CREATE_INDEX = pattern("^create\\s+(unique\\s+)?index\\s+(?:if\\s+not\\s+exists\\s+)?(" + IDENTIFIER + ")\\s+on\\s+(" + IDENTIFIER + ")\\s*\\((.*)\\)");
    private static final Pattern DROP_INDEX = pattern("^drop\\s+index\\s+(?:if\\s+exists\\s+)?(" + IDENTIFIER + ")");
    private static final Pattern CREATE_VIEW = pattern("^create\\s+(?:or\\s+replace\\s+)?view\\s+(" + IDENTIFIER + ")\\s+as\\s+(.*)");
    private static final Pattern DROP_VIEW = pattern("^drop\\s+view\\s+(?:if\\s+exists\\s+)?(" + IDENTIFIER + ")");
    private static final Pattern INSERT = pattern("^insert\\s+into\\s+(" + IDENTIFIER + ")\\s*(?:\\(([^)]*)\\))?");
    private static final Pattern UPDATE = pattern("^update\\s+(" + IDENTIFIER + ")(?:\\s+(?:as\\s+)?([A-Za-z_][A-Za-z0-9_$]*))?\\s+set\\s+(.*)");
    private static final Pattern DELETE = pattern("^delete\\s+from\\s+(" + IDENTIFIER + ")(.*)");
    private static final Pattern TABLE_REFERENCE = pattern("\\b(from|join)\\s+(" + IDENTIFIER + ")(?:\\s+(?:as\\s+)?([A-Za-z_][A-Za-z0-9_$]*))?");
    private static final Pattern QUALIFIED_COLUMN = Pattern.compile(
        "([A-Za-z_][A-Za-z0-9_$]*)\\.([A-Za-z_][A-Za-z0-9_$]*)"
    );
    private static final Pattern SIMPLE_COLUMN = pattern("^" + IDENTIFIER + "$");
    private static final Pattern COUNT_STAR = pattern("^count\\s*\\(\\s*\\*\\s*\\)$");
    private static final Pattern LITERAL = pattern("^(?:null|true|false|[-+]?\\d+(?:\\.\\d+)?|'(?:''|[^'])*')$");
    private static final Set<String> TABLE_ALIAS_STOP_WORDS = Set.of(
        "where", "join", "left", "right", "inner", "outer", "full", "cross", "on", "group", "order", "limit",
        "set", "values", "returning", "union", "having"
    );
    private static final Set<String> TABLE_CONSTRAINTS = Set.of(
        "constraint", "primary", "foreign", "unique", "check", "exclude"
    );

    private SqlImpactAnalyzer() {
    }

    public static SqlImpact analyze(String sql) {
        String statement = stripLeadingComments(sql).strip();
        if (statement.isEmpty()) {
            return unknown();
        }

        Matcher matcher = CREATE_TABLE.matcher(statement);
        if (matcher.find()) {
            String table = identifier(matcher.group(1));
            return impact(
                SqlStatementType.CREATE_TABLE,
                "",
                List.of(),
                List.of(table),
                List.of(),
                qualify(table, tableColumns(matcher.group(2))),
                true
            );
        }

        matcher = ALTER_TABLE.matcher(statement);
        if (matcher.find()) {
            String table = identifier(matcher.group(1));
            List<String> columns = alteredColumns(matcher.group(2));
            return impact(
                SqlStatementType.ALTER_TABLE,
                "",
                List.of(),
                List.of(table),
                List.of(),
                qualify(table, columns),
                !columns.isEmpty()
            );
        }

        matcher = DROP_TABLE.matcher(statement);
        if (matcher.find()) {
            String table = identifier(matcher.group(1));
            return impact(SqlStatementType.DROP_TABLE, "", List.of(), List.of(table), List.of(), List.of(), true);
        }

        matcher = CREATE_INDEX.matcher(statement);
        if (matcher.find()) {
            String index = identifier(matcher.group(2));
            String table = identifier(matcher.group(3));
            return impact(
                SqlStatementType.CREATE_INDEX,
                index,
                List.of(table),
                List.of(),
                qualify(table, identifiers(matcher.group(4))),
                List.of(),
                true
            );
        }

        matcher = DROP_INDEX.matcher(statement);
        if (matcher.find()) {
            return impact(
                SqlStatementType.DROP_INDEX,
                identifier(matcher.group(1)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false
            );
        }

        matcher = CREATE_VIEW.matcher(statement);
        if (matcher.find()) {
            LinkedHashSet<String> readTables = new LinkedHashSet<>();
            LinkedHashSet<ColumnReference> readColumns = new LinkedHashSet<>();
            String query = matcher.group(2);
            collectReads(query, Map.of(), readTables, readColumns);
            boolean complete = collectSingleTableSelectReads(query, readTables, readColumns);
            return impact(
                SqlStatementType.CREATE_VIEW,
                identifier(matcher.group(1)),
                List.copyOf(readTables),
                List.of(),
                List.copyOf(readColumns),
                List.of(),
                complete
            );
        }

        matcher = DROP_VIEW.matcher(statement);
        if (matcher.find()) {
            return impact(
                SqlStatementType.DROP_VIEW,
                identifier(matcher.group(1)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true
            );
        }

        matcher = INSERT.matcher(statement);
        if (matcher.find()) {
            String table = identifier(matcher.group(1));
            List<String> columns = matcher.group(2) == null ? List.of() : identifiers(matcher.group(2));
            LinkedHashSet<String> readTables = new LinkedHashSet<>();
            LinkedHashSet<ColumnReference> readColumns = new LinkedHashSet<>();
            collectReads(statement, Map.of(), readTables, readColumns);
            return impact(
                SqlStatementType.INSERT,
                "",
                List.copyOf(readTables),
                List.of(table),
                List.copyOf(readColumns),
                qualify(table, columns),
                matcher.group(2) != null && readTables.isEmpty()
            );
        }

        matcher = UPDATE.matcher(statement);
        if (matcher.find()) {
            String table = identifier(matcher.group(1));
            List<ColumnReference> writtenColumns = qualify(table, updatedColumns(matcher.group(3)));
            String alias = matcher.group(2);
            Map<String, String> aliases = new LinkedHashMap<>();
            aliases.put(tableName(table), table);
            if (alias != null && !TABLE_ALIAS_STOP_WORDS.contains(alias.toLowerCase(Locale.ROOT))) {
                aliases.put(alias, table);
            }
            LinkedHashSet<String> readTables = new LinkedHashSet<>();
            LinkedHashSet<ColumnReference> readColumns = new LinkedHashSet<>();
            collectReads(statement, aliases, readTables, readColumns);
            return impact(
                SqlStatementType.UPDATE,
                "",
                List.copyOf(readTables),
                List.of(table),
                List.copyOf(readColumns),
                writtenColumns,
                false
            );
        }

        matcher = DELETE.matcher(statement);
        if (matcher.find()) {
            String table = identifier(matcher.group(1));
            LinkedHashSet<String> readTables = new LinkedHashSet<>();
            LinkedHashSet<ColumnReference> readColumns = new LinkedHashSet<>();
            collectReads(
                statement,
                Map.of(tableName(table), table),
                readTables,
                readColumns
            );
            return impact(
                SqlStatementType.DELETE,
                "",
                List.copyOf(readTables),
                List.of(table),
                List.copyOf(readColumns),
                List.of(),
                false
            );
        }

        return unknown();
    }

    private static void collectReads(
        String sql,
        Map<String, String> knownAliases,
        Set<String> tables,
        Set<ColumnReference> columns
    ) {
        Map<String, String> aliases = new LinkedHashMap<>(knownAliases);
        tables.addAll(knownAliases.values());
        Matcher tableMatcher = TABLE_REFERENCE.matcher(sql);
        while (tableMatcher.find()) {
            String table = identifier(tableMatcher.group(2));
            tables.add(table);
            aliases.put(tableName(table), table);
            String alias = tableMatcher.group(3);
            if (alias != null && !TABLE_ALIAS_STOP_WORDS.contains(alias.toLowerCase(Locale.ROOT))) {
                aliases.put(alias, table);
            }
        }

        Matcher columnMatcher = QUALIFIED_COLUMN.matcher(sql);
        while (columnMatcher.find()) {
            String table = aliases.get(columnMatcher.group(1));
            if (table != null) {
                columns.add(new ColumnReference(table, identifier(columnMatcher.group(2))));
            }
        }
    }

    private static boolean collectSingleTableSelectReads(
        String sql,
        Set<String> tables,
        Set<ColumnReference> columns
    ) {
        String query = collapseWhitespace(trimTrailingSemicolon(sql.strip()));
        if (!query.regionMatches(true, 0, "select", 0, "select".length())) {
            return false;
        }
        if (tables.size() != 1 || topLevelKeywordOffset(query, " join ") >= 0) {
            return false;
        }
        int from = topLevelKeywordOffset(query, " from ");
        if (from < 0) {
            return false;
        }

        String table = tables.iterator().next();
        boolean complete = collectSelectColumns(query.substring("select".length(), from), table, columns);
        complete &= collectClauseColumns(query, " group by ", table, columns);
        complete &= collectClauseColumns(query, " order by ", table, columns);
        return complete
            && topLevelKeywordOffset(query, " where ") < 0
            && topLevelKeywordOffset(query, " having ") < 0
            && topLevelKeywordOffset(query, " union ") < 0;
    }

    private static boolean collectSelectColumns(
        String selectList,
        String table,
        Set<ColumnReference> columns
    ) {
        boolean complete = true;
        for (String expression : splitTopLevel(selectList)) {
            String source = removeAlias(expression);
            String column = simpleColumn(source);
            if (column != null) {
                columns.add(new ColumnReference(table, column));
            } else if (!COUNT_STAR.matcher(source).matches() && !LITERAL.matcher(source).matches()) {
                complete = false;
            }
        }
        return complete;
    }

    private static boolean collectClauseColumns(
        String query,
        String clause,
        String table,
        Set<ColumnReference> columns
    ) {
        int start = topLevelKeywordOffset(query, clause);
        if (start < 0) {
            return true;
        }
        int clauseStart = start + clause.length();
        int end = firstTopLevelKeywordOffset(
            query,
            clauseStart,
            " group by ",
            " having ",
            " order by ",
            " limit ",
            " offset ",
            " union "
        );
        String value = query.substring(clauseStart, end < 0 ? query.length() : end);
        boolean complete = true;
        for (String expression : splitTopLevel(value)) {
            String column = simpleColumn(removeOrdering(removeAlias(expression)));
            if (column == null) {
                complete = false;
            } else {
                columns.add(new ColumnReference(table, column));
            }
        }
        return complete;
    }

    private static List<String> tableColumns(String body) {
        List<String> columns = new ArrayList<>();
        for (String part : splitTopLevel(body)) {
            String trimmed = part.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            String first = firstToken(trimmed);
            if (!TABLE_CONSTRAINTS.contains(first.toLowerCase(Locale.ROOT))) {
                columns.add(identifier(first));
            }
        }
        return List.copyOf(columns);
    }

    private static List<String> alteredColumns(String operation) {
        Matcher matcher = pattern("^(?:add|drop|alter|rename)\\s+(?:column\\s+)?(" + IDENTIFIER + ")").matcher(operation.strip());
        return matcher.find() ? List.of(identifier(matcher.group(1))) : List.of();
    }

    private static List<String> updatedColumns(String setAndRest) {
        int end = firstKeywordOffset(setAndRest, " where ", " from ", " returning ", " order ", " limit ");
        String assignments = end < 0 ? setAndRest : setAndRest.substring(0, end);
        List<String> columns = new ArrayList<>();
        for (String assignment : splitTopLevel(assignments)) {
            int equals = assignment.indexOf('=');
            if (equals > 0) {
                columns.add(identifier(assignment.substring(0, equals).strip()));
            }
        }
        return List.copyOf(columns);
    }

    private static List<String> identifiers(String value) {
        List<String> identifiers = new ArrayList<>();
        for (String part : splitTopLevel(value)) {
            String candidate = part.strip();
            int whitespace = candidate.indexOf(' ');
            if (whitespace > 0) {
                candidate = candidate.substring(0, whitespace);
            }
            if (!candidate.isEmpty()) {
                identifiers.add(identifier(candidate));
            }
        }
        return List.copyOf(identifiers);
    }

    private static List<String> splitTopLevel(String value) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        char quote = '\0';
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (quote != '\0') {
                if (current == quote) {
                    quote = '\0';
                }
                continue;
            }
            if (current == '\'' || current == '"' || current == '`') {
                quote = current;
            } else if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
            } else if (current == ',' && depth == 0) {
                parts.add(value.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(value.substring(start));
        return parts;
    }

    private static String removeAlias(String expression) {
        String value = expression.strip();
        int alias = topLevelKeywordOffset(value, " as ");
        return alias < 0 ? value : value.substring(0, alias).strip();
    }

    private static String removeOrdering(String expression) {
        String value = expression.strip();
        int direction = firstTopLevelKeywordOffset(value, 0, " asc", " desc", " nulls first", " nulls last");
        return direction < 0 ? value : value.substring(0, direction).strip();
    }

    private static String simpleColumn(String expression) {
        String value = expression.strip();
        if (value.contains("*") || !SIMPLE_COLUMN.matcher(value).matches()) {
            return null;
        }
        return tableName(identifier(value));
    }

    private static String stripLeadingComments(String sql) {
        String value = sql;
        while (true) {
            String stripped = value.stripLeading();
            if (stripped.startsWith("--")) {
                int newline = stripped.indexOf('\n');
                value = newline < 0 ? "" : stripped.substring(newline + 1);
            } else if (stripped.startsWith("/*")) {
                int end = stripped.indexOf("*/", 2);
                value = end < 0 ? stripped : stripped.substring(end + 2);
            } else {
                return stripped;
            }
        }
    }

    private static int firstKeywordOffset(String value, String... keywords) {
        String lower = value.toLowerCase(Locale.ROOT);
        int result = -1;
        for (String keyword : keywords) {
            int offset = lower.indexOf(keyword);
            if (offset >= 0 && (result < 0 || offset < result)) {
                result = offset;
            }
        }
        return result;
    }

    private static int topLevelKeywordOffset(String value, String keyword) {
        return firstTopLevelKeywordOffset(value, 0, keyword);
    }

    private static int firstTopLevelKeywordOffset(String value, int start, String... keywords) {
        int depth = 0;
        char quote = '\0';
        String lower = value.toLowerCase(Locale.ROOT);
        for (int i = start; i < value.length(); i++) {
            char current = value.charAt(i);
            if (quote != '\0') {
                if (current == quote) {
                    quote = '\0';
                }
                continue;
            }
            if (current == '\'' || current == '"' || current == '`') {
                quote = current;
            } else if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
            } else if (depth == 0) {
                for (String keyword : keywords) {
                    if (lower.startsWith(keyword, i)) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private static String firstToken(String value) {
        int whitespace = 0;
        while (whitespace < value.length() && !Character.isWhitespace(value.charAt(whitespace))) {
            whitespace++;
        }
        return value.substring(0, whitespace);
    }

    private static String identifier(String value) {
        String cleaned = value.strip();
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\""))
            || (cleaned.startsWith("`") && cleaned.endsWith("`"))
            || (cleaned.startsWith("[") && cleaned.endsWith("]"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }

    private static String tableName(String qualifiedName) {
        int dot = qualifiedName.lastIndexOf('.');
        return dot < 0 ? qualifiedName : qualifiedName.substring(dot + 1);
    }

    private static List<ColumnReference> qualify(String table, List<String> columns) {
        return columns.stream().map(column -> new ColumnReference(table, column)).toList();
    }

    private static String trimTrailingSemicolon(String value) {
        return value.endsWith(";") ? value.substring(0, value.length() - 1).stripTrailing() : value;
    }

    private static String collapseWhitespace(String value) {
        StringBuilder builder = new StringBuilder();
        boolean previousWhitespace = false;
        char quote = '\0';
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (quote != '\0') {
                builder.append(current);
                if (current == quote) {
                    quote = '\0';
                }
            } else if (current == '\'' || current == '"' || current == '`') {
                builder.append(current);
                quote = current;
                previousWhitespace = false;
            } else if (Character.isWhitespace(current)) {
                if (!previousWhitespace) {
                    builder.append(' ');
                    previousWhitespace = true;
                }
            } else {
                builder.append(current);
                previousWhitespace = false;
            }
        }
        return builder.toString().strip();
    }

    private static Pattern pattern(String expression) {
        return Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }

    private static SqlImpact impact(
        SqlStatementType type,
        String schemaObject,
        List<String> readTables,
        List<String> writtenTables,
        List<ColumnReference> readColumns,
        List<ColumnReference> writtenColumns,
        boolean complete
    ) {
        return new SqlImpact(
            type,
            schemaObject,
            readTables,
            writtenTables,
            readColumns,
            writtenColumns,
            complete
        );
    }

    private static SqlImpact unknown() {
        return impact(
            SqlStatementType.UNKNOWN,
            "",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            false
        );
    }

}
