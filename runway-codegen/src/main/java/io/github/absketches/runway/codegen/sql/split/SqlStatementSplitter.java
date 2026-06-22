package io.github.absketches.runway.codegen.sql.split;

import io.github.absketches.runway.codegen.sql.CodegenDialect;

import java.util.List;

public final class SqlStatementSplitter {
    private SqlStatementSplitter() {
    }

    public static List<SplitStatement> split(String sql, String scriptName, CodegenDialect dialect) {
        return new SqlScanner(sql, scriptName, dialect.splittingRules()).split();
    }
}
