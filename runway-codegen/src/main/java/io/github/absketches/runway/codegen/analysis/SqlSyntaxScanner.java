package io.github.absketches.runway.codegen.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SqlSyntaxScanner {
    private SqlSyntaxScanner() {
    }

    static List<String> splitTopLevel(String value) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        char quote = '\0';
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (quote != '\0') {
                if (current == quote) {
                    quote = '\0';
                }
                continue;
            }
            if (current == '\'' || current == '"' || current == '`') {
                quote = current;
            } else if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
            } else if (current == ',' && depth == 0) {
                parts.add(value.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(value.substring(start));
        return parts;
    }

    static List<String> splitStatements(String value) {
        List<String> statements = new ArrayList<>();
        int depth = 0;
        int start = 0;
        char quote = '\0';
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (quote != '\0') {
                if (current == quote) {
                    if (i + 1 < value.length() && value.charAt(i + 1) == quote) {
                        i++;
                    } else {
                        quote = '\0';
                    }
                } else if (current == '\\' && i + 1 < value.length()) {
                    i++;
                }
                continue;
            }
            if (current == '\'' || current == '"' || current == '`') {
                quote = current;
            } else if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
            } else if (current == ';' && depth == 0) {
                statements.add(value.substring(start, i));
                start = i + 1;
            }
        }
        statements.add(value.substring(start));
        return statements;
    }

    static String removeAlias(String expression) {
        String value = expression.strip();
        int alias = topLevelKeywordOffset(value, " as ");
        return alias < 0 ? value : value.substring(0, alias).strip();
    }

    static String removeOrdering(String expression) {
        String value = expression.strip();
        int direction = firstTopLevelKeywordOffset(value, 0, " asc", " desc", " nulls first", " nulls last");
        return direction < 0 ? value : value.substring(0, direction).strip();
    }

    static String simpleColumn(String expression) {
        String value = expression.strip();
        if (value.contains("*") || !SqlRegexPatterns.SIMPLE_COLUMN.matcher(value).matches()) {
            return null;
        }
        return SqlIdentifierParser.tableName(SqlIdentifierParser.identifier(value));
    }

    static String stripLeadingComments(String sql) {
        String value = sql;
        while (true) {
            String stripped = value.stripLeading();
            if (stripped.startsWith("--")) {
                int newline = stripped.indexOf('\n');
                value = newline < 0 ? "" : stripped.substring(newline + 1);
            } else if (stripped.startsWith("/*")) {
                int end = stripped.indexOf("*/", 2);
                value = end < 0 ? stripped : stripped.substring(end + 2);
            } else {
                return stripped;
            }
        }
    }

    static int firstKeywordOffset(String value, String... keywords) {
        String lower = value.toLowerCase(Locale.ROOT);
        int result = -1;
        for (String keyword : keywords) {
            int offset = lower.indexOf(keyword);
            if (offset >= 0 && (result < 0 || offset < result)) {
                result = offset;
            }
        }
        return result;
    }

    static int topLevelKeywordOffset(String value, String keyword) {
        return firstTopLevelKeywordOffset(value, 0, keyword);
    }

    static int keywordOffset(String value, String keyword, int start) {
        String lower = value.toLowerCase(Locale.ROOT);
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        for (int offset = lower.indexOf(lowerKeyword, start);
             offset >= 0;
             offset = lower.indexOf(lowerKeyword, offset + lowerKeyword.length())) {
            if (hasWordBoundary(value, offset - 1) && hasWordBoundary(value, offset + lowerKeyword.length())) {
                return offset;
            }
        }
        return -1;
    }

    static int lastKeywordOffset(String value, String keyword) {
        String lower = value.toLowerCase(Locale.ROOT);
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        int match = -1;
        for (int offset = lower.indexOf(lowerKeyword);
             offset >= 0;
             offset = lower.indexOf(lowerKeyword, offset + lowerKeyword.length())) {
            if (hasWordBoundary(value, offset - 1) && hasWordBoundary(value, offset + lowerKeyword.length())) {
                match = offset;
            }
        }
        return match;
    }

    static int firstTopLevelKeywordOffset(String value, int start, String... keywords) {
        int depth = 0;
        char quote = '\0';
        String lower = value.toLowerCase(Locale.ROOT);
        for (int i = start; i < value.length(); i++) {
            char current = value.charAt(i);
            if (quote != '\0') {
                if (current == quote) {
                    quote = '\0';
                }
                continue;
            }
            if (current == '\'' || current == '"' || current == '`') {
                quote = current;
            } else if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
            } else if (depth == 0) {
                for (String keyword : keywords) {
                    if (lower.startsWith(keyword, i)) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    static String trimTrailingSemicolon(String value) {
        return value.endsWith(";") ? value.substring(0, value.length() - 1).stripTrailing() : value;
    }

    static String collapseWhitespace(String value) {
        StringBuilder builder = new StringBuilder();
        boolean previousWhitespace = false;
        char quote = '\0';
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (quote != '\0') {
                builder.append(current);
                if (current == quote) {
                    quote = '\0';
                }
            } else if (current == '\'' || current == '"' || current == '`') {
                builder.append(current);
                quote = current;
                previousWhitespace = false;
            } else if (Character.isWhitespace(current)) {
                if (!previousWhitespace) {
                    builder.append(' ');
                    previousWhitespace = true;
                }
            } else {
                builder.append(current);
                previousWhitespace = false;
            }
        }
        return builder.toString().strip();
    }

    private static boolean hasWordBoundary(String value, int offset) {
        return offset < 0 || offset >= value.length() || !isWordCharacter(value.charAt(offset));
    }

    private static boolean isWordCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }
}
