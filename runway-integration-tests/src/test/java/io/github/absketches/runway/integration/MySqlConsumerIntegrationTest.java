package io.github.absketches.runway.integration;

import ch.vorburger.exec.ManagedProcessException;
import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;
import io.github.absketches.runway.databases.mysql.MySqlDialect;
import io.github.absketches.runway.integration.generated.mysql.GeneratedRunwayMigrations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MySqlConsumerIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void consumerProjectGeneratesRegistryAndMigratesMySqlCompatibleDatabase() throws Exception {
        assumeFalse(
            mariaDb4jUnsupportedOnCurrentMachine(),
            "MariaDB4j does not provide a macOS ARM embedded database binary"
        );

        DBConfigurationBuilder config = DBConfigurationBuilder.newBuilder();
        config.setPort(0);
        config.setDataDir(tempDir.resolve("mariadb").toFile());

        DB database = embeddedDatabase(config);
        try {
            database.start();
            database.createDB("runway");

            ConsumerIntegrationSupport.assertGeneratedMigrationsApply(
                ConsumerIntegrationSupport.dataSource(
                    config.getURL("runway").replace("jdbc:mysql:", "jdbc:mariadb:"),
                    "root",
                    ""
                ),
                MySqlDialect.INSTANCE,
                GeneratedRunwayMigrations.registry(),
                """
                    select count(*)
                    from information_schema.statistics
                    where table_schema = database()
                      and table_name = 'audit_log'
                      and index_name = 'audit_log_event_name_idx'
                    """,
                expectedVersions(),
                "select user_display_name('Ada')",
                "call runway_touch_audit_metadata()"
            );
        } finally {
            database.stop();
        }
    }

    private static List<String> expectedVersions() {
        return List.of(
            "1", "2", "3", "4", "26", "79", "90", "91", "92", "101", "102", "103", "104", "105", "106", "107",
            "108", "109", "110", "111"
        );
    }

    private static DB embeddedDatabase(DBConfigurationBuilder config) throws ManagedProcessException {
        try {
            return DB.newEmbeddedDB(config.build());
        } catch (ManagedProcessException e) {
            if (unsupportedEmbeddedBinary(e)) {
                assumeTrue(false, "MariaDB4j embedded database binary is not supported on this machine");
            }
            throw e;
        }
    }

    private static boolean mariaDb4jUnsupportedOnCurrentMachine() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        return os.contains("mac") && (arch.equals("aarch64") || arch.equals("arm64"));
    }

    private static boolean unsupportedEmbeddedBinary(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                && (message.contains("Bad CPU type in executable")
                    || message.contains("Exec format error")
                    || message.contains("cannot execute binary file"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
