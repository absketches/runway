package io.github.absketches.runway;

import java.util.List;

public interface SqlScriptParser {
    List<SqlStatement> parse(String sql, String scriptName);
}
