drop view if exists audit_severities;

create or replace view audit_severities as
select "severity", count(*)::integer as "severity_count"
from "audit_log"
group by "severity";
