ALTER TABLE public.interactions
    DROP CONSTRAINT IF EXISTS interactions_target_type_check;

ALTER TABLE public.interactions
    ADD CONSTRAINT interactions_target_type_check CHECK (((target_type)::text = ANY (
        (ARRAY[
            'PORTFOLIO'::character varying,
            'ANALYSIS_POST'::character varying,
            'COMMENT'::character varying
        ])::text[]
    )));
