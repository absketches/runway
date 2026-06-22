drop view if exists user_ids;

create or replace view user_ids as
select "id", "name"
from "users";
