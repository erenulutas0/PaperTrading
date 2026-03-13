create table if not exists watchlist_alert_events (
    id uuid primary key,
    watchlist_item_id uuid not null references watchlist_items(id) on delete cascade,
    user_id uuid not null,
    symbol varchar(32) not null,
    direction varchar(16) not null,
    threshold_price numeric(19, 8) not null,
    triggered_price numeric(19, 8) not null,
    message varchar(512),
    triggered_at timestamp not null default current_timestamp
);

create index if not exists idx_watchlist_alert_events_item_triggered_at
    on watchlist_alert_events (watchlist_item_id, triggered_at desc);

create index if not exists idx_watchlist_alert_events_user_triggered_at
    on watchlist_alert_events (user_id, triggered_at desc);
