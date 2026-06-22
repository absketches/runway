create table `audit_log` (
    `id` bigint unsigned not null auto_increment,
    `event_name` varchar(191) not null,
    `created_at` timestamp(6) not null default current_timestamp(6),
    primary key (id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create index audit_log_event_name_idx
on `audit_log` (`event_name`) using btree;

create table `audit_metadata` (
    `metadata_key` varchar(191) not null,
    `metadata_value` varchar(255) character set utf8mb4 not null,
    primary key (metadata_key)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

insert into audit_metadata (metadata_key, metadata_value)
values ('created_by', 'runway');
