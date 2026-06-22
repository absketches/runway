alter table users
add column nickname text;

alter table users
add column preferred_language text default 'en';
