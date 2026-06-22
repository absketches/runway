alter table "users"
add column "nickname" text;

alter table "users"
add column "preferred_language" varchar(16) not null default 'en';
