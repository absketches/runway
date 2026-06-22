drop view if exists user_ids;

create view user_ids as
select id, name
from users;
