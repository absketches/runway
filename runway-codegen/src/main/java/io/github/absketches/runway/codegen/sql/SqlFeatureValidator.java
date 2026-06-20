package io.github.absketches.runway.codegen.sql;

import io.github.absketches.runway.codegen.CodegenException;

import java.util.Locale;
import java.util.Set;

public final class SqlFeatureValidator {
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

    private SqlFeatureValidator() {
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
        WordScanner scanner = new WordScanner(sql);
        String firstWord = scanner.next();
        if ("create".equals(firstWord)) {
            return createdObjectType(scanner);
        }
        if (ROUTINE_ACTIONS.contains(firstWord)) {
            return actionObjectType(scanner);
        }
        return "";
    }

    private static String createdObjectType(WordScanner scanner) {
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

    private static String actionObjectType(WordScanner scanner) {
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

    private static String objectAfterDefiner(WordScanner scanner) {
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

    private static final class WordScanner {
        private final String sql;
        private int offset;

        private WordScanner(String sql) {
            this.sql = sql;
        }

        private String next() {
            while (offset < sql.length()) {
                char current = sql.charAt(offset);
                char next = offset + 1 < sql.length() ? sql.charAt(offset + 1) : '\0';
                if (Character.isWhitespace(current) || !Character.isJavaIdentifierStart(current)) {
                    if (current == '-' && next == '-') {
                        skipLine();
                    } else if (current == '#') {
                        skipLine();
                    } else if (current == '/' && next == '*') {
                        skipBlockComment();
                    } else if (current == '\'' || current == '"' || current == '`' || current == '[') {
                        skipQuoted(current);
                    } else {
                        offset++;
                    }
                    continue;
                }

                int start = offset++;
                while (offset < sql.length() && Character.isJavaIdentifierPart(sql.charAt(offset))) {
                    offset++;
                }
                return sql.substring(start, offset).toLowerCase(Locale.ROOT);
            }
            return "";
        }

        private void skipLine() {
            int newline = sql.indexOf('\n', offset);
            offset = newline < 0 ? sql.length() : newline + 1;
        }

        private void skipBlockComment() {
            int end = sql.indexOf("*/", offset + 2);
            offset = end < 0 ? sql.length() : end + 2;
        }

        private void skipQuoted(char opening) {
            char closing = opening == '[' ? ']' : opening;
            offset++;
            while (offset < sql.length()) {
                char current = sql.charAt(offset++);
                if (current == closing) {
                    if (offset < sql.length() && sql.charAt(offset) == closing) {
                        offset++;
                    } else {
                        return;
                    }
                } else if (current == '\\' && offset < sql.length()) {
                    offset++;
                }
            }
        }
    }
}
