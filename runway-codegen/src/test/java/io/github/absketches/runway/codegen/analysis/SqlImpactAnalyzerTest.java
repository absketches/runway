package io.github.absketches.runway.codegen.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlImpactAnalyzerTest {
    @Test
    void analyzesSimpleViewColumns() {
        var change = SqlImpactAnalyzer.analyze("""
            create view user_names as
            select id, name
            from users
            """);

        assertEquals(SqlStatementType.CREATE_VIEW, change.type());
        assertEquals("user_names", change.schemaObject());
        assertEquals(java.util.List.of("users"), change.readTables());
        assertEquals(
            java.util.List.of(
                new ColumnReference("users", "id"),
                new ColumnReference("users", "name")
            ),
            change.readColumns()
        );
        assertTrue(change.analysisComplete());
    }

    @Test
    void analyzesAggregateViewColumns() {
        var change = SqlImpactAnalyzer.analyze("""
            create view audit_summary as
            select severity, count(*) as event_count
            from audit_log
            group by severity
            """);

        assertEquals(SqlStatementType.CREATE_VIEW, change.type());
        assertEquals(java.util.List.of("audit_log"), change.readTables());
        assertEquals(
            java.util.List.of(new ColumnReference("audit_log", "severity")),
            change.readColumns()
        );
        assertTrue(change.analysisComplete());
    }

    @Test
    void keepsJoinedViewsIncomplete() {
        var change = SqlImpactAnalyzer.analyze("""
            create view user_roles as
            select u.id, r.name
            from users u
            join roles r on r.id = u.role_id
            """);

        assertEquals(SqlStatementType.CREATE_VIEW, change.type());
        assertTrue(change.readColumns().contains(new ColumnReference("users", "id")));
        assertTrue(change.readColumns().contains(new ColumnReference("roles", "name")));
        assertTrue(change.readColumns().contains(new ColumnReference("users", "role_id")));
        assertEquals(false, change.analysisComplete());
    }

    @Test
    void analyzesIndexColumns() {
        var change = SqlImpactAnalyzer.analyze(
            "create unique index users_email_idx on users (email, tenant_id);"
        );

        assertEquals(SqlStatementType.CREATE_INDEX, change.type());
        assertEquals("users_email_idx", change.schemaObject());
        assertEquals(java.util.List.of("users"), change.readTables());
        assertEquals(
            java.util.List.of(
                new ColumnReference("users", "email"),
                new ColumnReference("users", "tenant_id")
            ),
            change.readColumns()
        );
        assertTrue(change.analysisComplete());
    }

    @Test
    void keepsExpressionAndPartialIndexesIncomplete() {
        var expression = SqlImpactAnalyzer.analyze(
            "create index users_email_lower_idx on users (lower(email))"
        );
        var partial = SqlImpactAnalyzer.analyze(
            "create index users_email_idx on users (email) where active = true"
        );

        assertEquals(SqlStatementType.CREATE_INDEX, expression.type());
        assertEquals("users_email_lower_idx", expression.schemaObject());
        assertEquals(java.util.List.of("users"), expression.readTables());
        assertEquals(java.util.List.of(), expression.readColumns());
        assertEquals(false, expression.analysisComplete());

        assertEquals(SqlStatementType.CREATE_INDEX, partial.type());
        assertEquals(
            java.util.List.of(new ColumnReference("users", "email")),
            partial.readColumns()
        );
        assertEquals(false, partial.analysisComplete());
    }

    @Test
    void analyzesMultiActionAlterTableColumns() {
        var change = SqlImpactAnalyzer.analyze("""
            alter table users
              add column email text,
              drop column old_email,
              rename column name to display_name
            """);

        assertEquals(SqlStatementType.ALTER_TABLE, change.type());
        assertEquals(java.util.List.of("users"), change.writtenTables());
        assertEquals(
            java.util.List.of(
                new ColumnReference("users", "email"),
                new ColumnReference("users", "old_email"),
                new ColumnReference("users", "name"),
                new ColumnReference("users", "display_name")
            ),
            change.writtenColumns()
        );
        assertTrue(change.analysisComplete());
    }

    @Test
    void keepsUnknownAlterTableOperationsIncomplete() {
        var change = SqlImpactAnalyzer.analyze("""
            alter table users
              add column email text,
              add constraint users_email_unique unique (email)
            """);

        assertEquals(SqlStatementType.ALTER_TABLE, change.type());
        assertEquals(
            java.util.List.of(new ColumnReference("users", "email")),
            change.writtenColumns()
        );
        assertEquals(false, change.analysisComplete());
    }

    @Test
    void analyzesUpdatedAndReadColumnsAcrossJoin() {
        var change = SqlImpactAnalyzer.analyze("""
            update users u
            set status = r.default_status
            from roles r
            where u.role_id = r.id
            """);

        assertEquals(SqlStatementType.UPDATE, change.type());
        assertEquals(java.util.List.of("users"), change.writtenTables());
        assertEquals(java.util.List.of(new ColumnReference("users", "status")), change.writtenColumns());
        assertTrue(change.readTables().contains("users"));
        assertTrue(change.readTables().contains("roles"));
        assertTrue(change.readColumns().contains(new ColumnReference("users", "role_id")));
        assertTrue(change.readColumns().contains(new ColumnReference("roles", "default_status")));
        assertTrue(change.readColumns().contains(new ColumnReference("roles", "id")));
        assertEquals(false, change.analysisComplete());
    }

    @Test
    void analyzesSqliteInsertOrReplaceAsRuntimeWrite() {
        var change = SqlImpactAnalyzer.analyze("""
            insert or replace into audit_metadata (metadata_key, metadata_value)
            values ('trigger_user_name', new.name)
            """);

        assertEquals(SqlStatementType.INSERT, change.type());
        assertEquals(
            java.util.List.of(
                new ColumnReference("audit_metadata", "metadata_key"),
                new ColumnReference("audit_metadata", "metadata_value")
            ),
            change.runtimeWriteColumns()
        );
        assertTrue(change.analysisComplete());
    }

    @Test
    void analyzesTriggerObjectAndDependencies() {
        var change = SqlImpactAnalyzer.analyze("""
            create trigger users_audit after update of email on users
            begin
              insert into audit_log (user_id, user_email)
              values (new.id, new.email);

              update audit_log
              set previous_name = old.name
              where user_id = new.id;
            end;
            """);

        assertEquals(SqlStatementType.CREATE_TRIGGER, change.type());
        assertEquals("users_audit", change.schemaObject());
        assertTrue(change.readTables().contains("users"));
        assertTrue(change.readTables().contains("audit_log"));
        assertTrue(change.readColumns().contains(new ColumnReference("users", "id")));
        assertTrue(change.readColumns().contains(new ColumnReference("users", "email")));
        assertTrue(change.readColumns().contains(new ColumnReference("users", "name")));
        assertTrue(change.readColumns().contains(new ColumnReference("audit_log", "user_id")));
        assertTrue(change.readColumns().contains(new ColumnReference("audit_log", "user_email")));
        assertTrue(change.readColumns().contains(new ColumnReference("audit_log", "previous_name")));
        assertTrue(change.runtimeWriteColumns().contains(new ColumnReference("audit_log", "user_id")));
        assertTrue(change.runtimeWriteColumns().contains(new ColumnReference("audit_log", "user_email")));
        assertTrue(change.runtimeWriteColumns().contains(new ColumnReference("audit_log", "previous_name")));
        assertTrue(change.runtimeReadColumns().contains(new ColumnReference("users", "id")));
        assertTrue(change.runtimeReadColumns().contains(new ColumnReference("users", "email")));
        assertTrue(change.runtimeReadColumns().contains(new ColumnReference("users", "name")));
        assertEquals(false, change.analysisComplete());
    }

    @Test
    void propagatesIncompleteTriggerBodyAnalysis() {
        var change = SqlImpactAnalyzer.analyze("""
            create trigger users_audit after insert on users
            begin
              insert into audit_log (user_id, event_name)
              select new.id, r.default_event
              from roles r
              where r.id = new.role_id;
            end;
            """);

        assertEquals(SqlStatementType.CREATE_TRIGGER, change.type());
        assertTrue(change.runtimeWriteColumns().contains(new ColumnReference("audit_log", "user_id")));
        assertTrue(change.runtimeWriteColumns().contains(new ColumnReference("audit_log", "event_name")));
        assertEquals(false, change.analysisComplete());
    }

    @Test
    void analyzesPostgreSqlTriggerDeclarations() {
        var change = SqlImpactAnalyzer.analyze("""
            create constraint trigger users_audit
            after update of email on users
            deferrable initially deferred
            for each row
            execute function audit_users();
            """);

        assertEquals(SqlStatementType.CREATE_TRIGGER, change.type());
        assertEquals("users_audit", change.schemaObject());
        assertEquals(java.util.List.of("users"), change.readTables());
        assertEquals(
            java.util.List.of(new ColumnReference("users", "email")),
            change.readColumns()
        );
        assertEquals(java.util.List.of(), change.runtimeWriteColumns());
        assertEquals(java.util.List.of(new ColumnReference("users", "email")), change.runtimeReadColumns());
        assertEquals(false, change.analysisComplete());
    }

    @Test
    void analyzesQuotedTriggerPseudoRecordColumns() {
        var change = SqlImpactAnalyzer.analyze("""
            create trigger `users_audit` after update on `users`
            begin
              insert into `audit_log` (`user_id`, `user_email`)
              values (NEW.`id`, OLD.`email`);
            end;
            """);

        assertEquals(SqlStatementType.CREATE_TRIGGER, change.type());
        assertEquals("users_audit", change.schemaObject());
        assertTrue(change.readColumns().contains(new ColumnReference("users", "id")));
        assertTrue(change.readColumns().contains(new ColumnReference("users", "email")));
        assertTrue(change.readColumns().contains(new ColumnReference("audit_log", "user_id")));
        assertTrue(change.readColumns().contains(new ColumnReference("audit_log", "user_email")));
        assertTrue(change.runtimeWriteColumns().contains(new ColumnReference("audit_log", "user_id")));
        assertTrue(change.runtimeWriteColumns().contains(new ColumnReference("audit_log", "user_email")));
        assertTrue(change.runtimeReadColumns().contains(new ColumnReference("users", "id")));
        assertTrue(change.runtimeReadColumns().contains(new ColumnReference("users", "email")));
    }

    @Test
    void analyzesDroppedTriggers() {
        var change = SqlImpactAnalyzer.analyze("drop trigger if exists users_audit on users");

        assertEquals(SqlStatementType.DROP_TRIGGER, change.type());
        assertEquals("users_audit", change.schemaObject());
        assertEquals(java.util.List.of("users"), change.readTables());
        assertTrue(change.analysisComplete());
    }

    @Test
    void marksFunctionsAndProceduresAsIncompleteSchemaObjects() {
        var function = SqlImpactAnalyzer.analyze("""
            create or replace function user_display_name(user_name text)
            returns text
            language sql
            as $$
              select 'user:' || user_name;
            $$;
            """);
        var procedure = SqlImpactAnalyzer.analyze("""
            create procedure runway_touch_audit_metadata()
            language sql
            as $$
              select 1;
            $$;
            """);

        assertEquals(SqlStatementType.CREATE_FUNCTION, function.type());
        assertEquals("user_display_name", function.schemaObject());
        assertEquals(false, function.analysisComplete());
        assertEquals(SqlStatementType.CREATE_PROCEDURE, procedure.type());
        assertEquals("runway_touch_audit_metadata", procedure.schemaObject());
        assertEquals(false, procedure.analysisComplete());
    }
}
