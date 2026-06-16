package io.github.absketches.runway;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeCompatibilityTest {
    private static final List<String> FORBIDDEN = List.of(
        "java.lang.reflect",
        "java.util.ServiceLoader",
        "Class.forName",
        "ClassLoader.loadClass",
        "ClassLoader.getResource",
        "Class.getResource",
        "Class.getResourceAsStream",
        "java.beans"
    );

    @Test
    void runtimeDoesNotUseReflectionOrResourceDiscovery() throws Exception {
        Path sourceRoot = Path.of("src/main/java");
        try (var paths = Files.walk(sourceRoot)) {
            List<String> violations = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .flatMap(path -> violations(path).stream())
                .toList();

            assertTrue(violations.isEmpty(), "Forbidden native-compatibility references: " + violations);
        }
    }

    private static List<String> violations(Path path) {
        try {
            String source = Files.readString(path);
            return FORBIDDEN.stream()
                .filter(source::contains)
                .map(pattern -> path + " contains " + pattern)
                .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to scan " + path, e);
        }
    }
}
