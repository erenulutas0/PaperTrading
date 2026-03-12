CREATE TABLE trust_score_snapshots (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    trust_score double precision NOT NULL,
    win_rate double precision NOT NULL,
    resolved_prediction_count bigint NOT NULL DEFAULT 0,
    resolved_trade_count bigint NOT NULL DEFAULT 0,
    portfolio_count integer NOT NULL DEFAULT 0,
    captured_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trust_score_snapshots_user_captured_at
    ON trust_score_snapshots (user_id, captured_at DESC);
