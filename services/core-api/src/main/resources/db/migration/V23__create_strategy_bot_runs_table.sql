create table if not exists strategy_bot_runs (
    id uuid primary key,
    strategy_bot_id uuid not null,
    user_id uuid not null,
    linked_portfolio_id uuid null,
    run_mode varchar(32) not null,
    status varchar(32) not null,
    requested_initial_capital numeric(18,2),
    effective_initial_capital numeric(18,2) not null,
    from_date date,
    to_date date,
    compiled_entry_rules text not null default '{}',
    compiled_exit_rules text not null default '{}',
    summary text not null default '{}',
    error_message varchar(1000),
    requested_at timestamp not null default now(),
    started_at timestamp,
    completed_at timestamp,
    constraint fk_strategy_bot_runs_bot
        foreign key (strategy_bot_id) references strategy_bots(id),
    constraint fk_strategy_bot_runs_linked_portfolio
        foreign key (linked_portfolio_id) references portfolios(id),
    constraint chk_strategy_bot_runs_mode
        check (run_mode in ('BACKTEST', 'FORWARD_TEST')),
    constraint chk_strategy_bot_runs_status
        check (status in ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

create index if not exists idx_strategy_bot_runs_bot_requested
    on strategy_bot_runs(strategy_bot_id, requested_at desc);

create index if not exists idx_strategy_bot_runs_user_requested
    on strategy_bot_runs(user_id, requested_at desc);
