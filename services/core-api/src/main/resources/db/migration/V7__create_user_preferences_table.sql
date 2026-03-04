CREATE TABLE IF NOT EXISTS public.user_preferences (
    user_id uuid NOT NULL,
    dashboard_period character varying(8) NOT NULL DEFAULT '1D',
    dashboard_sort_by character varying(32) NOT NULL DEFAULT 'RETURN_PERCENTAGE',
    dashboard_direction character varying(8) NOT NULL DEFAULT 'DESC',
    public_sort_by character varying(32) NOT NULL DEFAULT 'RETURN_PERCENTAGE',
    public_direction character varying(8) NOT NULL DEFAULT 'DESC',
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    CONSTRAINT user_preferences_pkey PRIMARY KEY (user_id),
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_preferences_updated
    ON public.user_preferences USING btree (updated_at DESC);

