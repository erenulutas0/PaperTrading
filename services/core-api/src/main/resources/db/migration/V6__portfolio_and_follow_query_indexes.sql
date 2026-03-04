DO
$$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'portfolios'
    ) THEN
        CREATE INDEX IF NOT EXISTS idx_portfolios_owner_id
            ON portfolios (owner_id);

        CREATE INDEX IF NOT EXISTS idx_portfolios_visibility_created
            ON portfolios (visibility, created_at DESC);

        CREATE INDEX IF NOT EXISTS idx_portfolios_owner_visibility_created
            ON portfolios (owner_id, visibility, created_at DESC);
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'follows'
    ) THEN
        CREATE INDEX IF NOT EXISTS idx_follows_follower_created
            ON follows (follower_id, created_at DESC);
    END IF;
END
$$;
