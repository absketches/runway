package io.github.absketches.runway.codegen.sql.dialects;

public final class PostgreSqlSplittingRules implements SqlDialectRules {
    @Override
    public boolean supportsDollarQuotedString() {
        return true;
    }

    @Override
    public boolean supportsNestedBlockComment() {
        return true;
    }

    @Override
    public boolean singleQuoteBackslashEscapes(String sql, int quoteOffset) {
        if (quoteOffset == 0) {
            return false;
        }
        char prefix = sql.charAt(quoteOffset - 1);
        if (prefix != 'e' && prefix != 'E') {
            return false;
        }
        return quoteOffset < 2 || !isWordCharacter(sql.charAt(quoteOffset - 2));
    }

    private static boolean isWordCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }
}
