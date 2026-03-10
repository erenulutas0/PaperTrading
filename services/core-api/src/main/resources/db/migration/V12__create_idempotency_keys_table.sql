CREATE TABLE IF NOT EXISTS public.idempotency_keys (
    id uuid NOT NULL,
    actor_scope character varying(100) NOT NULL,
    idempotency_key character varying(255) NOT NULL,
    request_method character varying(16) NOT NULL,
    request_path character varying(255) NOT NULL,
    request_hash character varying(128) NOT NULL,
    status character varying(20) NOT NULL,
    response_status integer,
    response_content_type character varying(255),
    response_body text,
    created_at timestamp(6) without time zone,
    completed_at timestamp(6) without time zone,
    expires_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT idempotency_keys_pkey PRIMARY KEY (id),
    CONSTRAINT uk_idempotency_actor_scope_key UNIQUE (actor_scope, idempotency_key),
    CONSTRAINT idempotency_keys_status_check CHECK (((status)::text = ANY (
        (ARRAY[
            'IN_PROGRESS'::character varying,
            'COMPLETED'::character varying
        ])::text[]
    )))
);

CREATE INDEX IF NOT EXISTS idx_idempotency_keys_expires_at
    ON public.idempotency_keys USING btree (expires_at);

CREATE INDEX IF NOT EXISTS idx_idempotency_keys_created_at
    ON public.idempotency_keys USING btree (created_at DESC);
