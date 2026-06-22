package io.github.absketches.runway.codegen.sql.dialects;

public final class SqliteSplittingRules implements SqlDialectRules {
    @Override
    public boolean supportsBacktickIdentifier() {
        return true;
    }

    @Override
    public boolean supportsBracketIdentifier() {
        return true;
    }

    @Override
    public boolean supportsCompoundTriggerBlock() {
        return true;
    }
}
