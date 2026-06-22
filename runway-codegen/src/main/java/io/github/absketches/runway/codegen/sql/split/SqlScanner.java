package io.github.absketches.runway.codegen.sql.split;

import io.github.absketches.runway.codegen.CodegenException;
import io.github.absketches.runway.codegen.sql.dialects.SqlDialectRules;

import java.util.ArrayList;
import java.util.List;

final class SqlScanner {
    private final String sql;
    private final String scriptName;
    private final SqlDialectRules rules;
    private final List<SplitStatement> statements = new ArrayList<>();
    private final StringBuilder current = new StringBuilder();
    private final SqlSplitContext context;
    private int offset = 0;
    private int line = 1;
    private SqlScannerState state = SqlScannerState.NORMAL;
    private String dollarTag = null;
    private int blockCommentDepth = 0;
    private boolean singleQuoteBackslashEscapes = false;

    SqlScanner(String sql, String scriptName, SqlDialectRules rules) {
        this.sql = sql;
        this.scriptName = scriptName;
        this.rules = rules;
        this.context = new SqlSplitContext(rules);
    }

    List<SplitStatement> split() {
        while (offset < sql.length()) {
            if (consumeDelimiterDirective()) {
                offset++;
                continue;
            }

            int start = offset;
            switch (state) {
                case NORMAL -> handleNormal();
                case SINGLE_QUOTE -> handleSingleQuote();
                case DOUBLE_QUOTE -> handleDoubleQuote();
                case BACKTICK -> handleBacktick();
                case BRACKET -> handleBracket();
                case LINE_COMMENT -> handleLineComment();
                case BLOCK_COMMENT -> handleBlockComment();
                case DOLLAR_QUOTE -> handleDollarQuote();
            }
            trackPosition(start, offset);
            offset++;
        }

        if (state != SqlScannerState.NORMAL && state != SqlScannerState.LINE_COMMENT) {
            throw new CodegenException("Unterminated SQL construct in " + scriptName + " near line " + line);
        }
        context.finishWord();
        addStatement();
        return List.copyOf(statements);
    }

    private boolean consumeDelimiterDirective() {
        if (state != SqlScannerState.NORMAL || !context.atLineStart() || !currentIsBlank()) {
            return false;
        }

        DelimiterDirective directive = DelimiterDirective.parse(sql, offset);
        if (directive == null) {
            return false;
        }

        if (!rules.supportsDelimiterDirective()) {
            throw new CodegenException(
                "DELIMITER directives are not supported by the configured SQL dialect: "
                    + scriptName + " near line " + line
            );
        }
        current.setLength(0);
        context.delimiter(directive.delimiter());
        offset = directive.lineEnd() - 1;
        context.atLineStart(true);
        if (directive.hasNewline()) {
            line++;
        }
        return true;
    }

    private void handleNormal() {
        if (terminateAtDelimiter()) {
            return;
        }

        char c = currentChar();
        char next = nextChar();
        current.append(c);
        if (isWordCharacter(c)) {
            context.appendWord(c);
        } else {
            context.finishWord();
        }

        if (c == '\'') {
            singleQuoteBackslashEscapes = rules.singleQuoteBackslashEscapes(sql, offset);
            state = SqlScannerState.SINGLE_QUOTE;
        } else if (c == '"') {
            state = SqlScannerState.DOUBLE_QUOTE;
        } else if (c == '`' && rules.supportsBacktickIdentifier()) {
            state = SqlScannerState.BACKTICK;
        } else if (c == '[' && rules.supportsBracketIdentifier()) {
            state = SqlScannerState.BRACKET;
        } else if (c == '-' && next == '-') {
            appendNext();
            state = SqlScannerState.LINE_COMMENT;
        } else if (c == '#' && rules.supportsHashLineComment()) {
            state = SqlScannerState.LINE_COMMENT;
        } else if (c == '/' && next == '*') {
            appendNext();
            blockCommentDepth = 1;
            state = SqlScannerState.BLOCK_COMMENT;
        } else if (c == '$' && rules.supportsDollarQuotedString()) {
            String tag = dollarTag(sql, offset);
            if (tag != null) {
                dollarTag = tag;
                appendAfterCurrent(tag.length());
                state = SqlScannerState.DOLLAR_QUOTE;
            }
        }
    }

    private boolean terminateAtDelimiter() {
        String delimiter = context.delimiter();
        if (!matchesDelimiter(sql, offset, delimiter)) {
            return false;
        }

        context.finishWord();
        if (!context.canTerminate()) {
            return false;
        }

        if (";".equals(delimiter)) {
            current.append(';');
        }
        addStatement();
        context.resetStatement();
        offset += delimiter.length() - 1;
        return true;
    }

    private void handleSingleQuote() {
        char c = currentChar();
        char next = nextChar();
        current.append(c);
        if (c == '\\' && next != '\0' && singleQuoteBackslashEscapes) {
            appendNext();
        } else if (c == '\'' && next == '\'') {
            appendNext();
        } else if (c == '\'') {
            state = SqlScannerState.NORMAL;
        }
    }

    private void handleDoubleQuote() {
        char c = currentChar();
        char next = nextChar();
        current.append(c);
        if (c == '\\' && next != '\0' && rules.doubleQuoteBackslashEscapes()) {
            appendNext();
        } else if (c == '"' && next == '"') {
            appendNext();
        } else if (c == '"') {
            state = SqlScannerState.NORMAL;
        }
    }

    private void handleBacktick() {
        char c = currentChar();
        char next = nextChar();
        current.append(c);
        if (c == '`' && next == '`') {
            appendNext();
        } else if (c == '`') {
            state = SqlScannerState.NORMAL;
        }
    }

    private void handleBracket() {
        char c = currentChar();
        char next = nextChar();
        current.append(c);
        if (c == ']' && next == ']') {
            appendNext();
        } else if (c == ']') {
            state = SqlScannerState.NORMAL;
        }
    }

    private void handleLineComment() {
        char c = currentChar();
        current.append(c);
        if (c == '\n') {
            state = SqlScannerState.NORMAL;
        }
    }

    private void handleBlockComment() {
        char c = currentChar();
        char next = nextChar();
        current.append(c);
        if (c == '/' && next == '*' && rules.supportsNestedBlockComment()) {
            appendNext();
            blockCommentDepth++;
        } else if (c == '*' && next == '/') {
            appendNext();
            blockCommentDepth--;
            if (blockCommentDepth == 0) {
                state = SqlScannerState.NORMAL;
            }
        }
    }

    private void handleDollarQuote() {
        char c = currentChar();
        current.append(c);
        if (c == '$' && dollarTag != null && sql.startsWith(dollarTag, offset)) {
            appendAfterCurrent(dollarTag.length());
            state = SqlScannerState.NORMAL;
        }
    }

    private char currentChar() {
        return sql.charAt(offset);
    }

    private char nextChar() {
        return offset + 1 < sql.length() ? sql.charAt(offset + 1) : '\0';
    }

    private void appendNext() {
        current.append(nextChar());
        offset++;
    }

    private void appendAfterCurrent(int tokenLength) {
        current.append(sql, offset + 1, offset + tokenLength);
        offset += tokenLength - 1;
    }

    private boolean currentIsBlank() {
        for (int index = 0; index < current.length(); index++) {
            if (!Character.isWhitespace(current.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private void trackPosition(int start, int end) {
        for (int index = start; index <= end; index++) {
            char c = sql.charAt(index);
            if (c == '\n') {
                line++;
                context.atLineStart(true);
            } else if (!Character.isWhitespace(c)) {
                context.atLineStart(false);
            }
        }
    }

    private void addStatement() {
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

    private static boolean matchesDelimiter(String sql, int offset, String delimiter) {
        return !delimiter.isEmpty() && sql.startsWith(delimiter, offset);
    }

    private static boolean isWordCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }
}
