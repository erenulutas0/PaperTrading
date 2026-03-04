DO
$$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'interactions'
    ) THEN
        -- Clean legacy duplicate likes before adding uniqueness.
        DELETE FROM interactions i
        USING interactions j
        WHERE i.interaction_type = 'LIKE'
          AND j.interaction_type = 'LIKE'
          AND i.actor_id = j.actor_id
          AND i.target_type = j.target_type
          AND i.target_id = j.target_id
          AND i.id > j.id;

        CREATE UNIQUE INDEX IF NOT EXISTS ux_interactions_like_actor_target
            ON interactions (actor_id, target_type, target_id)
            WHERE interaction_type = 'LIKE';

        CREATE INDEX IF NOT EXISTS idx_interactions_target_type_id_type_created
            ON interactions (target_type, target_id, interaction_type, created_at DESC);
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'activity_events'
    ) THEN
        CREATE INDEX IF NOT EXISTS idx_activity_actor_created_desc
            ON activity_events (actor_id, created_at DESC);
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'notifications'
    ) THEN
        CREATE INDEX IF NOT EXISTS idx_notifications_user_created_desc
            ON notifications (user_id, created_at DESC);
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'follows'
    ) THEN
        CREATE INDEX IF NOT EXISTS idx_follows_following_created
            ON follows (following_id, created_at DESC);
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'analysis_posts'
    )
    AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'analysis_posts'
          AND column_name = 'created_at'
    )
    AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'analysis_posts'
          AND column_name = 'deleted'
    ) THEN
        CREATE INDEX IF NOT EXISTS idx_analysis_posts_deleted_created
            ON analysis_posts (deleted, created_at DESC);
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'analysis_posts'
    )
    AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'analysis_posts'
          AND column_name = 'author_id'
    )
    AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'analysis_posts'
          AND column_name = 'deleted'
    )
    AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'analysis_posts'
          AND column_name = 'created_at'
    ) THEN
        CREATE INDEX IF NOT EXISTS idx_analysis_posts_author_deleted_created
            ON analysis_posts (author_id, deleted, created_at DESC);
    END IF;
END
$$;
