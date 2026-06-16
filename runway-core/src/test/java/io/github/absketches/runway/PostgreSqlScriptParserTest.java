package io.github.absketches.runway;

import io.github.absketches.runway.postgresql.PostgreSqlScriptParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostgreSqlScriptParserTest {
    @Test
    void ignoresSemicolonsInsideStringsCommentsAndDollarQuotes() {
        String sql = """
            create table demo (value text default ';');
            -- this comment has ;
            create function demo_fn() returns void as $$
            begin
                perform ';';
            end;
            $$ language plpgsql;
            """;

        var statements = new PostgreSqlScriptParser().parse(sql, "V1__demo.sql");

        assertEquals(2, statements.size());
        assertEquals("create table demo (value text default ';');", statements.get(0).sql());
        assertEquals(true, statements.get(1).sql().contains("language plpgsql"));
    }
}
