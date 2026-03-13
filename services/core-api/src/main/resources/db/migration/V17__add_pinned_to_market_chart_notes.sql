ALTER TABLE market_chart_notes
    ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_market_chart_notes_user_market_symbol_pinned_created
    ON market_chart_notes (user_id, market, symbol, pinned DESC, created_at DESC);
