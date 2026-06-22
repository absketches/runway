package io.github.absketches.runway.integration;

import io.github.absketches.runway.databases.postgresql.PostgreSqlDialect;
import io.github.absketches.runway.integration.generated.postgresql.GeneratedRunwayMigrations;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;

import java.util.List;

class PostgreSqlConsumerIntegrationTest {
    @Test
    void consumerProjectGeneratesRegistryAndMigratesPostgreSqlDatabase() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            ConsumerIntegrationSupport.assertGeneratedMigrationsApply(
                postgres.getPostgresDatabase(),
                PostgreSqlDialect.INSTANCE,
                GeneratedRunwayMigrations.registry(),
                """
                    select count(*)
                    from pg_indexes
                    where schemaname = current_schema()
                      and tablename = 'audit_log'
                      and indexname = 'audit_log_event_name_idx'
                    """,
                expectedVersions(),
                "select user_display_name('Ada')",
                "call runway_touch_audit_metadata()"
            );
        }
    }

    private static List<String> expectedVersions() {
        return List.of(
            "1", "2", "3", "4", "26", "79", "90", "91", "92", "101", "102", "103", "104", "105", "106", "107",
            "108", "109", "110", "111"
        );
    }
}
