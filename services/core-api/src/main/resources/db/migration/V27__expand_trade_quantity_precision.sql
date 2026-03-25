ALTER TABLE public.portfolio_items
    ALTER COLUMN quantity TYPE numeric(38,8);

ALTER TABLE public.trade_activities
    ALTER COLUMN quantity TYPE numeric(38,8);
