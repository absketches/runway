package io.github.absketches.runway.codegen.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SqlIdentifierParser {
    private SqlIdentifierParser() {
    }

    static List<String> identifiers(String value) {
        List<String> identifiers = new ArrayList<>();
        for (String part : SqlSyntaxScanner.splitTopLevel(value)) {
            String candidate = part.strip();
            int whitespace = candidate.indexOf(' ');
            if (whitespace > 0) {
                candidate = candidate.substring(0, whitespace);
            }
            if (!candidate.isEmpty()) {
                identifiers.add(identifier(candidate));
            }
        }
        return List.copyOf(identifiers);
    }

    static String firstToken(String value) {
        int whitespace = 0;
        while (whitespace < value.length() && !Character.isWhitespace(value.charAt(whitespace))) {
            whitespace++;
        }
        return value.substring(0, whitespace);
    }

    static String identifier(String value) {
        String cleaned = value.strip();
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\""))
            || (cleaned.startsWith("`") && cleaned.endsWith("`"))
            || (cleaned.startsWith("[") && cleaned.endsWith("]"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }

    static String tableName(String qualifiedName) {
        int dot = qualifiedName.lastIndexOf('.');
        return dot < 0 ? qualifiedName : qualifiedName.substring(dot + 1);
    }

    static String aliasKey(String value) {
        return tableName(identifier(value)).toLowerCase(Locale.ROOT);
    }
}
