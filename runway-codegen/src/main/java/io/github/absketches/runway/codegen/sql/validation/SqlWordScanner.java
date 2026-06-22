package io.github.absketches.runway.codegen.sql.validation;

import java.util.Locale;

final class SqlWordScanner {
    private final String sql;
    private int offset = 0;

    SqlWordScanner(String sql) {
        this.sql = sql;
    }

    String next() {
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
