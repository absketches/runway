alter table `audit_log`
add column `severity` enum('INFO', 'WARN', 'ERROR') not null default 'INFO';
