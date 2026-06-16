package io.github.absketches.runway.postgresql;

import io.github.absketches.runway.MigrationException;
import io.github.absketches.runway.SqlScriptParser;
import io.github.absketches.runway.SqlStatement;

import java.util.ArrayList;
import java.util.List;

public final class PostgreSqlScriptParser implements SqlScriptParser {
    @Override
    public List<SqlStatement> parse(String sql, String scriptName) {
        List<SqlStatement> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int statementStartLine = 1;
        int line = 1;
        State state = State.NORMAL;
        String dollarTag = null;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            current.append(c);

            if (c == '\n') {
                line++;
            }

            switch (state) {
                case NORMAL -> {
                    if (c == '\'' ) {
                        state = State.SINGLE_QUOTE;
                    } else if (c == '"') {
                        state = State.DOUBLE_QUOTE;
                    } else if (c == '-' && next == '-') {
                        current.append(next);
                        i++;
                        state = State.LINE_COMMENT;
                    } else if (c == '/' && next == '*') {
                        current.append(next);
                        i++;
                        state = State.BLOCK_COMMENT;
                    } else if (c == '$') {
                        String tag = readDollarTag(sql, i);
                        if (tag != null) {
                            dollarTag = tag;
                            current.append(sql, i + 1, i + tag.length());
                            i += tag.length() - 1;
                            state = State.DOLLAR_QUOTE;
                        }
                    } else if (c == ';') {
                        addStatement(statements, current, statementStartLine, line);
                        statementStartLine = line;
                    }
                }
                case SINGLE_QUOTE -> {
                    if (c == '\'' && next == '\'') {
                        current.append(next);
                        i++;
                    } else if (c == '\'') {
                        state = State.NORMAL;
                    }
                }
                case DOUBLE_QUOTE -> {
                    if (c == '"' && next == '"') {
                        current.append(next);
                        i++;
                    } else if (c == '"') {
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
                case DOLLAR_QUOTE -> {
                    if (c == '$' && dollarTag != null && sql.startsWith(dollarTag, i)) {
                        current.append(sql, i + 1, i + dollarTag.length());
                        i += dollarTag.length() - 1;
                        state = State.NORMAL;
                    }
                }
            }
        }

        if (state == State.SINGLE_QUOTE || state == State.DOUBLE_QUOTE || state == State.BLOCK_COMMENT || state == State.DOLLAR_QUOTE) {
            throw new MigrationException("Unterminated SQL construct in " + scriptName + " near line " + line);
        }
        addStatement(statements, current, statementStartLine, line);
        return List.copyOf(statements);
    }

    private static void addStatement(List<SqlStatement> statements, StringBuilder current, int startLine, int endLine) {
        String statement = current.toString().trim();
        if (!statement.isBlank()) {
            statements.add(new SqlStatement(statement, startLine, endLine));
        }
        current.setLength(0);
    }

    private static String readDollarTag(String sql, int offset) {
        int end = sql.indexOf('$', offset + 1);
        if (end < 0) {
            return null;
        }
        String candidate = sql.substring(offset, end + 1);
        if (candidate.length() == 2) {
            return candidate;
        }
        for (int i = 1; i < candidate.length() - 1; i++) {
            char c = candidate.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) {
                return null;
            }
        }
        return candidate;
    }

    private enum State {
        NORMAL,
        SINGLE_QUOTE,
        DOUBLE_QUOTE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        DOLLAR_QUOTE
    }
}
