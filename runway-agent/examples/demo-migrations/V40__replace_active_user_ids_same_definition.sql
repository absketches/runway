drop view if exists active_user_ids;

create or replace view active_user_ids as
select id
from users;
