create table if not exists market_chart_notes (
    id uuid primary key,
    user_id uuid not null,
    market varchar(32) not null,
    symbol varchar(32) not null,
    body varchar(2000) not null,
    created_at timestamp not null default current_timestamp
);

create index if not exists idx_market_chart_notes_user_market_symbol_created
    on market_chart_notes (user_id, market, symbol, created_at desc);
