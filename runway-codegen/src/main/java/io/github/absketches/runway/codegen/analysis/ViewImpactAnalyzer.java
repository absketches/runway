package io.github.absketches.runway.codegen.analysis;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ViewImpactAnalyzer {
    private static final Pattern CREATE_VIEW = SqlRegexPatterns.pattern(
        "^create\\s+(?:or\\s+replace\\s+)?view\\s+(" + SqlRegexPatterns.IDENTIFIER + ")\\s+as\\s+(.*)"
    );
    private static final Pattern DROP_VIEW = SqlRegexPatterns.pattern(
        "^drop\\s+view\\s+(?:if\\s+exists\\s+)?(" + SqlRegexPatterns.IDENTIFIER + ")"
    );

    private ViewImpactAnalyzer() {
    }

    static SqlImpact analyze(String statement) {
        Matcher matcher = CREATE_VIEW.matcher(statement);
        if (matcher.find()) {
            LinkedHashSet<String> readTables = new LinkedHashSet<>();
            LinkedHashSet<ColumnReference> readColumns = new LinkedHashSet<>();
            String query = matcher.group(2);
            SqlReadReferenceCollector.collectReads(query, Map.of(), readTables, readColumns);
            boolean complete = collectSingleTableSelectReads(query, readTables, readColumns);
            return SqlImpactFactory.create(
                SqlStatementType.CREATE_VIEW,
                SqlIdentifierParser.identifier(matcher.group(1)),
                List.copyOf(readTables),
                List.of(),
                List.copyOf(readColumns),
                List.of(),
                complete
            );
        }

        matcher = DROP_VIEW.matcher(statement);
        if (matcher.find()) {
            return SqlImpactFactory.create(
                SqlStatementType.DROP_VIEW,
                SqlIdentifierParser.identifier(matcher.group(1)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true
            );
        }

        return null;
    }

    private static boolean collectSingleTableSelectReads(
        String sql,
        Set<String> tables,
        Set<ColumnReference> columns
    ) {
        String query = SqlSyntaxScanner.collapseWhitespace(SqlSyntaxScanner.trimTrailingSemicolon(sql.strip()));
        if (!query.regionMatches(true, 0, "select", 0, "select".length())) {
            return false;
        }
        if (tables.size() != 1 || SqlSyntaxScanner.topLevelKeywordOffset(query, " join ") >= 0) {
            return false;
        }
        int from = SqlSyntaxScanner.topLevelKeywordOffset(query, " from ");
        if (from < 0) {
            return false;
        }

        String table = tables.iterator().next();
        boolean complete = collectSelectColumns(query.substring("select".length(), from), table, columns);
        complete &= collectClauseColumns(query, " group by ", table, columns);
        complete &= collectClauseColumns(query, " order by ", table, columns);
        return complete
            && SqlSyntaxScanner.topLevelKeywordOffset(query, " where ") < 0
            && SqlSyntaxScanner.topLevelKeywordOffset(query, " having ") < 0
            && SqlSyntaxScanner.topLevelKeywordOffset(query, " union ") < 0;
    }

    private static boolean collectSelectColumns(
        String selectList,
        String table,
        Set<ColumnReference> columns
    ) {
        boolean complete = true;
        for (String expression : SqlSyntaxScanner.splitTopLevel(selectList)) {
            String source = SqlSyntaxScanner.removeAlias(expression);
            String column = SqlSyntaxScanner.simpleColumn(source);
            if (column != null) {
                columns.add(new ColumnReference(table, column));
            } else if (!SqlRegexPatterns.COUNT_STAR.matcher(source).matches() && !SqlRegexPatterns.LITERAL.matcher(source).matches()) {
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
        int start = SqlSyntaxScanner.topLevelKeywordOffset(query, clause);
        if (start < 0) {
            return true;
        }
        int clauseStart = start + clause.length();
        int end = SqlSyntaxScanner.firstTopLevelKeywordOffset(
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
        for (String expression : SqlSyntaxScanner.splitTopLevel(value)) {
            String column = SqlSyntaxScanner.simpleColumn(SqlSyntaxScanner.removeOrdering(SqlSyntaxScanner.removeAlias(expression)));
            if (column == null) {
                complete = false;
            } else {
                columns.add(new ColumnReference(table, column));
            }
        }
        return complete;
    }
}
