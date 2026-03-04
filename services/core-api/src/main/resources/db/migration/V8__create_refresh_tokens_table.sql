CREATE TABLE IF NOT EXISTS public.refresh_tokens (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    token_hash character varying(128) NOT NULL,
    replaced_by_token_hash character varying(128),
    expires_at timestamp(6) without time zone NOT NULL,
    last_used_at timestamp(6) without time zone,
    revoked boolean NOT NULL DEFAULT false,
    revoked_at timestamp(6) without time zone,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_created
    ON public.refresh_tokens USING btree (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_revoked
    ON public.refresh_tokens USING btree (user_id, revoked);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at
    ON public.refresh_tokens USING btree (expires_at);
