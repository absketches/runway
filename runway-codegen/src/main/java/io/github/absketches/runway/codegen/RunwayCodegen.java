package io.github.absketches.runway.codegen;

import java.nio.file.Path;

public final class RunwayCodegen {
    private RunwayCodegen() {
    }

    public static void main(String[] args) {
        try {
            CodegenOptions options = CodegenOptions.parse(args);
            Path generated = new RunwayGenerator().generate(
                options.input(),
                options.output(),
                options.packageName(),
                options.className()
            );
            System.out.println("Generated " + generated);
        } catch (CodegenException | IllegalArgumentException e) {
            System.err.println("Runway code generation failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
