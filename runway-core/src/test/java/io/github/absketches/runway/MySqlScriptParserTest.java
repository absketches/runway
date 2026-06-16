package io.github.absketches.runway;

import io.github.absketches.runway.mysql.MySqlScriptParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlScriptParserTest {
    @Test
    void supportsDelimiterDirectivesForRoutines() {
        String sql = """
            create table users (id bigint primary key);
            delimiter //
            create procedure refresh_users()
            begin
                select ';';
            end//
            delimiter ;
            insert into users values (1);
            """;

        var statements = new MySqlScriptParser().parse(sql, "V1__mysql.sql");

        assertEquals(3, statements.size());
        assertEquals("create table users (id bigint primary key)", statements.get(0).sql());
        assertTrue(statements.get(1).sql().contains("create procedure refresh_users()"));
        assertEquals("insert into users values (1)", statements.get(2).sql());
    }

    @Test
    void ignoresSemicolonsInsideBackticksStringsAndComments() {
        String sql = """
            create table `weird;name` (`value` varchar(100) default ';');
            # comment ;
            insert into `weird;name` values ('still;one');
            """;

        var statements = new MySqlScriptParser().parse(sql, "V2__mysql.sql");

        assertEquals(2, statements.size());
    }
}
