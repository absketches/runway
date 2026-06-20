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
            "create unique index users_email_idx on users (email, tenant_id)"
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
}
