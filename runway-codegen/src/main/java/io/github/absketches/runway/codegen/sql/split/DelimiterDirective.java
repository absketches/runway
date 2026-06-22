package io.github.absketches.runway.codegen.sql.split;

import io.github.absketches.runway.codegen.CodegenException;

record DelimiterDirective(String delimiter, int lineEnd, boolean hasNewline) {
    static DelimiterDirective parse(String sql, int offset) {
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
            throw new CodegenException("Invalid DELIMITER directive: " + line);
        }
        return new DelimiterDirective(delimiter, end, hasNewline);
    }
}
