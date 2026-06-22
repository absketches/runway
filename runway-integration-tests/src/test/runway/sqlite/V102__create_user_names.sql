drop view if exists user_names;

create view user_names as
select id, name
from users;
