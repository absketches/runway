create function user_display_name(user_name text)
returns text
language sql
immutable
as $$
    select 'user:' || user_name;
$$;
