package io.github.absketches.runway.mysql;

import io.github.absketches.runway.MigrationException;
import io.github.absketches.runway.SqlScriptParser;
import io.github.absketches.runway.SqlStatement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MySqlScriptParser implements SqlScriptParser {
    @Override
    public List<SqlStatement> parse(String sql, String scriptName) {
        List<SqlStatement> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String delimiter = ";";
        int statementStartLine = 1;
        int line = 1;
        int lineStart = 0;
        State state = State.NORMAL;

        for (int i = 0; i < sql.length(); i++) {
            if (state == State.NORMAL && i == lineStart) {
                DelimiterDirective directive = delimiterDirective(sql, i);
                if (directive != null) {
                    addStatement(statements, current, statementStartLine, line);
                    delimiter = directive.delimiter();
                    i = directive.endOffset() - 1;
                    line++;
                    lineStart = directive.endOffset();
                    statementStartLine = line;
                    continue;
                }
            }

            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            current.append(c);

            if (c == '\n') {
                line++;
                lineStart = i + 1;
            }

            switch (state) {
                case NORMAL -> {
                    if (c == '\'') {
                        state = State.SINGLE_QUOTE;
                    } else if (c == '"') {
                        state = State.DOUBLE_QUOTE;
                    } else if (c == '`') {
                        state = State.BACKTICK;
                    } else if (c == '-' && next == '-') {
                        current.append(next);
                        i++;
                        state = State.LINE_COMMENT;
                    } else if (c == '#') {
                        state = State.LINE_COMMENT;
                    } else if (c == '/' && next == '*') {
                        current.append(next);
                        i++;
                        state = State.BLOCK_COMMENT;
                    } else if (matchesDelimiter(sql, i, delimiter)) {
                        current.setLength(current.length() - 1);
                        addStatement(statements, current, statementStartLine, line);
                        i += delimiter.length() - 1;
                        statementStartLine = line;
                    }
                }
                case SINGLE_QUOTE -> {
                    if (c == '\\' && next != '\0') {
                        current.append(next);
                        i++;
                    } else if (c == '\'' && next == '\'') {
                        current.append(next);
                        i++;
                    } else if (c == '\'') {
                        state = State.NORMAL;
                    }
                }
                case DOUBLE_QUOTE -> {
                    if (c == '\\' && next != '\0') {
                        current.append(next);
                        i++;
                    } else if (c == '"' && next == '"') {
                        current.append(next);
                        i++;
                    } else if (c == '"') {
                        state = State.NORMAL;
                    }
                }
                case BACKTICK -> {
                    if (c == '`' && next == '`') {
                        current.append(next);
                        i++;
                    } else if (c == '`') {
                        state = State.NORMAL;
                    }
                }
                case LINE_COMMENT -> {
                    if (c == '\n') {
                        state = State.NORMAL;
                    }
                }
                case BLOCK_COMMENT -> {
                    if (c == '*' && next == '/') {
                        current.append(next);
                        i++;
                        state = State.NORMAL;
                    }
                }
            }
        }

        if (state == State.SINGLE_QUOTE || state == State.DOUBLE_QUOTE || state == State.BACKTICK || state == State.BLOCK_COMMENT) {
            throw new MigrationException("Unterminated SQL construct in " + scriptName + " near line " + line);
        }
        addStatement(statements, current, statementStartLine, line);
        return List.copyOf(statements);
    }

    private static boolean matchesDelimiter(String sql, int offset, String delimiter) {
        return sql.startsWith(delimiter, offset);
    }

    private static void addStatement(List<SqlStatement> statements, StringBuilder current, int startLine, int endLine) {
        String statement = current.toString().trim();
        if (!statement.isBlank()) {
            statements.add(new SqlStatement(statement, startLine, endLine));
        }
        current.setLength(0);
    }

    private static DelimiterDirective delimiterDirective(String sql, int offset) {
        int lineEnd = sql.indexOf('\n', offset);
        if (lineEnd < 0) {
            lineEnd = sql.length();
        } else {
            lineEnd++;
        }
        String line = sql.substring(offset, lineEnd).strip();
        String lower = line.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("delimiter ")) {
            return null;
        }
        String delimiter = line.substring("delimiter ".length()).strip();
        if (delimiter.isEmpty()) {
            throw new MigrationException("MySQL DELIMITER directive must include a delimiter");
        }
        return new DelimiterDirective(delimiter, lineEnd);
    }

    private record DelimiterDirective(String delimiter, int endOffset) {
    }

    private enum State {
        NORMAL,
        SINGLE_QUOTE,
        DOUBLE_QUOTE,
        BACKTICK,
        LINE_COMMENT,
        BLOCK_COMMENT
    }
}
