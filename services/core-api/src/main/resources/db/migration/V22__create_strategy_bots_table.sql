create table if not exists strategy_bots (
    id uuid primary key,
    user_id uuid not null,
    linked_portfolio_id uuid null,
    name varchar(120) not null,
    description varchar(1000),
    bot_kind varchar(32) not null,
    status varchar(32) not null,
    market varchar(32) not null,
    symbol varchar(32) not null,
    timeframe varchar(16) not null,
    entry_rules text not null default '{}',
    exit_rules text not null default '{}',
    max_position_size_percent numeric(10,2) not null,
    stop_loss_percent numeric(10,2),
    take_profit_percent numeric(10,2),
    cooldown_minutes integer not null default 0,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    constraint fk_strategy_bots_linked_portfolio
        foreign key (linked_portfolio_id) references portfolios(id),
    constraint chk_strategy_bots_bot_kind
        check (bot_kind in ('RULE_BASED')),
    constraint chk_strategy_bots_status
        check (status in ('DRAFT', 'READY', 'ARCHIVED')),
    constraint chk_strategy_bots_max_position
        check (max_position_size_percent > 0 and max_position_size_percent <= 100),
    constraint chk_strategy_bots_stop_loss
        check (stop_loss_percent is null or (stop_loss_percent > 0 and stop_loss_percent <= 100)),
    constraint chk_strategy_bots_take_profit
        check (take_profit_percent is null or take_profit_percent > 0),
    constraint chk_strategy_bots_cooldown
        check (cooldown_minutes >= 0)
);

create index if not exists idx_strategy_bots_user_updated
    on strategy_bots(user_id, updated_at desc, created_at desc);

create index if not exists idx_strategy_bots_portfolio
    on strategy_bots(linked_portfolio_id);
