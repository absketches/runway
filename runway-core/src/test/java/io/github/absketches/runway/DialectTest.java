package io.github.absketches.runway;

import io.github.absketches.runway.databases.mysql.MariaDbDialect;
import io.github.absketches.runway.databases.mysql.MySqlDialect;
import io.github.absketches.runway.databases.postgresql.PostgreSqlDialect;
import io.github.absketches.runway.databases.sqlite.SqliteDialect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialectTest {
    @Test
    void exposesExplicitDialectNames() {
        assertEquals("PostgreSQL", PostgreSqlDialect.INSTANCE.name());
        assertEquals("MySQL", MySqlDialect.INSTANCE.name());
        assertEquals("MariaDB", MariaDbDialect.INSTANCE.name());
        assertEquals("SQLite", SqliteDialect.INSTANCE.name());
    }

    @Test
    void historyTimestampsAreEngineWrittenIsoText() {
        assertIsoTextHistory(PostgreSqlDialect.INSTANCE);
        assertIsoTextHistory(MySqlDialect.INSTANCE);
        assertIsoTextHistory(MariaDbDialect.INSTANCE);
        assertIsoTextHistory(SqliteDialect.INSTANCE);
    }

    @Test
    void mariaDbInitiallySharesMySqlDialectBuildingBlocks() {
        assertEquals(
            MySqlDialect.INSTANCE.historyTableStatements().createIfMissing(),
            MariaDbDialect.INSTANCE.historyTableStatements().createIfMissing()
        );
        assertEquals(
            MySqlDialect.INSTANCE.historyTableStatements().insert(),
            MariaDbDialect.INSTANCE.historyTableStatements().insert()
        );
    }

    private static void assertIsoTextHistory(DatabaseDialect dialect) {
        String create = dialect.historyTableStatements().createIfMissing().toLowerCase();
        String insert = dialect.historyTableStatements().insert().toLowerCase();

        assertTrue(create.contains("installed_on"));
        assertTrue(create.contains("codegen_version"));
        assertFalse(create.contains("type varchar"));
        assertFalse(create.contains("type text"));
        assertFalse(create.contains("default now()"));
        assertFalse(create.contains("default current_timestamp"));
        assertTrue(insert.contains("checksum, installed_on,"));
        assertTrue(insert.contains("engine_version, codegen_version"));
        assertEquals(10, insert.chars().filter(character -> character == '?').count());
    }
}
