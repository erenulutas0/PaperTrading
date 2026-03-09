UPDATE public.trade_activities
SET realized_pnl = 0
WHERE realized_pnl IS NULL
  AND UPPER(type) LIKE 'BUY%';
