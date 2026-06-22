create view user_name_filter as
select id
from users
where name is not null;
