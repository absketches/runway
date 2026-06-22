create table if not exists `users` (
    `id` int unsigned not null auto_increment,
    `name` varchar(255) character set utf8mb4 not null,
    `created_at` datetime(6) not null default current_timestamp(6),
    primary key (id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;
