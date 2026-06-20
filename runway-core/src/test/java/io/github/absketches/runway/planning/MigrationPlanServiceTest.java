package io.github.absketches.runway.planning;

import io.github.absketches.runway.MigrationDefinition;
import io.github.absketches.runway.MigrationVersion;
import io.github.absketches.runway.Migrations;
import io.github.absketches.runway.ValidationErrorCode;
import io.github.absketches.runway.history.AppliedMigration;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationPlanServiceTest {
    @Test
    void plansPendingVersionedMigrations() {
        MigrationPlan plan = MigrationPlanService.plan(
            Migrations.builder()
                .versioned("1", "create users", "sha256:a", MigrationPlanServiceTest::resource, statements())
                .versioned("2", "add email", "sha256:b", MigrationPlanServiceTest::resource, statements())
                .build()
                .migrations(),
            List.of(applied("1", "create users", "sha256:a"))
        );

        assertTrue(plan.valid());
        assertEquals(List.of("add email"), plan.pending().stream().map(MigrationDefinition::description).toList());
    }

    @Test
    void detectsChecksumMismatch() {
        MigrationPlan plan = MigrationPlanService.plan(
            Migrations.builder()
                .versioned("1", "create users", "sha256:new", MigrationPlanServiceTest::resource, statements())
                .build()
                .migrations(),
            List.of(applied("1", "create users", "sha256:old"))
        );

        assertFalse(plan.valid());
        assertEquals(ValidationErrorCode.CHECKSUM_MISMATCH, plan.validationErrors().getFirst().code());
    }

    @Test
    void versionedIdentityDoesNotDependOnDescription() {
        MigrationPlan plan = MigrationPlanService.plan(
            Migrations.builder()
                .versioned("1", "renamed description", "sha256:a", MigrationPlanServiceTest::resource, statements())
                .build()
                .migrations(),
            List.of(applied("1", "original description", "sha256:a"))
        );

        assertTrue(plan.valid());
        assertEquals(List.of(), plan.pending());
    }

    private static AppliedMigration applied(String version, String description, String checksum) {
        return new AppliedMigration(
            1,
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

    private static List<String> statements() {
        return List.of("/statement.sql");
    }

    private static ByteArrayInputStream resource(String ignored) {
        return new ByteArrayInputStream("select 1;\n".getBytes(StandardCharsets.UTF_8));
    }
}
