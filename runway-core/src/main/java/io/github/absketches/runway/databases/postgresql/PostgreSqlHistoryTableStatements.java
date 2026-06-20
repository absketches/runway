package io.github.absketches.runway.databases.postgresql;

import io.github.absketches.runway.history.HistoryTableStatements;

final class PostgreSqlHistoryTableStatements implements HistoryTableStatements {
    @Override
    public String createIfMissing() {
        return """
            create table if not exists runway_schema_history (
                installed_rank integer primary key,
                version varchar(100),
                description varchar(200) not null,
                type varchar(30) not null,
                script varchar(300) not null,
                checksum varchar(80) not null,
                installed_by varchar(100) not null default current_user,
                installed_on varchar(40) not null,
                execution_time_ms bigint not null,
                success boolean not null,
                engine_version varchar(50) not null
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
