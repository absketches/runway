create function user_display_name(user_name varchar(255))
returns varchar(255)
deterministic
return concat('user:', user_name);
