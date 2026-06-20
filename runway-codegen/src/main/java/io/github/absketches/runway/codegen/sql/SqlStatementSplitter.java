package io.github.absketches.runway.codegen.sql;

import io.github.absketches.runway.codegen.CodegenException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SqlStatementSplitter {
    private static final List<String> NON_TRIGGER_CREATE_OBJECTS = List.of(
        "database",
        "event",
        "function",
        "index",
        "procedure",
        "schema",
        "sequence",
        "table",
        "type",
        "view"
    );

    private SqlStatementSplitter() {
    }

    public static List<SplitStatement> split(String sql, String scriptName, CodegenDialect dialect) {
        List<SplitStatement> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        SplitContext context = new SplitContext(dialect);
        int line = 1;
        State state = State.NORMAL;
        String dollarTag = null;
        int blockCommentDepth = 0;
        boolean singleQuoteBackslashEscapes = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (state == State.NORMAL && context.atLineStart() && current.toString().isBlank()) {
                DelimiterDirective directive = delimiterDirective(sql, i);
                if (directive != null) {
                    if (!context.mysqlCompatible()) {
                        throw new CodegenException(
                            "DELIMITER directives are only valid for MySQL and MariaDB migrations: "
                                + scriptName + " near line " + line
                        );
                    }
                    context.delimiter(directive.delimiter());
                    i = directive.lineEnd() - 1;
                    context.atLineStart(true);
                    if (directive.hasNewline()) {
                        line++;
                    }
                    continue;
                }
            }

            switch (state) {
                case NORMAL -> {
                    if (matchesDelimiter(sql, i, context.delimiter())) {
                        context.finishWord();
                        if (context.canTerminate()) {
                            if (";".equals(context.delimiter())) {
                                current.append(';');
                            }
                            addStatement(statements, current);
                            context.resetStatement();
                            i += context.delimiter().length() - 1;
                            continue;
                        }
                    }

                    current.append(c);
                    if (isWordCharacter(c)) {
                        context.appendWord(c);
                    } else {
                        context.finishWord();
                    }

                    if (c == '\'') {
                        singleQuoteBackslashEscapes = context.mysqlCompatible()
                            || postgresEscapeString(sql, i, dialect);
                        state = State.SINGLE_QUOTE;
                    } else if (c == '"') {
                        state = State.DOUBLE_QUOTE;
                    } else if (c == '`' && dialect != CodegenDialect.POSTGRESQL) {
                        state = State.BACKTICK;
                    } else if (c == '[' && dialect == CodegenDialect.SQLITE) {
                        state = State.BRACKET;
                    } else if (c == '-' && next == '-') {
                        current.append(next);
                        i++;
                        state = State.LINE_COMMENT;
                    } else if (c == '#' && context.mysqlCompatible()) {
                        state = State.LINE_COMMENT;
                    } else if (c == '/' && next == '*') {
                        current.append(next);
                        i++;
                        blockCommentDepth = 1;
                        state = State.BLOCK_COMMENT;
                    } else if (c == '$' && dialect == CodegenDialect.POSTGRESQL) {
                        String tag = dollarTag(sql, i);
                        if (tag != null) {
                            dollarTag = tag;
                            current.append(sql, i + 1, i + tag.length());
                            i += tag.length() - 1;
                            state = State.DOLLAR_QUOTE;
                        }
                    }
                }
                case SINGLE_QUOTE -> {
                    current.append(c);
                    if (c == '\\' && next != '\0' && singleQuoteBackslashEscapes) {
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
                    current.append(c);
                    if (c == '\\' && next != '\0' && context.mysqlCompatible()) {
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
                    current.append(c);
                    if (c == '`' && next == '`') {
                        current.append(next);
                        i++;
                    } else if (c == '`') {
                        state = State.NORMAL;
                    }
                }
                case BRACKET -> {
                    current.append(c);
                    if (c == ']' && next == ']') {
                        current.append(next);
                        i++;
                    } else if (c == ']') {
                        state = State.NORMAL;
                    }
                }
                case LINE_COMMENT -> {
                    current.append(c);
                    if (c == '\n') {
                        state = State.NORMAL;
                    }
                }
                case BLOCK_COMMENT -> {
                    current.append(c);
                    if (c == '/' && next == '*' && dialect == CodegenDialect.POSTGRESQL) {
                        current.append(next);
                        i++;
                        blockCommentDepth++;
                    } else if (c == '*' && next == '/') {
                        current.append(next);
                        i++;
                        blockCommentDepth--;
                        if (blockCommentDepth == 0) {
                            state = State.NORMAL;
                        }
                    }
                }
                case DOLLAR_QUOTE -> {
                    current.append(c);
                    if (c == '$' && dollarTag != null && sql.startsWith(dollarTag, i)) {
                        current.append(sql, i + 1, i + dollarTag.length());
                        i += dollarTag.length() - 1;
                        state = State.NORMAL;
                    }
                }
            }

            if (c == '\n') {
                line++;
                context.atLineStart(true);
            } else if (!Character.isWhitespace(c)) {
                context.atLineStart(false);
            }
        }

        if (state != State.NORMAL && state != State.LINE_COMMENT) {
            throw new CodegenException("Unterminated SQL construct in " + scriptName + " near line " + line);
        }
        context.finishWord();
        addStatement(statements, current);
        return List.copyOf(statements);
    }

    private static void addStatement(List<SplitStatement> statements, StringBuilder current) {
        String statement = current.toString().trim();
        if (!statement.isBlank()) {
            statements.add(new SplitStatement(statement));
        }
        current.setLength(0);
    }

    private static String dollarTag(String sql, int offset) {
        int end = sql.indexOf('$', offset + 1);
        if (end < 0) {
            return null;
        }
        String candidate = sql.substring(offset, end + 1);
        if (candidate.length() > 2) {
            char first = candidate.charAt(1);
            if (!(Character.isLetter(first) || first == '_')) {
                return null;
            }
        }
        for (int i = 1; i < candidate.length() - 1; i++) {
            char c = candidate.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) {
                return null;
            }
        }
        return candidate;
    }

    private static boolean postgresEscapeString(String sql, int quoteOffset, CodegenDialect dialect) {
        if (dialect != CodegenDialect.POSTGRESQL || quoteOffset == 0) {
            return false;
        }
        char prefix = sql.charAt(quoteOffset - 1);
        if (prefix != 'e' && prefix != 'E') {
            return false;
        }
        return quoteOffset < 2 || !isWordCharacter(sql.charAt(quoteOffset - 2));
    }

    private static boolean matchesDelimiter(String sql, int offset, String delimiter) {
        return !delimiter.isEmpty() && sql.startsWith(delimiter, offset);
    }

    private static DelimiterDirective delimiterDirective(String sql, int offset) {
        int lineEnd = sql.indexOf('\n', offset);
        boolean hasNewline = lineEnd >= 0;
        int end = hasNewline ? lineEnd + 1 : sql.length();
        String line = sql.substring(offset, hasNewline ? lineEnd : end).strip();
        if (line.length() < "delimiter".length()
            || !line.regionMatches(true, 0, "delimiter", 0, "delimiter".length())) {
            return null;
        }
        if (line.length() > "delimiter".length()
            && !Character.isWhitespace(line.charAt("delimiter".length()))) {
            return null;
        }
        String delimiter = line.substring("delimiter".length()).strip();
        if (delimiter.isEmpty() || delimiter.chars().anyMatch(Character::isWhitespace)) {
            throw new CodegenException("Invalid MySQL DELIMITER directive: " + line);
        }
        return new DelimiterDirective(delimiter, end, hasNewline);
    }

    private static boolean isWordCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }

    private record DelimiterDirective(String delimiter, int lineEnd, boolean hasNewline) {
    }

    private static final class SplitContext {
        private final CodegenDialect dialect;
        private final StringBuilder word = new StringBuilder();
        private final List<String> leadingWords = new ArrayList<>();
        private String delimiter = ";";
        private boolean atLineStart = true;
        private boolean compoundTrigger;
        private int compoundBlockDepth;

        private SplitContext(CodegenDialect dialect) {
            this.dialect = dialect;
        }

        private boolean mysqlCompatible() {
            return dialect == CodegenDialect.MYSQL || dialect == CodegenDialect.MARIADB;
        }

        private String delimiter() {
            return delimiter;
        }

        private void delimiter(String delimiter) {
            this.delimiter = delimiter;
        }

        private boolean atLineStart() {
            return atLineStart;
        }

        private void atLineStart(boolean atLineStart) {
            this.atLineStart = atLineStart;
        }

        private void appendWord(char value) {
            word.append(value);
        }

        private void finishWord() {
            if (word.isEmpty()) {
                return;
            }
            String value = word.toString().toLowerCase(Locale.ROOT);
            word.setLength(0);
            if (leadingWords.size() < 8) {
                leadingWords.add(value);
                compoundTrigger = isCompoundTrigger(leadingWords);
            }
            if (compoundTrigger) {
                if ("begin".equals(value) || "case".equals(value)) {
                    compoundBlockDepth++;
                } else if ("end".equals(value) && compoundBlockDepth > 0) {
                    compoundBlockDepth--;
                }
            }
        }

        private boolean canTerminate() {
            return !compoundTrigger || compoundBlockDepth == 0;
        }

        private void resetStatement() {
            word.setLength(0);
            leadingWords.clear();
            compoundTrigger = false;
            compoundBlockDepth = 0;
        }

        private boolean isCompoundTrigger(List<String> words) {
            if (dialect == CodegenDialect.POSTGRESQL) {
                return false;
            }
            if (words.isEmpty() || !"create".equals(words.getFirst())) {
                return false;
            }
            for (int index = 1; index < words.size(); index++) {
                String value = words.get(index);
                if ("trigger".equals(value)) {
                    return true;
                }
                if (NON_TRIGGER_CREATE_OBJECTS.contains(value)) {
                    return false;
                }
            }
            return false;
        }
    }

    private enum State {
        NORMAL,
        SINGLE_QUOTE,
        DOUBLE_QUOTE,
        BACKTICK,
        BRACKET,
        LINE_COMMENT,
        BLOCK_COMMENT,
        DOLLAR_QUOTE
    }
}
