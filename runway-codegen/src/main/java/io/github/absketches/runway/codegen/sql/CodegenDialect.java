package io.github.absketches.runway.codegen.sql;

import java.util.Locale;

public enum CodegenDialect {
    POSTGRESQL("PostgreSQL"),
    MYSQL("MySQL"),
    MARIADB("MariaDB"),
    SQLITE("SQLite");

    private final String runtimeName;

    CodegenDialect(String runtimeName) {
        this.runtimeName = runtimeName;
    }

    public String runtimeName() {
        return runtimeName;
    }

    public static CodegenDialect parse(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "postgres", "postgresql" -> POSTGRESQL;
            case "mysql" -> MYSQL;
            case "mariadb" -> MARIADB;
            case "sqlite" -> SQLITE;
            default -> throw new IllegalArgumentException("Unsupported dialect: " + value);
        };
    }
}
