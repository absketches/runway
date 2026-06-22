package io.github.absketches.runway.codegen.sql.validation;

import io.github.absketches.runway.codegen.CodegenException;

import java.util.Set;

public final class RunwaySqlFeatureValidator {
    private static final Set<String> CREATE_OBJECTS = Set.of(
        "access",
        "cast",
        "collation",
        "database",
        "domain",
        "event",
        "extension",
        "index",
        "language",
        "materialized",
        "operator",
        "policy",
        "publication",
        "role",
        "rule",
        "schema",
        "sequence",
        "server",
        "statistics",
        "subscription",
        "synonym",
        "table",
        "tablespace",
        "trigger",
        "type",
        "user",
        "view"
    );
    private static final Set<String> CREATE_MODIFIERS = Set.of(
        "aggregate",
        "global",
        "local",
        "materialized",
        "or",
        "replace",
        "temp",
        "temporary",
        "unique",
        "unlogged"
    );
    private static final Set<String> DEFINER_OBJECTS = Set.of(
        "event",
        "function",
        "procedure",
        "trigger",
        "view"
    );
    private static final Set<String> ROUTINE_ACTIONS = Set.of(
        "alter",
        "drop",
        "replace",
        "update"
    );

    private RunwaySqlFeatureValidator() {
    }

    public static void validate(String sql, String scriptName) {
        String objectType = routineObjectType(sql);
        if ("procedure".equals(objectType) || "function".equals(objectType)) {
            throw new CodegenException(
                scriptName + ": Runway does not currently support " + objectType + " statements"
            );
        }
    }

    private static String routineObjectType(String sql) {
        SqlWordScanner scanner = new SqlWordScanner(sql);
        String firstWord = scanner.next();
        if ("create".equals(firstWord)) {
            return createdObjectType(scanner);
        }
        if (ROUTINE_ACTIONS.contains(firstWord)) {
            return actionObjectType(scanner);
        }
        return "";
    }

    private static String createdObjectType(SqlWordScanner scanner) {
        for (int count = 0; count < 32; count++) {
            String word = scanner.next();
            if (word.isEmpty()) {
                return "";
            }
            if ("definer".equals(word)) {
                return objectAfterDefiner(scanner);
            }
            if (CREATE_MODIFIERS.contains(word)) {
                continue;
            }
            if ("procedure".equals(word) || "function".equals(word) || CREATE_OBJECTS.contains(word)) {
                return word;
            }
        }
        return "";
    }

    private static String actionObjectType(SqlWordScanner scanner) {
        for (int count = 0; count < 16; count++) {
            String word = scanner.next();
            if (word.isEmpty()) {
                return "";
            }
            if ("procedure".equals(word) || "function".equals(word)) {
                return word;
            }
            if (!CREATE_MODIFIERS.contains(word)) {
                return "";
            }
        }
        return "";
    }

    private static String objectAfterDefiner(SqlWordScanner scanner) {
        for (int count = 0; count < 16; count++) {
            String word = scanner.next();
            if (word.isEmpty()) {
                return "";
            }
            if (DEFINER_OBJECTS.contains(word)) {
                return word;
            }
        }
        return "";
    }
}
