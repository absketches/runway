create view user_ids as
select id
from users;

create view audit_event_names as
select event_name
from audit_log;
