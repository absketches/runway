package io.github.absketches.runway.codegen;

import io.github.absketches.runway.codegen.sql.CodegenDialect;

import javax.lang.model.SourceVersion;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

record CodegenOptions(
    Path input,
    Path sourceOutput,
    Path resourceOutput,
    String packageName,
    String className,
    CodegenDialect dialect,
    Path impactOutput
) {
    private static final Set<String> SUPPORTED_OPTIONS = Set.of(
        "--input",
        "--output",
        "--resource-output",
        "--package",
        "--class-name",
        "--dialect",
        "--impact-output"
    );

    CodegenOptions {
        if (!SourceVersion.isName(packageName)) {
            throw new IllegalArgumentException("Invalid Java package name: " + packageName);
        }
        if (!SourceVersion.isIdentifier(className) || SourceVersion.isKeyword(className)) {
            throw new IllegalArgumentException("Invalid Java class name: " + className);
        }
    }

    static CodegenOptions parse(String[] args) {
        Map<String, String> values = parseValues(args);
        return new CodegenOptions(
            Path.of(required(values, "--input")),
            Path.of(values.getOrDefault("--output", "build/generated/sources/runway/main/java")),
            Path.of(values.getOrDefault("--resource-output", "build/generated/resources/runway/main")),
            values.getOrDefault("--package", "io.github.absketches.runway.generated"),
            values.getOrDefault("--class-name", "GeneratedRunwayMigrations"),
            CodegenDialect.parse(required(values, "--dialect")),
            optionalPath(values.get("--impact-output"))
        );
    }

    private static Map<String, String> parseValues(String[] args) {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("Expected option name but found: " + args[i]);
            }
            if (!SUPPORTED_OPTIONS.contains(args[i])) {
                throw new IllegalArgumentException("Unknown option " + args[i]);
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

    private static Path optionalPath(String value) {
        return value == null || value.isBlank() ? null : Path.of(value);
    }
}
