package io.github.absketches.runway.codegen.analysis;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RoutineImpactAnalyzer {
    private static final Pattern CREATE_ROUTINE = SqlRegexPatterns.pattern(
        "^create\\s+(?:or\\s+replace\\s+)?(?:definer\\s*=\\s*\\S+\\s+)?"
            + "(function|procedure)\\s+(" + SqlRegexPatterns.IDENTIFIER + ")"
    );
    private static final Pattern ALTER_ROUTINE = SqlRegexPatterns.pattern(
        "^alter\\s+(function|procedure)\\s+(" + SqlRegexPatterns.IDENTIFIER + ")"
    );
    private static final Pattern DROP_ROUTINE = SqlRegexPatterns.pattern(
        "^drop\\s+(function|procedure)\\s+(?:if\\s+exists\\s+)?(" + SqlRegexPatterns.IDENTIFIER + ")"
    );
    private static final Pattern REPLACE_ROUTINE = SqlRegexPatterns.pattern(
        "^replace\\s+(function|procedure)\\s+(" + SqlRegexPatterns.IDENTIFIER + ")"
    );

    private RoutineImpactAnalyzer() {
    }

    static SqlImpact analyze(String statement) {
        Matcher matcher = CREATE_ROUTINE.matcher(statement);
        if (matcher.find()) {
            return incompleteRoutine(createType(matcher.group(1)), matcher.group(2));
        }

        matcher = ALTER_ROUTINE.matcher(statement);
        if (matcher.find()) {
            return incompleteRoutine(alterType(matcher.group(1)), matcher.group(2));
        }

        matcher = DROP_ROUTINE.matcher(statement);
        if (matcher.find()) {
            return incompleteRoutine(dropType(matcher.group(1)), matcher.group(2));
        }

        matcher = REPLACE_ROUTINE.matcher(statement);
        if (matcher.find()) {
            return incompleteRoutine(createType(matcher.group(1)), matcher.group(2));
        }

        return null;
    }

    private static SqlImpact incompleteRoutine(SqlStatementType type, String name) {
        return SqlImpactFactory.create(
            type,
            SqlIdentifierParser.identifier(name),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            false
        );
    }

    private static SqlStatementType createType(String objectType) {
        return "function".equalsIgnoreCase(objectType)
            ? SqlStatementType.CREATE_FUNCTION
            : SqlStatementType.CREATE_PROCEDURE;
    }

    private static SqlStatementType alterType(String objectType) {
        return "function".equalsIgnoreCase(objectType)
            ? SqlStatementType.ALTER_FUNCTION
            : SqlStatementType.ALTER_PROCEDURE;
    }

    private static SqlStatementType dropType(String objectType) {
        return "function".equalsIgnoreCase(objectType)
            ? SqlStatementType.DROP_FUNCTION
            : SqlStatementType.DROP_PROCEDURE;
    }
}
