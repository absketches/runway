package io.github.absketches.runway.codegen;

import io.github.absketches.runway.codegen.sql.CodegenDialect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CodegenOptionsTest {
    @Test
    void parsesRequiredOptions() {
        CodegenOptions options = CodegenOptions.parse(new String[] {
            "--input", "migrations",
            "--dialect", "postgresql"
        });

        assertEquals(CodegenDialect.POSTGRESQL, options.dialect());
    }

    @Test
    void requiresDialect() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CodegenOptions.parse(new String[] {"--input", "migrations"})
        );
    }

    @Test
    void rejectsRemovedInputCharsetOption() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CodegenOptions.parse(new String[] {
                "--input", "migrations",
                "--dialect", "postgresql",
                "--input-charset", "ISO-8859-1"
            })
        );
    }

    @Test
    void rejectsInvalidGeneratedPackageName() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CodegenOptions.parse(new String[] {
                "--input", "migrations",
                "--dialect", "postgresql",
                "--package", "io.github.123bad"
            })
        );
    }

    @Test
    void rejectsInvalidGeneratedClassName() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CodegenOptions.parse(new String[] {
                "--input", "migrations",
                "--dialect", "postgresql",
                "--class-name", "class"
            })
        );
    }
}
