drop view if exists audit_severities;

create view audit_severities as
select severity, count(*) as severity_count
from audit_log
group by severity;
