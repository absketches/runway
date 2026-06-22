create function runway_user_insert_metadata()
returns trigger
language plpgsql
as $$
begin
    insert into audit_metadata (metadata_key, metadata_value)
    values ('trigger_user_name', new.name)
    on conflict (metadata_key) do update
    set metadata_value = excluded.metadata_value;
    return new;
end;
$$;

create trigger users_insert_metadata
after insert on users
for each row
execute function runway_user_insert_metadata();
