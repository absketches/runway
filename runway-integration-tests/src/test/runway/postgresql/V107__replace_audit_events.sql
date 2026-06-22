drop view if exists audit_events;

create or replace view audit_events as
select "id", "event_name"
from "audit_log";
