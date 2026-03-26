'use client';

import Link from 'next/link';
import { useParams, useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useMemo, useState } from 'react';
import { apiFetch } from '../../../../../lib/api-client';

type StrategyBotRunFill = {
  id: string;
  sequenceNo: number;
  side: 'ENTRY' | 'EXIT';
  openTime: number;
  price: number;
  quantity: number;
  realizedPnl: number;
  matchedRules: string[];
};

type StrategyBotRunEquityPoint = {
  id: string;
  sequenceNo: number;
  openTime: number;
  closePrice: number;
  equity: number;
};

type PublicStrategyBotRunDetail = {
  strategyBotId: string;
  runId: string;
  botName: string;
  botDescription?: string | null;
  botStatus: string;
  market: string;
  symbol: string;
  timeframe: string;
  linkedPortfolioId?: string | null;
  linkedPortfolioName?: string | null;
  ownerId?: string | null;
  ownerUsername?: string | null;
  ownerDisplayName?: string | null;
  ownerAvatarUrl?: string | null;
  ownerTrustScore?: number | null;
  runMode: string;
  status: string;
  requestedInitialCapital?: number | null;
  effectiveInitialCapital?: number | null;
  fromDate?: string | null;
  toDate?: string | null;
  compiledEntryRules?: unknown;
  compiledExitRules?: unknown;
  summary?: Record<string, unknown> | null;
  errorMessage?: string | null;
  requestedAt?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  fills: StrategyBotRunFill[];
  equityCurve: StrategyBotRunEquityPoint[];
};

function fmtPercent(value?: number | null): string {
  if (value === undefined || value === null || Number.isNaN(value)) return '-';
  return `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`;
}

function fmtCurrency(value?: number | null): string {
  if (value === undefined || value === null || Number.isNaN(value)) return '-';
  return `$${value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function fmtDate(value?: string | null): string {
  if (!value) return '-';
  return new Date(value).toLocaleString();
}

function fmtEpoch(value?: number | null): string {
  if (value === undefined || value === null || Number.isNaN(value)) return '-';
  return new Date(value).toLocaleString();
}

function pretty(value: unknown): string {
  try {
    return JSON.stringify(value ?? {}, null, 2);
  } catch {
    return '{}';
  }
}

function toNullableNumber(value: unknown): number | null {
  if (value === undefined || value === null) return null;
  const parsed = Number(value);
  return Number.isNaN(parsed) ? null : parsed;
}

function buildSparkline(points: StrategyBotRunEquityPoint[]): string {
  if (points.length < 2) return '';
  const width = 320;
  const height = 96;
  const padding = 8;
  const values = points.map((point) => point.equity);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  return points
    .map((point, index) => {
      const x = padding + (index * (width - padding * 2)) / (points.length - 1);
      const y = height - padding - ((point.equity - min) / range) * (height - padding * 2);
      return `${x},${y}`;
    })
    .join(' ');
}

function PublicStrategyBotRunPageContent() {
  const params = useParams<{ botId: string; runId: string }>();
  const searchParams = useSearchParams();
  const botId = params.botId;
  const runId = params.runId;
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [detail, setDetail] = useState<PublicStrategyBotRunDetail | null>(null);
  const [copyingLink, setCopyingLink] = useState(false);
  const [exportingFormat, setExportingFormat] = useState<'csv' | 'json' | null>(null);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await apiFetch(`/api/v1/strategy-bots/discover/${botId}/runs/${runId}`);
        if (!res.ok) {
          throw new Error(`Failed to load public strategy bot run: ${res.status}`);
        }
        const payload = await res.json();
        setDetail(payload as PublicStrategyBotRunDetail);
      } catch (loadError) {
        console.error(loadError);
        setDetail(null);
        setError(loadError instanceof Error ? loadError.message : 'Failed to load public strategy bot run');
      } finally {
        setLoading(false);
      }
    };

    if (botId && runId) {
      void load();
    }
  }, [botId, runId]);

  const detailHref = useMemo(() => {
    const params = new URLSearchParams(searchParams.toString());
    const query = params.toString();
    return query ? `/bots/${botId}?${query}` : `/bots/${botId}`;
  }, [botId, searchParams]);

  const boardHref = useMemo(() => {
    const params = new URLSearchParams();
    const q = searchParams.get('q');
    const sortBy = searchParams.get('sortBy');
    const direction = searchParams.get('direction');
    const runMode = searchParams.get('runMode');
    const lookback = searchParams.get('lookbackDays');
    const page = searchParams.get('page');
    if (q) params.set('q', q);
    if (sortBy) params.set('sortBy', sortBy);
    if (direction) params.set('direction', direction);
    if (runMode) params.set('runMode', runMode);
    if (lookback) params.set('lookbackDays', lookback);
    if (page) params.set('page', page);
    const query = params.toString();
    return query ? `/bots?${query}` : '/bots';
  }, [searchParams]);

  const sparkline = useMemo(() => buildSparkline(detail?.equityCurve ?? []), [detail?.equityCurve]);

  async function copyRunLink() {
    setCopyingLink(true);
    try {
      await navigator.clipboard.writeText(window.location.href);
    } catch (copyError) {
      console.error(copyError);
    } finally {
      setCopyingLink(false);
    }
  }

  async function exportRun(format: 'csv' | 'json') {
    setExportingFormat(format);
    try {
      const res = await apiFetch(`/api/v1/strategy-bots/discover/${botId}/runs/${runId}/export?format=${format}`);
      if (!res.ok) {
        throw new Error(`Failed to export public strategy bot run: ${res.status}`);
      }
      const blob = await res.blob();
      const objectUrl = window.URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = format === 'csv' ? `public-strategy-bot-run-${runId}.csv` : `public-strategy-bot-run-${runId}.json`;
      anchor.click();
      window.URL.revokeObjectURL(objectUrl);
    } catch (exportError) {
      console.error(exportError);
    } finally {
      setExportingFormat(null);
    }
  }

  return (
    <div className="min-h-screen bg-black text-white">
      <nav className="sticky top-0 z-50 flex items-center justify-between border-b border-white/10 bg-black/50 px-6 py-4 backdrop-blur-md">
        <Link href="/" className="flex items-center gap-2 font-bold tracking-tight">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-green-500 font-bold text-black">P</div>
          <span>PaperTrade<span className="text-green-500">Pro</span></span>
        </Link>
        <div className="flex items-center gap-4 text-sm font-medium text-zinc-400">
          <Link href="/discover" className="transition-colors hover:text-white">Discover</Link>
          <Link href="/bots" className="text-white">Bots</Link>
        </div>
      </nav>

      <div className="mx-auto max-w-6xl px-6 py-10">
        <div className="mb-6 flex flex-wrap items-center gap-3 text-sm text-zinc-400">
          <Link href={boardHref} className="rounded-full border border-white/10 bg-white/5 px-4 py-2 font-semibold text-zinc-300 transition hover:text-white">Back To Board</Link>
          <Link href={detailHref} className="rounded-full border border-white/10 bg-white/5 px-4 py-2 font-semibold text-zinc-300 transition hover:text-white">Back To Bot</Link>
          <button
            type="button"
            onClick={() => void exportRun('csv')}
            disabled={exportingFormat !== null}
            className="rounded-full border border-white/10 bg-white/5 px-4 py-2 font-semibold text-zinc-300 transition hover:text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            {exportingFormat === 'csv' ? 'Exporting CSV...' : 'Export CSV'}
          </button>
          <button
            type="button"
            onClick={() => void exportRun('json')}
            disabled={exportingFormat !== null}
            className="rounded-full border border-white/10 bg-white/5 px-4 py-2 font-semibold text-zinc-300 transition hover:text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            {exportingFormat === 'json' ? 'Exporting JSON...' : 'Export JSON'}
          </button>
          <button
            type="button"
            onClick={() => void copyRunLink()}
            disabled={copyingLink}
            className="rounded-full border border-green-500/20 bg-green-500/10 px-4 py-2 font-semibold text-green-300 transition hover:bg-green-500/15 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {copyingLink ? 'Copying Link...' : 'Copy Link'}
          </button>
        </div>

        {loading ? (
          <div className="flex justify-center py-24">
            <div className="h-9 w-9 animate-spin rounded-full border-2 border-green-500 border-t-transparent"></div>
          </div>
        ) : error || !detail ? (
          <div className="rounded-2xl border border-dashed border-white/10 px-6 py-20 text-center">
            <p className="text-lg text-zinc-300">Public strategy bot run could not be loaded.</p>
            <p className="mt-2 text-sm text-zinc-500">{error ?? 'No run payload returned.'}</p>
          </div>
        ) : (
          <div className="space-y-6">
            <section className="rounded-3xl border border-white/10 bg-white/[0.02] p-6">
              <div className="flex flex-col gap-6 xl:flex-row xl:items-start xl:justify-between">
                <div className="max-w-3xl">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="rounded-full border border-green-500/20 bg-green-500/10 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.24em] text-green-300">{detail.status}</span>
                    <span className="rounded-full border border-white/10 bg-black/30 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.24em] text-zinc-400">{detail.runMode}</span>
                    <span className="rounded-full border border-white/10 bg-black/30 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.24em] text-zinc-400">{detail.market}</span>
                    <span className="rounded-full border border-white/10 bg-black/30 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.24em] text-zinc-400">{detail.symbol}</span>
                    <span className="rounded-full border border-white/10 bg-black/30 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.24em] text-zinc-400">{detail.timeframe}</span>
                  </div>
                  <h1 className="mt-4 text-3xl font-black text-white">{detail.botName}</h1>
                  <p className="mt-3 text-sm leading-7 text-zinc-400">{detail.botDescription || 'No public bot description was provided.'}</p>
                  <div className="mt-5 flex flex-wrap gap-3 text-xs text-zinc-500">
                    <span>Requested {fmtDate(detail.requestedAt)}</span>
                    <span>Started {fmtDate(detail.startedAt)}</span>
                    <span>Completed {fmtDate(detail.completedAt)}</span>
                  </div>
                </div>
                <div className="grid min-w-[280px] gap-3 sm:grid-cols-2 xl:w-[360px] xl:grid-cols-1">
                  <div className="rounded-2xl border border-white/10 bg-black/30 p-4">
                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Owner</p>
                    <p className="mt-2 text-lg font-bold text-white">{detail.ownerDisplayName || (detail.ownerUsername ? `@${detail.ownerUsername}` : 'Unknown owner')}</p>
                    <p className="mt-1 text-xs text-zinc-500">{detail.ownerUsername ? `@${detail.ownerUsername}` : 'Profile handle unavailable'}</p>
                  </div>
                  <div className="rounded-2xl border border-white/10 bg-black/30 p-4">
                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Linked Portfolio</p>
                    <p className="mt-2 text-lg font-bold text-white">{detail.linkedPortfolioName || '-'}</p>
                    <p className="mt-1 text-xs text-zinc-500">Owner trust {detail.ownerTrustScore == null ? '-' : detail.ownerTrustScore.toFixed(2)}</p>
                  </div>
                </div>
              </div>
            </section>

            <section className="rounded-3xl border border-white/10 bg-white/[0.02] p-6">
              <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Run Summary</p>
              <div className="mt-4 grid gap-3 md:grid-cols-4">
                <div className="rounded-xl border border-white/5 bg-black/30 p-4"><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Return</p><p className="mt-2 text-xl font-bold text-white">{fmtPercent(toNullableNumber(detail.summary?.returnPercent))}</p></div>
                <div className="rounded-xl border border-white/5 bg-black/30 p-4"><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Net PnL</p><p className="mt-2 text-xl font-bold text-white">{fmtCurrency(toNullableNumber(detail.summary?.netPnl))}</p></div>
                <div className="rounded-xl border border-white/5 bg-black/30 p-4"><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Win Rate</p><p className="mt-2 text-xl font-bold text-white">{fmtPercent(toNullableNumber(detail.summary?.winRate))}</p></div>
                <div className="rounded-xl border border-white/5 bg-black/30 p-4"><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Profit Factor</p><p className="mt-2 text-xl font-bold text-white">{toNullableNumber(detail.summary?.profitFactor) == null ? '-' : toNullableNumber(detail.summary?.profitFactor)?.toFixed(2)}</p></div>
                <div className="rounded-xl border border-white/5 bg-black/30 p-4"><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Trade Count</p><p className="mt-2 text-xl font-bold text-white">{toNullableNumber(detail.summary?.tradeCount) == null ? '-' : toNullableNumber(detail.summary?.tradeCount)}</p></div>
                <div className="rounded-xl border border-white/5 bg-black/30 p-4"><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Max Drawdown</p><p className="mt-2 text-xl font-bold text-white">{fmtPercent(toNullableNumber(detail.summary?.maxDrawdownPercent))}</p></div>
                <div className="rounded-xl border border-white/5 bg-black/30 p-4"><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Requested Capital</p><p className="mt-2 text-xl font-bold text-white">{fmtCurrency(detail.requestedInitialCapital)}</p></div>
                <div className="rounded-xl border border-white/5 bg-black/30 p-4"><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Effective Capital</p><p className="mt-2 text-xl font-bold text-white">{fmtCurrency(detail.effectiveInitialCapital)}</p></div>
              </div>
              {detail.errorMessage && <p className="mt-4 text-sm text-red-300">{detail.errorMessage}</p>}
            </section>

            <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
              <section className="rounded-3xl border border-white/10 bg-white/[0.02] p-6">
                <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Equity Curve</p>
                {detail.equityCurve.length < 2 ? (
                  <div className="mt-4 rounded-2xl border border-dashed border-white/10 px-4 py-16 text-center text-sm text-zinc-500">Not enough equity points for a public curve yet.</div>
                ) : (
                  <div className="mt-4 rounded-2xl border border-white/5 bg-black/30 p-4">
                    <svg viewBox="0 0 320 96" className="h-32 w-full">
                      <polyline fill="none" stroke="rgb(74 222 128)" strokeWidth="2.5" points={sparkline} />
                    </svg>
                    <div className="mt-4 grid gap-3 sm:grid-cols-3">
                      <div><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">First Equity</p><p className="mt-1 text-sm font-semibold text-white">{fmtCurrency(detail.equityCurve[0]?.equity)}</p></div>
                      <div><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Latest Equity</p><p className="mt-1 text-sm font-semibold text-white">{fmtCurrency(detail.equityCurve.at(-1)?.equity)}</p></div>
                      <div><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Last Candle</p><p className="mt-1 text-sm font-semibold text-white">{fmtEpoch(detail.equityCurve.at(-1)?.openTime)}</p></div>
                    </div>
                  </div>
                )}
              </section>

              <section className="rounded-3xl border border-white/10 bg-white/[0.02] p-6">
                <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Compiled Rules</p>
                <div className="mt-4 grid gap-4">
                  <div className="rounded-2xl border border-white/5 bg-black/30 p-4">
                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Entry</p>
                    <pre className="mt-3 overflow-x-auto whitespace-pre-wrap text-xs leading-6 text-zinc-300">{pretty(detail.compiledEntryRules)}</pre>
                  </div>
                  <div className="rounded-2xl border border-white/5 bg-black/30 p-4">
                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Exit</p>
                    <pre className="mt-3 overflow-x-auto whitespace-pre-wrap text-xs leading-6 text-zinc-300">{pretty(detail.compiledExitRules)}</pre>
                  </div>
                </div>
              </section>
            </div>

            <section className="rounded-3xl border border-white/10 bg-white/[0.02] p-6">
              <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Simulated Fills</p>
              <div className="mt-4 space-y-3">
                {detail.fills.length === 0 ? (
                  <div className="rounded-2xl border border-dashed border-white/10 px-4 py-16 text-center text-sm text-zinc-500">No persisted fills for this public run.</div>
                ) : detail.fills.map((fill) => (
                  <div key={fill.id} className="rounded-2xl border border-white/5 bg-black/30 p-4">
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="rounded-full border border-white/10 bg-black/35 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.24em] text-zinc-400">{fill.side}</span>
                        <span className="rounded-full border border-white/10 bg-black/35 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.24em] text-zinc-400">Fill #{fill.sequenceNo}</span>
                      </div>
                      <p className="text-xs text-zinc-500">{fmtEpoch(fill.openTime)}</p>
                    </div>
                    <div className="mt-3 grid gap-3 sm:grid-cols-4">
                      <div><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Price</p><p className="mt-1 text-sm font-semibold text-white">{fmtCurrency(fill.price)}</p></div>
                      <div><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Quantity</p><p className="mt-1 text-sm font-semibold text-white">{fill.quantity.toFixed(8)}</p></div>
                      <div><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Realized PnL</p><p className="mt-1 text-sm font-semibold text-white">{fmtCurrency(fill.realizedPnl)}</p></div>
                      <div><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Rules</p><p className="mt-1 text-sm font-semibold text-white">{fill.matchedRules.length}</p></div>
                    </div>
                    <div className="mt-3 flex flex-wrap gap-2">
                      {fill.matchedRules.length === 0 ? <span className="text-xs text-zinc-500">No matched rules recorded.</span> : fill.matchedRules.map((rule) => (
                        <span key={rule} className="rounded-full border border-white/10 bg-black/35 px-3 py-1 text-xs font-semibold text-zinc-300">{rule}</span>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </section>

            <section className="rounded-3xl border border-white/10 bg-white/[0.02] p-6">
              <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Raw Summary</p>
              <pre className="mt-4 overflow-x-auto whitespace-pre-wrap rounded-2xl border border-white/5 bg-black/30 p-4 text-xs leading-6 text-zinc-300">{pretty(detail.summary)}</pre>
            </section>
          </div>
        )}
      </div>
    </div>
  );
}

export default function PublicStrategyBotRunPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen bg-black text-white">
          <div className="mx-auto max-w-6xl px-6 py-10">
            <div className="rounded-2xl border border-white/10 bg-white/[0.02] p-6 text-sm text-zinc-400">
              Loading strategy bot run...
            </div>
          </div>
        </div>
      }
    >
      <PublicStrategyBotRunPageContent />
    </Suspense>
  );
}
