create or replace view audit_summary as
select `severity`, count(*) as `event_count`
from `audit_log`
group by `severity`;
