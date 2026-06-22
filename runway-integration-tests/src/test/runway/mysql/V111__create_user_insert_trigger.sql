delimiter //
create trigger users_insert_metadata
after insert on users
for each row
begin
    insert into audit_metadata (metadata_key, metadata_value)
    values ('trigger_user_name', new.name)
    on duplicate key update metadata_value = values(metadata_value);
end//
delimiter ;
