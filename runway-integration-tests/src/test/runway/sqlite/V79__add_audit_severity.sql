alter table audit_log
add column severity varchar(20) not null default 'INFO';
