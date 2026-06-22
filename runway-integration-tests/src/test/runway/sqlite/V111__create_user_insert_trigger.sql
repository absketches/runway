create trigger users_insert_metadata
after insert on users
begin
    insert or replace into audit_metadata (metadata_key, metadata_value)
    values ('trigger_user_name', new.name);
end;
