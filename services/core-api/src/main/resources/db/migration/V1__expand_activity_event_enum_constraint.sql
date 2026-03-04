DO
$$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'activity_events'
    ) THEN
        IF EXISTS (
            SELECT 1
            FROM pg_constraint c
            JOIN pg_class t ON c.conrelid = t.oid
            JOIN pg_namespace n ON t.relnamespace = n.oid
            WHERE n.nspname = 'public'
              AND t.relname = 'activity_events'
              AND c.conname = 'activity_events_event_type_check'
        ) THEN
            EXECUTE 'ALTER TABLE public.activity_events DROP CONSTRAINT activity_events_event_type_check';
        END IF;

        EXECUTE $constraint$
            ALTER TABLE public.activity_events
                ADD CONSTRAINT activity_events_event_type_check
                CHECK (event_type IN (
                    'FOLLOW',
                    'POST_CREATED',
                    'PORTFOLIO_PUBLISHED',
                    'PORTFOLIO_JOINED',
                    'PORTFOLIO_LEFT',
                    'PORTFOLIO_LIKED',
                    'PORTFOLIO_COMMENTED',
                    'POST_LIKED',
                    'POST_COMMENTED',
                    'TRADE_EXECUTED',
                    'POST_DELETED'
                ))
        $constraint$;
    END IF;
END
$$;
