package io.github.absketches.runway.databases.sqlite;

import io.github.absketches.runway.history.HistoryTableStatements;

final class SqliteHistoryTableStatements implements HistoryTableStatements {
    @Override
    public String createIfMissing() {
        return """
            create table if not exists runway_schema_history (
                installed_rank integer not null primary key,
                version text,
                description text not null,
                type text not null,
                script text not null,
                checksum text not null,
                installed_by text not null default 'unknown',
                installed_on text not null,
                execution_time_ms integer not null,
                success integer not null,
                engine_version text not null
            )
            """;
    }

    @Override
    public String selectAll() {
        return """
            select installed_rank, version, description, type, script, checksum, installed_on,
                   execution_time_ms, success, engine_version
            from runway_schema_history
            order by installed_rank
            """;
    }

    @Override
    public String nextInstalledRank() {
        return "select coalesce(max(installed_rank), 0) + 1 from runway_schema_history";
    }

    @Override
    public String insert() {
        return """
            insert into runway_schema_history (
                installed_rank, version, description, type, script, checksum, installed_on,
                execution_time_ms, success, engine_version
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    }
}
