package io.github.absketches.runway.mysql;

public final class MariaDbDialect extends MySqlCompatibleDialect {
    public static final MariaDbDialect INSTANCE = new MariaDbDialect();

    private MariaDbDialect() {
        super("MariaDB");
    }
}
