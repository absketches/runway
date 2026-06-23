package io.github.absketches.runway.codegen;

import io.github.absketches.runway.codegen.sql.CodegenDialect;

import javax.lang.model.SourceVersion;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

record CodegenOptions(
    Path input,
    Path sourceOutput,
    Path resourceOutput,
    String packageName,
    String className,
    CodegenDialect dialect,
    Path impactOutput,
    boolean analysisOnly
) {
    private static final Set<String> VALUE_OPTIONS = Set.of(
        "--input",
        "--output",
        "--resource-output",
        "--package",
        "--class-name",
        "--dialect",
        "--impact-output"
    );
    private static final Set<String> FLAG_OPTIONS = Set.of(
        "--analysis-only"
    );

    CodegenOptions {
        if (analysisOnly && impactOutput == null) {
            throw new IllegalArgumentException("Analysis-only mode requires --impact-output");
        }
        if (!analysisOnly && !SourceVersion.isName(packageName)) {
            throw new IllegalArgumentException("Invalid Java package name: " + packageName);
        }
        if (!analysisOnly && (!SourceVersion.isIdentifier(className) || SourceVersion.isKeyword(className))) {
            throw new IllegalArgumentException("Invalid Java class name: " + className);
        }
    }

    static CodegenOptions parse(String[] args) {
        ParsedArguments parsed = parseArguments(args);
        Map<String, String> values = parsed.values();
        return new CodegenOptions(
            Path.of(required(values, "--input")),
            Path.of(values.getOrDefault("--output", "build/generated/sources/runway/main/java")),
            Path.of(values.getOrDefault("--resource-output", "build/generated/resources/runway/main")),
            values.getOrDefault("--package", "io.github.absketches.runway.generated"),
            values.getOrDefault("--class-name", "GeneratedRunwayMigrations"),
            CodegenDialect.parse(required(values, "--dialect")),
            optionalPath(values.get("--impact-output")),
            parsed.flags().contains("--analysis-only")
        );
    }

    private static ParsedArguments parseArguments(String[] args) {
        Map<String, String> values = new HashMap<>();
        Set<String> flags = new HashSet<>();
        for (int i = 0; i < args.length;) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("Expected option name but found: " + args[i]);
            }
            if (FLAG_OPTIONS.contains(args[i])) {
                flags.add(args[i]);
                i++;
                continue;
            }
            if (!VALUE_OPTIONS.contains(args[i])) {
                throw new IllegalArgumentException("Unknown option " + args[i]);
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for " + args[i]);
            }
            values.put(args[i], args[i + 1]);
            i += 2;
        }
        return new ParsedArguments(Map.copyOf(values), Set.copyOf(flags));
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

    private record ParsedArguments(Map<String, String> values, Set<String> flags) {
    }
}
