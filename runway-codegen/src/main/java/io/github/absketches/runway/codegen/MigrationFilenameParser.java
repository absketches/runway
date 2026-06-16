package io.github.absketches.runway.codegen;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MigrationFilenameParser {
    private static final Pattern VERSIONED = Pattern.compile("^V([0-9][0-9._-]*)__([A-Za-z0-9][A-Za-z0-9_ -]*)\\.sql$");
    private static final Pattern REPEATABLE = Pattern.compile("^R__([A-Za-z0-9][A-Za-z0-9_ -]*)\\.sql$");

    private MigrationFilenameParser() {
    }

    static ParsedMigrationName parse(Path path) {
        String fileName = path.getFileName().toString();
        Matcher versioned = VERSIONED.matcher(fileName);
        if (versioned.matches()) {
            String version = versioned.group(1);
            validateVersion(version);
            return new ParsedMigrationName(CodegenMigrationType.VERSIONED, version, normalizeDescription(versioned.group(2)));
        }
        Matcher repeatable = REPEATABLE.matcher(fileName);
        if (repeatable.matches()) {
            return new ParsedMigrationName(CodegenMigrationType.REPEATABLE, null, normalizeDescription(repeatable.group(1)));
        }
        throw new CodegenException("Invalid migration filename: " + fileName);
    }

    private static String normalizeDescription(String raw) {
        return raw.replace('_', ' ').trim().replaceAll(" +", " ");
    }

    private static void validateVersion(String version) {
        String[] parts = version.split("[._-]");
        for (String part : parts) {
            if (part.isBlank() || !part.chars().allMatch(Character::isDigit)) {
                throw new CodegenException("Migration version must contain numeric parts only: " + version);
            }
            Integer.parseInt(part);
        }
    }
}
