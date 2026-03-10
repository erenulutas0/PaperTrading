CREATE TABLE IF NOT EXISTS public.audit_logs (
    id uuid NOT NULL,
    actor_id uuid,
    action_type character varying(100) NOT NULL,
    resource_type character varying(100) NOT NULL,
    resource_id uuid,
    request_id character varying(100),
    ip_address character varying(255),
    user_agent character varying(1024),
    request_method character varying(16),
    request_path character varying(255),
    details text,
    created_at timestamp(6) without time zone,
    CONSTRAINT audit_logs_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_created_desc
    ON public.audit_logs USING btree (actor_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_resource_created_desc
    ON public.audit_logs USING btree (resource_type, resource_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_request_id
    ON public.audit_logs USING btree (request_id);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created_desc
    ON public.audit_logs USING btree (created_at DESC);
