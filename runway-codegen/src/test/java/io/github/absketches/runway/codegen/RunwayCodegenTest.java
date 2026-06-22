package io.github.absketches.runway.codegen;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunwayCodegenTest {
    @Test
    void printsHelpWithoutRequiredGenerationOptions() {
        CommandResult result = run("--help");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("Usage:"));
        assertTrue(result.output().contains("--input <directory>"));
        assertTrue(result.output().contains("--dialect <postgresql|mysql|mariadb|sqlite>"));
        assertEquals("", result.error());
    }

    @Test
    void supportsShortHelpOption() {
        CommandResult result = run("-h");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("Runway codegen"));
        assertEquals("", result.error());
    }

    private static CommandResult run(String... args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        int exitCode = RunwayCodegen.run(
            args,
            new PrintStream(output, true, StandardCharsets.UTF_8),
            new PrintStream(error, true, StandardCharsets.UTF_8)
        );
        return new CommandResult(
            exitCode,
            output.toString(StandardCharsets.UTF_8),
            error.toString(StandardCharsets.UTF_8)
        );
    }

    private record CommandResult(int exitCode, String output, String error) {
    }
}
