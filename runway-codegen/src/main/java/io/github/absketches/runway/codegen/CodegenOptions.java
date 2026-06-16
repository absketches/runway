package io.github.absketches.runway.codegen;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

record CodegenOptions(
    Path input,
    Path output,
    String packageName,
    String className
) {
    static CodegenOptions parse(String[] args) {
        Map<String, String> values = parseValues(args);
        return new CodegenOptions(
            Path.of(required(values, "--input")),
            Path.of(values.getOrDefault("--output", "build/generated/sources/runway/main/java")),
            values.getOrDefault("--package", "io.github.absketches.runway.generated"),
            values.getOrDefault("--class-name", "GeneratedRunwayMigrations")
        );
    }

    private static Map<String, String> parseValues(String[] args) {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("Expected option name but found: " + args[i]);
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for " + args[i]);
            }
            values.put(args[i], args[i + 1]);
        }
        return values;
    }

    private static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required option " + key);
        }
        return value;
    }
}
