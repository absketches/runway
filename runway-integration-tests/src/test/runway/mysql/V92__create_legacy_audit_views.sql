create view audit_events as
select event_name
from audit_log;

create view audit_severities as
select severity
from audit_log;
