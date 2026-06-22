drop view if exists user_names;

create or replace view user_names as
select `id`, `name`
from `users`;
