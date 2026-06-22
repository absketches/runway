delimiter //
create procedure runway_touch_audit_metadata()
begin
    insert into audit_metadata (metadata_key, metadata_value)
    values ('procedure_created', 'yes')
    on duplicate key update metadata_value = values(metadata_value);
end//
delimiter ;
