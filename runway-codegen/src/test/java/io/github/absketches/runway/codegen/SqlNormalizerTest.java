package io.github.absketches.runway.codegen;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlNormalizerTest {
    @Test
    void removesBomAndNormalizesLineEndingsWithoutTrimmingSql() {
        byte[] bytes = ("\uFEFFselect 1;\r\n-- keep spaces  \rselect 2;\n").getBytes(StandardCharsets.UTF_8);

        String normalized = SqlNormalizer.normalize(bytes, "V1__demo.sql");

        assertEquals("select 1;\n-- keep spaces  \nselect 2;\n", normalized);
    }

    @Test
    void rejectsMalformedUtf8() {
        byte[] bytes = {(byte) 0xC3, 0x28};

        assertThrows(CodegenException.class, () -> SqlNormalizer.normalize(bytes, "V1__bad.sql"));
    }
}
