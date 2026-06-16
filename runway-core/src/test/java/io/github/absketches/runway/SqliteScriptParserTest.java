package io.github.absketches.runway;

import io.github.absketches.runway.sqlite.SqliteScriptParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteScriptParserTest {
    @Test
    void ignoresSemicolonsInsideSqliteQuotedConstructs() {
        String sql = """
            create table [weird;name] ("value" text default ';');
            -- comment ;
            create index `idx;value` on [weird;name] ("value");
            """;

        var statements = new SqliteScriptParser().parse(sql, "V1__sqlite.sql");

        assertEquals(2, statements.size());
        assertEquals("create table [weird;name] (\"value\" text default ';');", statements.get(0).sql());
    }
}
