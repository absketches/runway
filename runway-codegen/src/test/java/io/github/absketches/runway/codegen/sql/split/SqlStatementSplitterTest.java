package io.github.absketches.runway.codegen.sql.split;

import io.github.absketches.runway.codegen.CodegenException;
import io.github.absketches.runway.codegen.sql.CodegenDialect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlStatementSplitterTest {
    @Test
    void splitsSqliteWithoutSplittingQuotedSemicolons() {
        String sql = """
            create table [weird;name] ("value" text default ';');
            -- comment ;
            create index `idx;value` on [weird;name] ("value");
            """;

        var statements = SqlStatementSplitter.split(sql, "V1__sqlite.sql", CodegenDialect.SQLITE);

        assertEquals(2, statements.size());
        assertEquals("create table [weird;name] (\"value\" text default ';');", statements.getFirst().sql());
    }

    @Test
    void splitsMySqlWithoutSplittingBackticksStringsOrComments() {
        String sql = """
            create table `weird;name` (`value` varchar(100) default ';');
            # comment ;
            insert into `weird;name` values ('still;one');
            """;

        assertEquals(2, SqlStatementSplitter.split(sql, "V1__mysql.sql", CodegenDialect.MYSQL).size());
    }

    @Test
    void recognizesPostgreSqlDollarQuotedText() {
        String sql = """
            insert into messages (body) values ($tag$text;with;semicolons$tag$);
            select 1;
            """;

        assertEquals(2, SqlStatementSplitter.split(sql, "V1__postgres.sql", CodegenDialect.POSTGRESQL).size());
    }

    @Test
    void recognizesPostgreSqlEscapeStringsAndNestedComments() {
        String sql = """
            /* outer /* inner; */ still outer; */
            insert into messages (body) values (E'quoted\\';semicolon');
            create table messages_archive (id bigint);
            """;

        assertEquals(2, SqlStatementSplitter.split(sql, "V1__postgres.sql", CodegenDialect.POSTGRESQL).size());
    }

    @Test
    void removesMySqlDelimiterDirectivesAndKeepsTriggerBodyTogether() {
        String sql = """
            delimiter //
            create trigger users_audit after update on users
            for each row
            begin
              insert into audit_log (user_id) values (new.id);
              update counters set value = value + 1 where name = 'users';
            end//
            delimiter ;
            create index users_email_idx on users (email);
            """;

        var statements = SqlStatementSplitter.split(sql, "V1__mysql.sql", CodegenDialect.MYSQL);

        assertEquals(2, statements.size());
        assertEquals(false, statements.getFirst().sql().toLowerCase().contains("delimiter"));
        assertEquals(true, statements.getFirst().sql().contains("update counters"));
    }

    @Test
    void keepsSqliteTriggerBodyTogether() {
        String sql = """
            create trigger users_audit after update on users
            begin
              insert into audit_log (user_id) values (new.id);
              update counters
              set value = case when value < 0 then 0 else value + 1 end
              where name = 'users';
            end;
            create index users_email_idx on users (email);
            """;

        var statements = SqlStatementSplitter.split(sql, "V1__sqlite.sql", CodegenDialect.SQLITE);

        assertEquals(2, statements.size());
        assertEquals(true, statements.getFirst().sql().contains("update counters"));
    }

    @Test
    void rejectsDelimiterDirectiveWhenDialectDoesNotSupportIt() {
        String sql = """
            delimiter //
            select 1//
            """;

        CodegenException exception = assertThrows(
            CodegenException.class,
            () -> SqlStatementSplitter.split(sql, "V1__sqlite.sql", CodegenDialect.SQLITE)
        );

        assertTrue(exception.getMessage().contains("not supported by the configured SQL dialect"));
    }
}
