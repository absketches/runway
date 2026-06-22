package io.github.absketches.runway.codegen.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DdlImpactAnalyzer {
    private static final Pattern CREATE_TABLE = SqlRegexPatterns.pattern(
        "^create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?(" + SqlRegexPatterns.IDENTIFIER + ")\\s*\\((.*)\\)"
    );
    private static final Pattern ALTER_TABLE = SqlRegexPatterns.pattern(
        "^alter\\s+table\\s+(" + SqlRegexPatterns.IDENTIFIER + ")\\s+(.*)"
    );
    private static final Pattern DROP_TABLE = SqlRegexPatterns.pattern(
        "^drop\\s+table\\s+(?:if\\s+exists\\s+)?(" + SqlRegexPatterns.IDENTIFIER + ")"
    );
    private static final Pattern CREATE_INDEX = SqlRegexPatterns.pattern(
        "^create\\s+(unique\\s+)?index\\s+(?:if\\s+not\\s+exists\\s+)?(" + SqlRegexPatterns.IDENTIFIER + ")\\s+on\\s+"
            + "(" + SqlRegexPatterns.IDENTIFIER + ")\\s*\\((.*)\\)\\s*(.*)$"
    );
    private static final Pattern DROP_INDEX = SqlRegexPatterns.pattern(
        "^drop\\s+index\\s+(?:if\\s+exists\\s+)?(" + SqlRegexPatterns.IDENTIFIER + ")"
    );
    private static final Pattern ADD_DROP_ALTER_COLUMN = SqlRegexPatterns.pattern(
        "^(?:add|drop|alter|modify)\\s+(?:column\\s+)?(?:if\\s+(?:not\\s+)?exists\\s+)?("
            + SqlRegexPatterns.IDENTIFIER + ")\\b"
    );
    private static final Pattern RENAME_COLUMN = SqlRegexPatterns.pattern(
        "^rename\\s+column\\s+(" + SqlRegexPatterns.IDENTIFIER + ")\\s+to\\s+("
            + SqlRegexPatterns.IDENTIFIER + ")\\b"
    );
    private static final Pattern CHANGE_COLUMN = SqlRegexPatterns.pattern(
        "^change\\s+(?:column\\s+)?(" + SqlRegexPatterns.IDENTIFIER + ")\\s+("
            + SqlRegexPatterns.IDENTIFIER + ")\\b"
    );
    private static final Pattern RENAME_TABLE = SqlRegexPatterns.pattern("^rename\\s+to\\s+" + SqlRegexPatterns.IDENTIFIER + "\\b");
    private static final Pattern ALTER_TABLE_CONSTRAINT = SqlRegexPatterns.pattern(
        "^(?:add|drop)\\s+(?:constraint|primary|foreign|unique|check|exclude)\\b"
    );
    private static final Set<String> TABLE_CONSTRAINTS = Set.of(
        "constraint", "primary", "foreign", "unique", "check", "exclude"
    );

    private DdlImpactAnalyzer() {
    }

    static SqlImpact analyze(String statement) {
        Matcher matcher = CREATE_TABLE.matcher(statement);
        if (matcher.find()) {
            String table = SqlIdentifierParser.identifier(matcher.group(1));
            return SqlImpactFactory.create(
                SqlStatementType.CREATE_TABLE,
                "",
                List.of(),
                List.of(table),
                List.of(),
                SqlImpactFactory.columnReferences(table, tableColumns(matcher.group(2))),
                true
            );
        }

        matcher = ALTER_TABLE.matcher(statement);
        if (matcher.find()) {
            String table = SqlIdentifierParser.identifier(matcher.group(1));
            AlteredColumns columns = alteredColumns(matcher.group(2));
            return SqlImpactFactory.create(
                SqlStatementType.ALTER_TABLE,
                "",
                List.of(),
                List.of(table),
                List.of(),
                SqlImpactFactory.columnReferences(table, columns.names()),
                columns.complete()
            );
        }

        matcher = DROP_TABLE.matcher(statement);
        if (matcher.find()) {
            String table = SqlIdentifierParser.identifier(matcher.group(1));
            return SqlImpactFactory.create(SqlStatementType.DROP_TABLE, "", List.of(), List.of(table), List.of(), List.of(), true);
        }

        matcher = CREATE_INDEX.matcher(statement);
        if (matcher.find()) {
            String index = SqlIdentifierParser.identifier(matcher.group(2));
            String table = SqlIdentifierParser.identifier(matcher.group(3));
            IndexColumns columns = indexColumns(matcher.group(4), matcher.group(5));
            return SqlImpactFactory.create(
                SqlStatementType.CREATE_INDEX,
                index,
                List.of(table),
                List.of(),
                SqlImpactFactory.columnReferences(table, columns.names()),
                List.of(),
                columns.complete()
            );
        }

        matcher = DROP_INDEX.matcher(statement);
        if (matcher.find()) {
            return SqlImpactFactory.create(
                SqlStatementType.DROP_INDEX,
                SqlIdentifierParser.identifier(matcher.group(1)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false
            );
        }

        return null;
    }

    private static List<String> tableColumns(String body) {
        List<String> columns = new ArrayList<>();
        for (String part : SqlSyntaxScanner.splitTopLevel(body)) {
            String trimmed = part.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            String first = SqlIdentifierParser.firstToken(trimmed);
            if (!TABLE_CONSTRAINTS.contains(first.toLowerCase(Locale.ROOT))) {
                columns.add(SqlIdentifierParser.identifier(first));
            }
        }
        return List.copyOf(columns);
    }

    private static AlteredColumns alteredColumns(String operation) {
        List<String> columns = new ArrayList<>();
        boolean complete = true;
        for (String part : SqlSyntaxScanner.splitTopLevel(operation)) {
            AlterOperation result = alterOperation(part);
            columns.addAll(result.columns());
            if (!result.complete()) {
                complete = false;
            }
        }
        return new AlteredColumns(List.copyOf(columns), complete);
    }

    private static AlterOperation alterOperation(String operation) {
        String value = operation.strip();
        if (value.isBlank() || RENAME_TABLE.matcher(value).find()) {
            return new AlterOperation(List.of(), true);
        }
        if (ALTER_TABLE_CONSTRAINT.matcher(value).find()) {
            return new AlterOperation(List.of(), false);
        }

        Matcher matcher = RENAME_COLUMN.matcher(value);
        if (matcher.find()) {
            return new AlterOperation(
                List.of(
                    SqlIdentifierParser.identifier(matcher.group(1)),
                    SqlIdentifierParser.identifier(matcher.group(2))
                ),
                true
            );
        }

        matcher = CHANGE_COLUMN.matcher(value);
        if (matcher.find()) {
            return new AlterOperation(
                List.of(
                    SqlIdentifierParser.identifier(matcher.group(1)),
                    SqlIdentifierParser.identifier(matcher.group(2))
                ),
                true
            );
        }

        matcher = ADD_DROP_ALTER_COLUMN.matcher(value);
        if (matcher.find()) {
            return new AlterOperation(List.of(SqlIdentifierParser.identifier(matcher.group(1))), true);
        }

        return new AlterOperation(List.of(), false);
    }

    private static IndexColumns indexColumns(String columnList, String trailingSql) {
        List<String> columns = new ArrayList<>();
        boolean complete = trailingSql.isBlank();
        for (String part : SqlSyntaxScanner.splitTopLevel(columnList)) {
            String column = SqlSyntaxScanner.simpleColumn(SqlSyntaxScanner.removeOrdering(part));
            if (column == null) {
                complete = false;
            } else {
                columns.add(column);
            }
        }
        return new IndexColumns(List.copyOf(columns), complete);
    }

    private record AlterOperation(List<String> columns, boolean complete) {
    }

    private record AlteredColumns(List<String> names, boolean complete) {
    }

    private record IndexColumns(List<String> names, boolean complete) {
    }
}
