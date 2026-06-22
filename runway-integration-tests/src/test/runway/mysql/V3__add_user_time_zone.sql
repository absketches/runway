alter table `users`
add column `time_zone` varchar(64) not null default 'UTC';
