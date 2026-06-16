package io.github.absketches.runway.codegen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunwayGeneratorTest {
    @TempDir
    Path tempDir;

    @Test
    void generatesCompilableRegistry() throws Exception {
        Path input = tempDir.resolve("runway");
        Path output = tempDir.resolve("generated");
        Files.createDirectories(input);
        Files.writeString(input.resolve("V1__create_users.sql"), "create table users (name text default '\"\"\"');\r\n");
        Files.writeString(input.resolve("R__refresh_user_view.sql"), "select $$;$$;\n");

        Path generated = new RunwayGenerator().generate(input, output, "io.github.absketches.runway.generated", "GeneratedRunwayMigrations");

        String source = Files.readString(generated);
        assertTrue(source.contains("sha256:"));
        assertTrue(source.contains(".versioned("));
        assertTrue(source.contains(".repeatable("));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, generated.toString()));
    }

    @Test
    void rejectsDuplicateVersions() throws Exception {
        Path input = tempDir.resolve("runway");
        Path output = tempDir.resolve("generated");
        Files.createDirectories(input);
        Files.writeString(input.resolve("V1__first.sql"), "select 1;\n");
        Files.writeString(input.resolve("V1__second.sql"), "select 2;\n");

        assertThrows(CodegenException.class, () -> new RunwayGenerator().generate(input, output, "io.github.absketches.runway.generated", "GeneratedRunwayMigrations"));
    }
}
