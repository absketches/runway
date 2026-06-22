create procedure runway_touch_audit_metadata()
language plpgsql
as $$
begin
    insert into audit_metadata (metadata_key, metadata_value)
    values ('procedure_created', 'yes')
    on conflict (metadata_key) do update
    set metadata_value = excluded.metadata_value;
end;
$$;
