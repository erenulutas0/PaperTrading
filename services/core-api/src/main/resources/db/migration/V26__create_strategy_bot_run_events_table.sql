create table if not exists strategy_bot_run_events (
    id uuid primary key,
    strategy_bot_run_id uuid not null,
    sequence_no integer not null,
    open_time bigint not null,
    phase varchar(32) not null,
    action varchar(64) not null,
    close_price numeric(18,8) not null,
    cash_balance numeric(18,2) not null,
    position_quantity numeric(18,8) not null default 0,
    equity numeric(18,2) not null,
    matched_rules text not null default '[]',
    details text not null default '{}',
    created_at timestamp not null default now(),
    constraint fk_strategy_bot_run_events_run
        foreign key (strategy_bot_run_id) references strategy_bot_runs(id) on delete cascade
);

create unique index if not exists idx_strategy_bot_run_events_run_sequence
    on strategy_bot_run_events(strategy_bot_run_id, sequence_no);

create index if not exists idx_strategy_bot_run_events_run_open_time
    on strategy_bot_run_events(strategy_bot_run_id, open_time);
