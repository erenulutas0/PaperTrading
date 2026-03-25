ALTER TABLE public.user_preferences
    ADD COLUMN IF NOT EXISTS notification_in_app_social boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS notification_in_app_watchlist boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS notification_in_app_tournaments boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS notification_digest_cadence character varying(16) NOT NULL DEFAULT 'INSTANT',
    ADD COLUMN IF NOT EXISTS notification_quiet_hours_enabled boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS notification_quiet_hours_start character varying(5) NOT NULL DEFAULT '22:00',
    ADD COLUMN IF NOT EXISTS notification_quiet_hours_end character varying(5) NOT NULL DEFAULT '08:00';
