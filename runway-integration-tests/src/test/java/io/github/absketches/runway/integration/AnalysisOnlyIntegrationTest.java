package io.github.absketches.runway.integration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisOnlyIntegrationTest {
    @Test
    void generateImpactReportWithoutRuntimeCatalogOutput() throws Exception {
        Path target = Path.of("target");
        Path report = target.resolve("generated-test-resources/analysis-only/runway-impact.html");

        assertTrue(Files.exists(report));

        String html = Files.readString(report);
        assertTrue(html.contains("Runway Impact Report"));
        assertTrue(html.contains("V1__create_users.sql"));
        assertTrue(html.contains("Migration Files"));
        assertTrue(html.contains("Schema points"));

        assertFalse(Files.exists(target.resolve("analysis-only/generated-test-sources/runway")));
        assertFalse(Files.exists(target.resolve("analysis-only/generated-test-resources/runway")));
    }
}
