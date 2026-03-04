DO
$$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'notifications'
    ) THEN
        IF EXISTS (
            SELECT 1
            FROM pg_constraint c
            JOIN pg_class t ON c.conrelid = t.oid
            JOIN pg_namespace n ON t.relnamespace = n.oid
            WHERE n.nspname = 'public'
              AND t.relname = 'notifications'
              AND c.conname = 'notifications_type_check'
        ) THEN
            EXECUTE 'ALTER TABLE public.notifications DROP CONSTRAINT notifications_type_check';
        END IF;

        EXECUTE $constraint$
            ALTER TABLE public.notifications
                ADD CONSTRAINT notifications_type_check
                CHECK (type IN (
                    'FOLLOW',
                    'PORTFOLIO_LIKE',
                    'POST_LIKE',
                    'PORTFOLIO_COMMENT',
                    'POST_COMMENT',
                    'PORTFOLIO_JOINED',
                    'PRICE_ALERT'
                ))
        $constraint$;
    END IF;
END
$$;
