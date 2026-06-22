package io.github.absketches.runway.codegen.analysis;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TriggerImpactAnalyzer {
    private static final Pattern CREATE_TRIGGER = SqlRegexPatterns.pattern(
        "^create\\s+(?:or\\s+replace\\s+)?(?:definer\\s*=\\s*\\S+\\s+)?(?:temp(?:orary)?\\s+)?(?:constraint\\s+)?trigger\\s+"
            + "(?:if\\s+not\\s+exists\\s+)?(" + SqlRegexPatterns.IDENTIFIER + ")\\s+(.*?\\bon\\s+"
            + "(" + SqlRegexPatterns.IDENTIFIER + ")(?:\\s|$).*)"
    );
    private static final Pattern DROP_TRIGGER = SqlRegexPatterns.pattern(
        "^drop\\s+trigger\\s+(?:if\\s+exists\\s+)?(" + SqlRegexPatterns.IDENTIFIER + ")(?:\\s+on\\s+"
            + "(" + SqlRegexPatterns.IDENTIFIER + "))?"
    );
    private static final Pattern TRIGGER_UPDATE_OF = SqlRegexPatterns.pattern(
        "\\bupdate\\s+of\\s+(.+?)\\s+on\\s+" + SqlRegexPatterns.IDENTIFIER
    );
    private static final Pattern ROUTINE_TRIGGER_ACTION = SqlRegexPatterns.pattern(
        "\\bexecute\\s+(?:function|procedure)\\b"
    );

    private TriggerImpactAnalyzer() {
    }

    static SqlImpact analyze(String statement) {
        Matcher matcher = CREATE_TRIGGER.matcher(statement);
        if (matcher.find()) {
            String table = SqlIdentifierParser.identifier(matcher.group(3));
            TriggerDependencies dependencies = triggerDependencies(statement, table);
            return SqlImpactFactory.create(
                SqlStatementType.CREATE_TRIGGER,
                SqlIdentifierParser.identifier(matcher.group(1)),
                dependencies.readTables(),
                List.of(),
                dependencies.readColumns(),
                List.of(),
                dependencies.runtimeReadTables(),
                dependencies.runtimeWriteTables(),
                dependencies.runtimeReadColumns(),
                dependencies.runtimeWriteColumns(),
                dependencies.complete()
            );
        }

        matcher = DROP_TRIGGER.matcher(statement);
        if (matcher.find()) {
            return SqlImpactFactory.create(
                SqlStatementType.DROP_TRIGGER,
                SqlIdentifierParser.identifier(matcher.group(1)),
                matcher.group(2) == null ? List.of() : List.of(SqlIdentifierParser.identifier(matcher.group(2))),
                List.of(),
                List.of(),
                List.of(),
                true
            );
        }

        return null;
    }

    private static TriggerDependencies triggerDependencies(String statement, String table) {
        LinkedHashSet<String> readTables = new LinkedHashSet<>();
        LinkedHashSet<ColumnReference> readColumns = new LinkedHashSet<>();
        LinkedHashSet<String> runtimeReadTables = new LinkedHashSet<>();
        LinkedHashSet<String> runtimeWriteTables = new LinkedHashSet<>();
        LinkedHashSet<ColumnReference> runtimeReadColumns = new LinkedHashSet<>();
        LinkedHashSet<ColumnReference> runtimeWriteColumns = new LinkedHashSet<>();
        readTables.add(table);
        SqlReadReferenceCollector.collectReads(
            statement,
            Map.of(
                "new", table,
                "old", table,
                SqlIdentifierParser.tableName(table), table
            ),
            readTables,
            readColumns
        );
        SqlReadReferenceCollector.collectReads(
            statement,
            Map.of(
                "new", table,
                "old", table,
                SqlIdentifierParser.tableName(table), table
            ),
            runtimeReadTables,
            runtimeReadColumns
        );
        collectTriggerEventColumns(statement, table, readColumns);
        collectTriggerEventColumns(statement, table, runtimeReadColumns);

        boolean complete = !ROUTINE_TRIGGER_ACTION.matcher(statement).find();
        for (String nestedStatement : triggerBodyStatements(statement)) {
            SqlImpact nested = SqlImpactAnalyzer.analyze(nestedStatement);
            if (nested.type() == SqlStatementType.UNKNOWN) {
                complete = false;
                continue;
            }
            if (!nested.analysisComplete()) {
                complete = false;
            }
            readTables.addAll(nested.readTables());
            readTables.addAll(nested.writtenTables());
            readColumns.addAll(nested.readColumns());
            readColumns.addAll(nested.writtenColumns());
            runtimeReadTables.addAll(nested.runtimeReadTables());
            runtimeWriteTables.addAll(nested.runtimeWriteTables());
            runtimeReadColumns.addAll(nested.runtimeReadColumns());
            runtimeWriteColumns.addAll(nested.runtimeWriteColumns());
            if (nested.type().isDml() && nested.writtenColumns().isEmpty() && !nested.writtenTables().isEmpty()) {
                complete = false;
            }
        }

        return new TriggerDependencies(
            List.copyOf(readTables),
            List.copyOf(readColumns),
            List.copyOf(runtimeReadTables),
            List.copyOf(runtimeWriteTables),
            List.copyOf(runtimeReadColumns),
            List.copyOf(runtimeWriteColumns),
            complete
        );
    }

    private static void collectTriggerEventColumns(
        String statement,
        String table,
        Set<ColumnReference> columns
    ) {
        Matcher matcher = TRIGGER_UPDATE_OF.matcher(statement);
        if (!matcher.find()) {
            return;
        }
        for (String column : SqlIdentifierParser.identifiers(matcher.group(1))) {
            columns.add(new ColumnReference(table, column));
        }
    }

    private static List<String> triggerBodyStatements(String statement) {
        String body = triggerBody(statement);
        if (body.isBlank()) {
            return List.of();
        }
        List<String> statements = new ArrayList<>();
        for (String nestedStatement : SqlSyntaxScanner.splitStatements(body)) {
            String trimmed = nestedStatement.strip();
            if (!trimmed.isBlank()) {
                statements.add(trimmed);
            }
        }
        return List.copyOf(statements);
    }

    private static String triggerBody(String statement) {
        int begin = SqlSyntaxScanner.keywordOffset(statement, "begin", 0);
        if (begin < 0) {
            return "";
        }
        int end = SqlSyntaxScanner.lastKeywordOffset(statement, "end");
        if (end <= begin) {
            return "";
        }
        return statement.substring(begin + "begin".length(), end);
    }

    private record TriggerDependencies(
        List<String> readTables,
        List<ColumnReference> readColumns,
        List<String> runtimeReadTables,
        List<String> runtimeWriteTables,
        List<ColumnReference> runtimeReadColumns,
        List<ColumnReference> runtimeWriteColumns,
        boolean complete
    ) {
    }
}
