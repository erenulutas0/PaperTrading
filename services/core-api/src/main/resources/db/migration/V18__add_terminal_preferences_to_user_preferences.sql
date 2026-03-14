ALTER TABLE user_preferences
    ADD COLUMN terminal_market VARCHAR(32) NOT NULL DEFAULT 'CRYPTO',
    ADD COLUMN terminal_symbol VARCHAR(32) NOT NULL DEFAULT 'BTCUSDT',
    ADD COLUMN terminal_compare_symbols VARCHAR(512) NOT NULL DEFAULT '',
    ADD COLUMN terminal_compare_visible BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN terminal_range VARCHAR(16) NOT NULL DEFAULT '1D',
    ADD COLUMN terminal_interval VARCHAR(16) NOT NULL DEFAULT '1h',
    ADD COLUMN terminal_favorite_symbols VARCHAR(1024) NOT NULL DEFAULT '';
