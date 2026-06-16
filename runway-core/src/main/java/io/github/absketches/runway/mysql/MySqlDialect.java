package io.github.absketches.runway.mysql;

public final class MySqlDialect extends MySqlCompatibleDialect {
    public static final MySqlDialect INSTANCE = new MySqlDialect();

    private MySqlDialect() {
        super("MySQL");
    }
}
