package io.github.absketches.runway.codegen.sql.dialects;

public class MySqlSplittingRules implements SqlDialectRules {
    @Override
    public boolean supportsDelimiterDirective() {
        return true;
    }

    @Override
    public boolean supportsBacktickIdentifier() {
        return true;
    }

    @Override
    public boolean supportsHashLineComment() {
        return true;
    }

    @Override
    public boolean supportsCompoundTriggerBlock() {
        return true;
    }

    @Override
    public boolean singleQuoteBackslashEscapes(String sql, int quoteOffset) {
        return true;
    }

    @Override
    public boolean doubleQuoteBackslashEscapes() {
        return true;
    }
}
