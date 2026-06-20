package io.github.absketches.runway.codegen.sql;

import io.github.absketches.runway.codegen.CodegenException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlFeatureValidatorTest {
    @Test
    void rejectsRoutineDefinitionsWithModifiers() {
        assertRejects("create definer = current_user procedure refresh_users() select 1");
        assertRejects("create or replace function user_count() returns integer return 1");
        assertRejects("alter procedure refresh_users comment 'refreshes users'");
        assertRejects("drop function user_count");
        assertRejects("replace procedure refresh_users() select 1");
        assertRejects("update procedure refresh_users");
    }

    @Test
    void acceptsTriggersAndRoutineWordsOutsideCreateObjectType() {
        assertDoesNotThrow(() -> SqlFeatureValidator.validate(
            "create trigger users_audit after update on users begin select 1; end",
            "V1__trigger.sql"
        ));
        assertDoesNotThrow(() -> SqlFeatureValidator.validate(
            "insert into messages (body) values ('create function is documentation')",
            "V1__message.sql"
        ));
        assertDoesNotThrow(() -> SqlFeatureValidator.validate(
            "create table functions (function_name text)",
            "V1__table.sql"
        ));
    }

    private static void assertRejects(String sql) {
        assertThrows(
            CodegenException.class,
            () -> SqlFeatureValidator.validate(sql, "V1__routine.sql")
        );
    }
}
