package io.github.absketches.runway.codegen;

import java.util.List;

final class JavaSourceWriter {
    private static final String CHAIN_INDENT = "            ";
    private static final String ARGUMENT_INDENT = "                ";

    private JavaSourceWriter() {
    }

    static String write(String packageName, String className, List<ParsedMigration> migrations) {
        StringBuilder entries = new StringBuilder();

        for (ParsedMigration migration : migrations) {
            entries.append("\n").append(entry(migration));
        }

        return """
            package %s;

            import io.github.absketches.runway.MigrationRegistry;
            import io.github.absketches.runway.Migrations;

            public final class %s {
                private %s() {
                }

                public static MigrationRegistry registry() {
                    return Migrations.builder()
            %s
                        .build();
                }
            }
            """.formatted(packageName, className, className, entries);
    }

    private static String entry(ParsedMigration migration) {
        return switch (migration.type()) {
            case VERSIONED -> versionedEntry(migration);
            case REPEATABLE -> repeatableEntry(migration);
        };
    }

    private static String versionedEntry(ParsedMigration migration) {
        return entry(
            "versioned",
            quote(migration.version()),
            quote(migration.description()),
            quote(migration.checksum()),
            textBlock(migration.sql())
        );
    }

    private static String repeatableEntry(ParsedMigration migration) {
        return entry(
            "repeatable",
            quote(migration.description()),
            quote(migration.checksum()),
            textBlock(migration.sql())
        );
    }

    private static String entry(String builderMethod, String... arguments) {
        return CHAIN_INDENT + "." + builderMethod + "(\n"
            + arguments(arguments)
            + CHAIN_INDENT + ")\n";
    }

    private static String arguments(String... arguments) {
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < arguments.length; i++) {
            source.append(ARGUMENT_INDENT).append(arguments[i]);
            if (i < arguments.length - 1) {
                source.append(",");
            }
            source.append("\n");
        }
        return source.toString();
    }

    private static String quote(String value) {
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\"";
    }

    private static String textBlock(String sql) {
        String escaped = sql.replace("\\", "\\\\").replace("\"\"\"", "\"\"\\\"");
        if (!escaped.endsWith("\n")) {
            escaped += "\n";
        }
        return "\"\"\"\n" + indent(escaped, ARGUMENT_INDENT) + ARGUMENT_INDENT + "\"\"\"";
    }

    private static String indent(String value, String indentation) {
        String[] lines = value.split("\n", -1);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length - 1; i++) {
            builder.append(indentation).append(lines[i]).append("\n");
        }
        return builder.toString();
    }
}
