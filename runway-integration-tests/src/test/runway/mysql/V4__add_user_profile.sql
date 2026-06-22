alter table `users`
add column `nickname` varchar(255) after `time_zone`;

alter table `users`
add column `preferred_language` varchar(16) not null default 'en' after `nickname`;
