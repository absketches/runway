package io.github.absketches.runway.codegen;

import java.io.PrintStream;

public final class RunwayCodegen {
    private RunwayCodegen() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (helpRequested(args)) {
            out.println(helpText());
            return 0;
        }

        try {
            CodegenOptions options = CodegenOptions.parse(args);
            new RunwayGenerator().generate(options);
            return 0;
        } catch (CodegenException | IllegalArgumentException e) {
            err.println("Runway code generation failed: " + e.getMessage());
            return 1;
        }
    }

    private static boolean helpRequested(String[] args) {
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    static String helpText() {
        return """
            Runway codegen

            Usage:
              java -cp runway-codegen.jar io.github.absketches.runway.codegen.RunwayCodegen \\
                --input <directory> \\
                --dialect <postgresql|mysql|mariadb|sqlite> \\
                [options]

            Required:
              --input <directory>       Directory containing versioned SQL migration files.
              --dialect <dialect>       SQL dialect used to split migration files.

            Options:
              --output <directory>      Generated Java source directory.
                                      Default: build/generated/sources/runway/main/java
              --resource-output <dir>   Generated SQL resource directory.
                                      Default: build/generated/resources/runway/main
              --package <name>          Java package for the generated migration catalog.
                                      Default: io.github.absketches.runway.generated
              --class-name <name>       Generated migration catalog class name.
                                      Default: GeneratedRunwayMigrations
              --impact-output <file>    Generate a standalone HTML impact analysis report.
              --analysis-only           Generate only the migration history analysis report.
              -h, --help                Show this help.
            """;
    }
}
