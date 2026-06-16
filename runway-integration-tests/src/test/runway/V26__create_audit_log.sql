create table audit_log (
    id integer primary key,
    event_name text not null
);

create index audit_log_event_name_idx
on audit_log (event_name);

create table audit_metadata (
    metadata_key text primary key,
    metadata_value text not null
);

insert into audit_metadata (metadata_key, metadata_value)
values ('created_by', 'runway');
