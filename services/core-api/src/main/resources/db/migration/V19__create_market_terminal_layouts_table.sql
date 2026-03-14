create table if not exists market_terminal_layouts (
    id uuid primary key,
    user_id uuid not null,
    name varchar(80) not null,
    watchlist_id uuid null,
    market varchar(32) not null,
    symbol varchar(32) not null,
    compare_symbols varchar(512) not null default '',
    compare_visible boolean not null default true,
    chart_range varchar(16) not null,
    chart_interval varchar(16) not null,
    favorite_symbols varchar(1024) not null default '',
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create index if not exists idx_market_terminal_layouts_user_updated
    on market_terminal_layouts(user_id, updated_at desc, created_at desc);
