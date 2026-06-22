package io.github.absketches.runway.codegen.sql;

import io.github.absketches.runway.codegen.sql.dialects.MariaDbSplittingRules;
import io.github.absketches.runway.codegen.sql.dialects.MySqlSplittingRules;
import io.github.absketches.runway.codegen.sql.dialects.PostgreSqlSplittingRules;
import io.github.absketches.runway.codegen.sql.dialects.SqlDialectRules;
import io.github.absketches.runway.codegen.sql.dialects.SqliteSplittingRules;

import java.util.Locale;

public enum CodegenDialect {
    POSTGRESQL("PostgreSQL", new PostgreSqlSplittingRules()),
    MYSQL("MySQL", new MySqlSplittingRules()),
    MARIADB("MariaDB", new MariaDbSplittingRules()),
    SQLITE("SQLite", new SqliteSplittingRules());

    private final String runtimeName;
    private final SqlDialectRules splittingRules;

    CodegenDialect(String runtimeName, SqlDialectRules splittingRules) {
        this.runtimeName = runtimeName;
        this.splittingRules = splittingRules;
    }

    public String runtimeName() {
        return runtimeName;
    }

    public SqlDialectRules splittingRules() {
        return splittingRules;
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
