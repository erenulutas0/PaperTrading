CREATE TABLE strategy_bot_materialized_summaries (
    strategy_bot_id uuid PRIMARY KEY REFERENCES strategy_bots(id) ON DELETE CASCADE,
    total_runs integer NOT NULL DEFAULT 0,
    backtest_runs integer NOT NULL DEFAULT 0,
    forward_test_runs integer NOT NULL DEFAULT 0,
    completed_runs integer NOT NULL DEFAULT 0,
    running_runs integer NOT NULL DEFAULT 0,
    failed_runs integer NOT NULL DEFAULT 0,
    cancelled_runs integer NOT NULL DEFAULT 0,
    compiler_ready_runs integer NOT NULL DEFAULT 0,
    positive_completed_runs integer NOT NULL DEFAULT 0,
    negative_completed_runs integer NOT NULL DEFAULT 0,
    total_simulated_trades integer NOT NULL DEFAULT 0,
    avg_return_percent double precision,
    avg_net_pnl double precision,
    avg_max_drawdown_percent double precision,
    avg_win_rate double precision,
    avg_trade_count double precision,
    avg_profit_factor double precision,
    avg_expectancy_per_trade double precision,
    latest_requested_at timestamp,
    best_run_id uuid,
    worst_run_id uuid,
    latest_completed_run_id uuid,
    active_forward_run_id uuid,
    recent_run_ids text NOT NULL DEFAULT '[]',
    entry_driver_totals text NOT NULL DEFAULT '{}',
    exit_driver_totals text NOT NULL DEFAULT '{}',
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_strategy_bot_materialized_summaries_latest_requested_at
    ON strategy_bot_materialized_summaries (latest_requested_at DESC);
