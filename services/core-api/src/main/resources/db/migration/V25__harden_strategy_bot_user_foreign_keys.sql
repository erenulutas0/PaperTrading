-- Remove orphan strategy-bot ownership rows before enforcing strict user foreign keys.
delete from public.strategy_bot_runs r
where not exists (
        select 1
        from public.users u
        where u.id = r.user_id
    )
   or not exists (
        select 1
        from public.strategy_bots b
        join public.users u on u.id = b.user_id
        where b.id = r.strategy_bot_id
    );

delete from public.strategy_bots b
where not exists (
    select 1
    from public.users u
    where u.id = b.user_id
);

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'fk_strategy_bots_user'
    ) then
        alter table public.strategy_bots
            add constraint fk_strategy_bots_user
                foreign key (user_id) references public.users(id) on delete cascade;
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'fk_strategy_bot_runs_user'
    ) then
        alter table public.strategy_bot_runs
            add constraint fk_strategy_bot_runs_user
                foreign key (user_id) references public.users(id) on delete cascade;
    end if;
end $$;
