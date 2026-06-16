package io.github.absketches.runway;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationPlanServiceTest {
    private final MigrationPlanService planner = new MigrationPlanService();

    @Test
    void plansPendingVersionedMigrations() {
        MigrationPlan plan = planner.plan(
            Migrations.builder()
                .versioned("1", "create users", "sha256:a", "create table users(id bigint);")
                .versioned("2", "add email", "sha256:b", "alter table users add column email text;")
                .build()
                .migrations(),
            List.of(applied("1", "create users", "sha256:a"))
        );

        assertTrue(plan.valid());
        assertEquals(List.of("add email"), plan.pending().stream().map(MigrationDefinition::description).toList());
    }

    @Test
    void detectsChecksumMismatch() {
        MigrationPlan plan = planner.plan(
            Migrations.builder()
                .versioned("1", "create users", "sha256:new", "create table users(id bigint);")
                .build()
                .migrations(),
            List.of(applied("1", "create users", "sha256:old"))
        );

        assertFalse(plan.valid());
        assertEquals(ValidationErrorCode.CHECKSUM_MISMATCH, plan.validationErrors().getFirst().code());
    }

    @Test
    void rerunsChangedRepeatableMigration() {
        MigrationDefinition repeatable = Migrations.builder()
            .repeatable("refresh view", "sha256:new", "select 1;")
            .build()
            .migrations()
            .getFirst();
        AppliedMigration applied = new AppliedMigration(
            1,
            MigrationType.REPEATABLE,
            null,
            "refresh view",
            "R__refresh_view.sql",
            "sha256:old",
            Instant.now(),
            1,
            true,
            "test"
        );

        MigrationPlan plan = planner.plan(List.of(repeatable), List.of(applied));

        assertTrue(plan.valid());
        assertEquals(List.of(repeatable), plan.pending());
    }

    private static AppliedMigration applied(String version, String description, String checksum) {
        return new AppliedMigration(
            1,
            MigrationType.VERSIONED,
            MigrationVersion.of(version),
            description,
            "V" + version + "__" + description.replace(' ', '_') + ".sql",
            checksum,
            Instant.now(),
            1,
            true,
            "test"
        );
    }
}
