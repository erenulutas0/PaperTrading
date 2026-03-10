-- Bootstrap schema for fresh databases so Hibernate can run in validate mode.
-- This migration is idempotent and safe against existing deployments.

CREATE TABLE IF NOT EXISTS public.users (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    email character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    updated_at timestamp(6) without time zone,
    username character varying(255) NOT NULL,
    avatar_url character varying(255),
    bio character varying(500),
    display_name character varying(255),
    verified boolean DEFAULT false NOT NULL,
    follower_count integer DEFAULT 0 NOT NULL,
    following_count integer DEFAULT 0 NOT NULL,
    trust_score double precision DEFAULT 50.0,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT uk6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email),
    CONSTRAINT ukr43af9ap4edm43mmtq01oddj6 UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS public.portfolios (
    id uuid NOT NULL,
    balance numeric(38,2) NOT NULL,
    created_at timestamp(6) without time zone,
    name character varying(255) NOT NULL,
    owner_id character varying(255) NOT NULL,
    updated_at timestamp(6) without time zone,
    description character varying(500),
    visibility character varying(255) DEFAULT 'PRIVATE'::character varying NOT NULL,
    version bigint,
    CONSTRAINT portfolios_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.activity_events (
    id uuid NOT NULL,
    actor_id uuid NOT NULL,
    actor_username character varying(255),
    created_at timestamp(6) without time zone,
    event_type character varying(255) NOT NULL,
    metadata character varying(2000),
    target_id uuid NOT NULL,
    target_label character varying(255),
    target_type character varying(255) NOT NULL,
    CONSTRAINT activity_events_pkey PRIMARY KEY (id),
    CONSTRAINT activity_events_event_type_check CHECK (((event_type)::text = ANY (
        (ARRAY[
            'FOLLOW'::character varying,
            'POST_CREATED'::character varying,
            'PORTFOLIO_PUBLISHED'::character varying,
            'PORTFOLIO_JOINED'::character varying,
            'PORTFOLIO_LEFT'::character varying,
            'PORTFOLIO_LIKED'::character varying,
            'PORTFOLIO_COMMENTED'::character varying,
            'POST_LIKED'::character varying,
            'POST_COMMENTED'::character varying,
            'TRADE_EXECUTED'::character varying,
            'POST_DELETED'::character varying
        ])::text[]
    ))),
    CONSTRAINT activity_events_target_type_check CHECK (((target_type)::text = ANY (
        (ARRAY[
            'USER'::character varying,
            'PORTFOLIO'::character varying,
            'POST'::character varying
        ])::text[]
    )))
);

CREATE TABLE IF NOT EXISTS public.analysis_posts (
    id uuid NOT NULL,
    author_id uuid NOT NULL,
    content character varying(5000) NOT NULL,
    created_at timestamp(6) without time zone,
    deleted boolean NOT NULL,
    deleted_at timestamp(6) without time zone,
    direction character varying(255) NOT NULL,
    instrument_symbol character varying(255) NOT NULL,
    outcome character varying(255) NOT NULL,
    outcome_resolved_at timestamp(6) without time zone,
    price_at_creation numeric(20,8) NOT NULL,
    price_at_resolution numeric(20,8),
    stop_price numeric(20,8),
    target_date timestamp(6) without time zone,
    target_price numeric(20,8),
    timeframe character varying(10),
    title character varying(200) NOT NULL,
    CONSTRAINT analysis_posts_pkey PRIMARY KEY (id),
    CONSTRAINT analysis_posts_direction_check CHECK (((direction)::text = ANY (
        (ARRAY[
            'BULLISH'::character varying,
            'BEARISH'::character varying,
            'NEUTRAL'::character varying
        ])::text[]
    ))),
    CONSTRAINT analysis_posts_outcome_check CHECK (((outcome)::text = ANY (
        (ARRAY[
            'PENDING'::character varying,
            'HIT'::character varying,
            'MISSED'::character varying,
            'EXPIRED'::character varying
        ])::text[]
    )))
);

CREATE TABLE IF NOT EXISTS public.badges (
    id uuid NOT NULL,
    description character varying(500),
    earned_at timestamp(6) without time zone,
    icon character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    tournament_id uuid,
    user_id uuid NOT NULL,
    CONSTRAINT badges_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.follows (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    follower_id uuid NOT NULL,
    following_id uuid NOT NULL,
    CONSTRAINT follows_pkey PRIMARY KEY (id),
    CONSTRAINT uk4faelgsm2rxl2jf3iyjy981ro UNIQUE (follower_id, following_id)
);

CREATE TABLE IF NOT EXISTS public.interactions (
    id uuid NOT NULL,
    actor_id uuid NOT NULL,
    content character varying(1000),
    created_at timestamp(6) without time zone,
    interaction_type character varying(20) NOT NULL,
    target_id uuid NOT NULL,
    target_type character varying(20) NOT NULL,
    CONSTRAINT interactions_pkey PRIMARY KEY (id),
    CONSTRAINT interactions_interaction_type_check CHECK (((interaction_type)::text = ANY (
        (ARRAY[
            'LIKE'::character varying,
            'COMMENT'::character varying
        ])::text[]
    ))),
    CONSTRAINT interactions_target_type_check CHECK (((target_type)::text = ANY (
        (ARRAY[
            'PORTFOLIO'::character varying,
            'ANALYSIS_POST'::character varying
        ])::text[]
    )))
);

CREATE TABLE IF NOT EXISTS public.notifications (
    id uuid NOT NULL,
    actor_id uuid,
    actor_username character varying(50),
    created_at timestamp(6) without time zone,
    is_read boolean NOT NULL,
    reference_id uuid,
    reference_label character varying(255),
    type character varying(30) NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT notifications_pkey PRIMARY KEY (id),
    CONSTRAINT notifications_type_check CHECK (((type)::text = ANY (
        (ARRAY[
            'FOLLOW'::character varying,
            'PORTFOLIO_LIKE'::character varying,
            'POST_LIKE'::character varying,
            'PORTFOLIO_COMMENT'::character varying,
            'POST_COMMENT'::character varying,
            'PORTFOLIO_JOINED'::character varying,
            'PRICE_ALERT'::character varying
        ])::text[]
    )))
);

CREATE TABLE IF NOT EXISTS public.portfolio_items (
    id uuid NOT NULL,
    average_price numeric(38,2) NOT NULL,
    quantity numeric(38,2) NOT NULL,
    symbol character varying(255) NOT NULL,
    portfolio_id uuid NOT NULL,
    leverage integer,
    side character varying(255),
    version bigint,
    CONSTRAINT portfolio_items_pkey PRIMARY KEY (id),
    CONSTRAINT fkmkd3xsfvbvnj0k7akoleedr40 FOREIGN KEY (portfolio_id) REFERENCES public.portfolios(id)
);

CREATE TABLE IF NOT EXISTS public.portfolio_participants (
    id uuid NOT NULL,
    cloned_portfolio_id uuid,
    joined_at timestamp(6) without time zone,
    portfolio_id uuid NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT portfolio_participants_pkey PRIMARY KEY (id),
    CONSTRAINT ukcadh807akivqsfeu9qyb9qmhc UNIQUE (portfolio_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.portfolio_snapshots (
    id uuid NOT NULL,
    portfolio_id uuid NOT NULL,
    "timestamp" timestamp(6) without time zone NOT NULL,
    total_equity numeric(38,2) NOT NULL,
    CONSTRAINT portfolio_snapshots_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.tournaments (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    description character varying(500),
    ends_at timestamp(6) without time zone NOT NULL,
    name character varying(255) NOT NULL,
    starting_balance numeric(38,2) NOT NULL,
    starts_at timestamp(6) without time zone NOT NULL,
    status character varying(255) NOT NULL,
    CONSTRAINT tournaments_pkey PRIMARY KEY (id),
    CONSTRAINT tournaments_status_check CHECK (((status)::text = ANY (
        (ARRAY[
            'UPCOMING'::character varying,
            'ACTIVE'::character varying,
            'COMPLETED'::character varying
        ])::text[]
    )))
);

CREATE TABLE IF NOT EXISTS public.tournament_participants (
    id uuid NOT NULL,
    final_rank integer,
    final_return_percent double precision,
    joined_at timestamp(6) without time zone,
    portfolio_id uuid NOT NULL,
    tournament_id uuid NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT tournament_participants_pkey PRIMARY KEY (id),
    CONSTRAINT ukqhimhuf01nlo47h5h0j1q8b6e UNIQUE (tournament_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.trade_activities (
    id uuid NOT NULL,
    portfolio_id uuid NOT NULL,
    price numeric(38,2) NOT NULL,
    quantity numeric(38,2) NOT NULL,
    realized_pnl numeric(38,2),
    side character varying(255) NOT NULL,
    symbol character varying(255) NOT NULL,
    "timestamp" timestamp(6) without time zone NOT NULL,
    type character varying(255) NOT NULL,
    CONSTRAINT trade_activities_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.watchlists (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    name character varying(255) NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT watchlists_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.watchlist_items (
    id uuid NOT NULL,
    added_at timestamp(6) without time zone,
    alert_above_triggered boolean,
    alert_below_triggered boolean,
    alert_price_above numeric(38,2),
    alert_price_below numeric(38,2),
    notes character varying(255),
    symbol character varying(255) NOT NULL,
    watchlist_id uuid NOT NULL,
    CONSTRAINT watchlist_items_pkey PRIMARY KEY (id),
    CONSTRAINT fkbukcexbiyx6jdcyksenct5fn1 FOREIGN KEY (watchlist_id) REFERENCES public.watchlists(id)
);

CREATE INDEX IF NOT EXISTS idx_activity_actor
    ON public.activity_events USING btree (actor_id);

CREATE INDEX IF NOT EXISTS idx_activity_created
    ON public.activity_events USING btree (created_at);

CREATE INDEX IF NOT EXISTS idx_activity_actor_created_desc
    ON public.activity_events USING btree (actor_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_analysispost_author
    ON public.analysis_posts USING btree (author_id);

CREATE INDEX IF NOT EXISTS idx_analysispost_outcome
    ON public.analysis_posts USING btree (outcome);

CREATE INDEX IF NOT EXISTS idx_analysispost_created
    ON public.analysis_posts USING btree (created_at);

CREATE INDEX IF NOT EXISTS idx_analysispost_deleted
    ON public.analysis_posts USING btree (deleted);

CREATE INDEX IF NOT EXISTS idx_analysispost_instrument
    ON public.analysis_posts USING btree (instrument_symbol);

CREATE INDEX IF NOT EXISTS idx_analysis_posts_deleted_created
    ON public.analysis_posts USING btree (deleted, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_analysis_posts_author_deleted_created
    ON public.analysis_posts USING btree (author_id, deleted, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_follows_following_created
    ON public.follows USING btree (following_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_interaction_actor
    ON public.interactions USING btree (actor_id);

CREATE INDEX IF NOT EXISTS idx_interaction_target
    ON public.interactions USING btree (target_type, target_id);

CREATE INDEX IF NOT EXISTS idx_interactions_target_type_id_type_created
    ON public.interactions USING btree (target_type, target_id, interaction_type, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS ux_interactions_like_actor_target
    ON public.interactions USING btree (actor_id, target_type, target_id)
    WHERE ((interaction_type)::text = 'LIKE'::text);

CREATE INDEX IF NOT EXISTS idx_notification_user
    ON public.notifications USING btree (user_id, is_read);

CREATE INDEX IF NOT EXISTS idx_notifications_user_created_desc
    ON public.notifications USING btree (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_snapshot_portfolio_time
    ON public.portfolio_snapshots USING btree (portfolio_id, "timestamp");

CREATE INDEX IF NOT EXISTS idx_trade_portfolio_time
    ON public.trade_activities USING btree (portfolio_id, "timestamp");
