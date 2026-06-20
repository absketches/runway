package io.github.absketches.runway.codegen;

public final class RunwayCodegen {
    private RunwayCodegen() {
    }

    public static void main(String[] args) {
        try {
            CodegenOptions options = CodegenOptions.parse(args);
            new RunwayGenerator().generate(options);
        } catch (CodegenException | IllegalArgumentException e) {
            System.err.println("Runway code generation failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
