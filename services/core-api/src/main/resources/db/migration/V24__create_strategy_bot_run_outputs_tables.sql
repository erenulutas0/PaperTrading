create table if not exists strategy_bot_run_fills (
    id uuid primary key,
    strategy_bot_run_id uuid not null,
    sequence_no integer not null,
    side varchar(16) not null,
    open_time bigint not null,
    price numeric(18,8) not null,
    quantity numeric(18,8) not null,
    realized_pnl numeric(18,2) not null default 0,
    matched_rules text not null default '[]',
    created_at timestamp not null default now(),
    constraint fk_strategy_bot_run_fills_run
        foreign key (strategy_bot_run_id) references strategy_bot_runs(id) on delete cascade,
    constraint chk_strategy_bot_run_fills_side
        check (side in ('ENTRY', 'EXIT'))
);

create unique index if not exists idx_strategy_bot_run_fills_run_sequence
    on strategy_bot_run_fills(strategy_bot_run_id, sequence_no);

create table if not exists strategy_bot_run_equity_points (
    id uuid primary key,
    strategy_bot_run_id uuid not null,
    sequence_no integer not null,
    open_time bigint not null,
    close_price numeric(18,8) not null,
    equity numeric(18,2) not null,
    created_at timestamp not null default now(),
    constraint fk_strategy_bot_run_equity_points_run
        foreign key (strategy_bot_run_id) references strategy_bot_runs(id) on delete cascade
);

create unique index if not exists idx_strategy_bot_run_equity_points_run_sequence
    on strategy_bot_run_equity_points(strategy_bot_run_id, sequence_no);
