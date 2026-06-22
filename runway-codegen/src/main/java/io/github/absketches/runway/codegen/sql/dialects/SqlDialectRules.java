package io.github.absketches.runway.codegen.sql.dialects;

public interface SqlDialectRules {
    default boolean supportsDelimiterDirective() {
        return false;
    }

    default boolean supportsBacktickIdentifier() {
        return false;
    }

    default boolean supportsBracketIdentifier() {
        return false;
    }

    default boolean supportsHashLineComment() {
        return false;
    }

    default boolean supportsDollarQuotedString() {
        return false;
    }

    default boolean supportsNestedBlockComment() {
        return false;
    }

    default boolean supportsCompoundTriggerBlock() {
        return false;
    }

    default boolean singleQuoteBackslashEscapes(String sql, int quoteOffset) {
        return false;
    }

    default boolean doubleQuoteBackslashEscapes() {
        return false;
    }
}
