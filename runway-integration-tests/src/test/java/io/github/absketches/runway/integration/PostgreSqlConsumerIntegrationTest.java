package io.github.absketches.runway.integration;

import io.github.absketches.runway.databases.postgresql.PostgreSqlDialect;
import io.github.absketches.runway.integration.generated.postgresql.GeneratedRunwayMigrations;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;

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
                    """
            );
        }
    }
}
