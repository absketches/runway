package io.github.absketches.runway.codegen.analysis;

import java.util.regex.Pattern;

final class SqlRegexPatterns {
    static final String IDENTIFIER_PART = "[`\"\\[]?[A-Za-z_][A-Za-z0-9_$]*[`\"\\]]?";
    static final String IDENTIFIER = "(?:" + IDENTIFIER_PART + ")(?:\\.(?:" + IDENTIFIER_PART + "))?";
    static final Pattern SIMPLE_COLUMN = pattern("^" + IDENTIFIER + "$");
    static final Pattern COUNT_STAR = pattern("^count\\s*\\(\\s*\\*\\s*\\)$");
    static final Pattern LITERAL = pattern("^(?:null|true|false|[-+]?\\d+(?:\\.\\d+)?|'(?:''|[^'])*')$");

    private SqlRegexPatterns() {
    }

    static Pattern pattern(String expression) {
        return Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }
}
