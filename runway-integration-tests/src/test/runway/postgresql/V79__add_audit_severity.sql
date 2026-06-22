alter table "audit_log"
add column "severity" text not null default 'INFO' check ("severity" in ('INFO', 'WARN', 'ERROR'));
