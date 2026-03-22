'use client';

import Link from 'next/link';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import { apiFetch } from '../../../lib/api-client';

type RunMode = 'ALL' | 'BACKTEST' | 'FORWARD_TEST';
type LookbackOption = 'ALL' | '7' | '30' | '90';

type StrategyBotRunScorecard = {
  id: string;
  runMode: 'BACKTEST' | 'FORWARD_TEST';
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  requestedAt: string;
  completedAt?: string | null;
  returnPercent?: number | null;
  netPnl?: number | null;
  maxDrawdownPercent?: number | null;
  winRate?: number | null;
  tradeCount?: number | null;
  profitFactor?: number | null;
  expectancyPerTrade?: number | null;
  timeInMarketPercent?: number | null;
  linkedPortfolioAligned?: boolean | null;
  executionEngineReady?: boolean | null;
  errorMessage?: string | null;
};

type StrategyBotAnalytics = {
  strategyBotId: string;
  totalRuns: number;
  backtestRuns: number;
  forwardTestRuns: number;
  completedRuns: number;
  runningRuns: number;
  failedRuns: number;
  totalSimulatedTrades: number;
  avgReturnPercent?: number | null;
  avgNetPnl?: number | null;
  avgMaxDrawdownPercent?: number | null;
  avgWinRate?: number | null;
  avgTradeCount?: number | null;
  avgProfitFactor?: number | null;
  avgExpectancyPerTrade?: number | null;
  bestRun?: StrategyBotRunScorecard | null;
  worstRun?: StrategyBotRunScorecard | null;
  latestCompletedRun?: StrategyBotRunScorecard | null;
  activeForwardRun?: StrategyBotRunScorecard | null;
  entryDriverTotals: Record<string, number>;
  exitDriverTotals: Record<string, number>;
  recentScorecards: StrategyBotRunScorecard[];
};

type PublicStrategyBotDetail = {
  strategyBotId: string;
  name: string;
  description?: string | null;
  botKind: string;
  status: 'READY' | 'ARCHIVED';
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
  maxPositionSizePercent?: number | null;
  stopLossPercent?: number | null;
  takeProfitPercent?: number | null;
  cooldownMinutes?: number | null;
  entryRules?: unknown;
  exitRules?: unknown;
  analytics: StrategyBotAnalytics;
};

function parseRunMode(value: string | null): RunMode {
  return value === 'BACKTEST' || value === 'FORWARD_TEST' ? value : 'ALL';
}

function parseLookback(value: string | null): LookbackOption {
  return value === '7' || value === '30' || value === '90' ? value : 'ALL';
}

function runModeLabel(value: RunMode): string {
  if (value === 'BACKTEST') return 'Backtests';
  if (value === 'FORWARD_TEST') return 'Forward Tests';
  return 'All Runs';
}

function lookbackLabel(value: LookbackOption): string {
  return value === 'ALL' ? 'All Time' : `${value}D`;
}

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

function pretty(value: unknown): string {
  try {
    return JSON.stringify(value ?? {}, null, 2);
  } catch {
    return '{}';
  }
}

export default function PublicStrategyBotDetailPage() {
  const params = useParams<{ botId: string }>();
  const router = useRouter();
  const searchParams = useSearchParams();
  const botId = params.botId;
  const [runMode, setRunMode] = useState<RunMode>(() => parseRunMode(searchParams.get('runMode')));
  const [lookback, setLookback] = useState<LookbackOption>(() => parseLookback(searchParams.get('lookbackDays')));
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState<PublicStrategyBotDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [exportingFormat, setExportingFormat] = useState<'csv' | 'json' | null>(null);
  const [copyingLink, setCopyingLink] = useState(false);

  useEffect(() => {
    setRunMode(parseRunMode(searchParams.get('runMode')));
    setLookback(parseLookback(searchParams.get('lookbackDays')));
  }, [searchParams]);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const url = new URL(`/api/v1/strategy-bots/discover/${botId}`, window.location.origin);
        url.searchParams.set('runMode', runMode);
        if (lookback !== 'ALL') {
          url.searchParams.set('lookbackDays', lookback);
        }
        const res = await apiFetch(`${url.pathname}${url.search}`);
        if (!res.ok) {
          throw new Error(`Failed to load public strategy bot: ${res.status}`);
        }
        const payload = await res.json();
        setDetail(payload as PublicStrategyBotDetail);
      } catch (loadError) {
        console.error(loadError);
        setDetail(null);
        setError(loadError instanceof Error ? loadError.message : 'Failed to load public strategy bot');
      } finally {
        setLoading(false);
      }
    };

    if (botId) {
      load();
    }
  }, [botId, runMode, lookback]);

  const scopeSummary = useMemo(() => `${runModeLabel(runMode)} / ${lookbackLabel(lookback)}`, [runMode, lookback]);
  const backToBoardHref = useMemo(() => {
    const params = new URLSearchParams();
    const q = searchParams.get('q');
    const sortBy = searchParams.get('sortBy');
    const direction = searchParams.get('direction');
    const page = searchParams.get('page');
    if (q) params.set('q', q);
    if (sortBy) params.set('sortBy', sortBy);
    if (direction) params.set('direction', direction);
    params.set('runMode', runMode);
    if (lookback !== 'ALL') {
      params.set('lookbackDays', lookback);
    }
    if (page) params.set('page', page);
    const queryString = params.toString();
    return queryString ? `/bots?${queryString}` : '/bots';
  }, [lookback, runMode, searchParams]);

  function updateScope(nextRunMode: RunMode, nextLookback: LookbackOption) {
    const params = new URLSearchParams(searchParams.toString());
    params.set('runMode', nextRunMode);
    if (nextLookback === 'ALL') {
      params.delete('lookbackDays');
    } else {
      params.set('lookbackDays', nextLookback);
    }
    router.replace(`/bots/${botId}?${params.toString()}`);
  }

  async function exportDetail(format: 'csv' | 'json') {
    setExportingFormat(format);
    try {
      const params = new URLSearchParams();
      params.set('format', format);
      params.set('runMode', runMode);
      if (lookback !== 'ALL') {
        params.set('lookbackDays', lookback);
      }
      const res = await apiFetch(`/api/v1/strategy-bots/discover/${botId}/export?${params.toString()}`);
      if (!res.ok) {
        throw new Error(`Failed to export public strategy bot: ${res.status}`);
      }
      const blob = await res.blob();
      const objectUrl = window.URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = format === 'csv' ? `public-strategy-bot-${botId}.csv` : `public-strategy-bot-${botId}.json`;
      anchor.click();
      window.URL.revokeObjectURL(objectUrl);
    } catch (exportError) {
      console.error(exportError);
    } finally {
      setExportingFormat(null);
    }
  }

  async function copyDetailLink() {
    setCopyingLink(true);
    try {
      await navigator.clipboard.writeText(window.location.href);
    } catch (copyError) {
      console.error(copyError);
    } finally {
      setCopyingLink(false);
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
          <Link href={backToBoardHref} className="rounded-full border border-white/10 bg-white/5 px-4 py-2 font-semibold text-zinc-300 transition hover:text-white">Back To Bots</Link>
          <div className="rounded-full border border-white/10 bg-black/30 px-4 py-2 font-semibold text-zinc-400">Scope {scopeSummary}</div>
          <button
            type="button"
            onClick={() => void exportDetail('csv')}
            disabled={exportingFormat !== null}
            className="rounded-full border border-white/10 bg-white/5 px-4 py-2 font-semibold text-zinc-300 transition hover:text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            {exportingFormat === 'csv' ? 'Exporting CSV...' : 'Export CSV'}
          </button>
          <button
            type="button"
            onClick={() => void exportDetail('json')}
            disabled={exportingFormat !== null}
            className="rounded-full border border-white/10 bg-white/5 px-4 py-2 font-semibold text-zinc-300 transition hover:text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            {exportingFormat === 'json' ? 'Exporting JSON...' : 'Export JSON'}
          </button>
          <button
            type="button"
            onClick={() => void copyDetailLink()}
            disabled={copyingLink}
            className="rounded-full border border-green-500/20 bg-green-500/10 px-4 py-2 font-semibold text-green-300 transition hover:bg-green-500/15 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {copyingLink ? 'Copying Link...' : 'Copy Link'}
          </button>
        </div>

        {loading ? (
          <div className="flex justify-center py-24"><div className="h-9 w-9 animate-spin rounded-full border-2 border-green-500 border-t-transparent"></div></div>
        ) : error || !detail ? (
          <div className="rounded-2xl border border-dashed border-white/10 px-6 py-20 text-center">
            <p className="text-lg text-zinc-300">Public strategy bot could not be loaded.</p>
            <p className="mt-2 text-sm text-zinc-500">{error ?? 'No detail payload returned.'}</p>
          </div>
        ) : (
          <div className="space-y-6">
            <section className="rounded-3xl border border-white/10 bg-white/[0.02] p-6">
              <div className="flex flex-col gap-6 xl:flex-row xl:items-start xl:justify-between">
                <div className="max-w-3xl">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="rounded-full border border-emerald-500/20 bg-emerald-500/10 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.24em] text-emerald-300">{detail.status}</span>
                    <span className="rounded-full border border-white/10 bg-black/30 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.24em] text-zinc-400">{detail.botKind}</span>
                    <span className="rounded-full border border-white/10 bg-black/30 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.24em] text-zinc-400">{detail.market}</span>
                    <span className="rounded-full border border-white/10 bg-black/30 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.24em] text-zinc-400">{detail.symbol}</span>
                    <span className="rounded-full border border-white/10 bg-black/30 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.24em] text-zinc-400">{detail.timeframe}</span>
                  </div>
                  <h1 className="mt-4 text-3xl font-black text-white">{detail.name}</h1>
                  <p className="mt-3 text-sm leading-7 text-zinc-400">{detail.description || 'No public description was provided for this bot.'}</p>
                  <div className="mt-5 flex flex-wrap gap-3">
                    {detail.ownerId && <Link href={`/profile/${detail.ownerId}`} className="rounded-full border border-white/10 bg-white/5 px-4 py-2 text-xs font-semibold text-zinc-300 transition hover:text-white">View Owner Profile</Link>}
                    {detail.linkedPortfolioId && <Link href={`/dashboard/portfolio/${detail.linkedPortfolioId}`} className="rounded-full border border-green-500/20 bg-green-500/10 px-4 py-2 text-xs font-semibold text-green-300 transition hover:bg-green-500/15">Open Linked Portfolio</Link>}
                  </div>
                </div>
                <div className="grid min-w-[280px] gap-3 sm:grid-cols-2 xl:w-[340px] xl:grid-cols-1">
                  <div className="rounded-2xl border border-white/10 bg-black/30 p-4">
                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Owner</p>
                    <p className="mt-2 text-lg font-bold text-white">{detail.ownerDisplayName || (detail.ownerUsername ? `@${detail.ownerUsername}` : 'Unknown owner')}</p>
                    <p className="mt-1 text-xs text-zinc-500">{detail.ownerUsername ? `@${detail.ownerUsername}` : 'Profile handle unavailable'}</p>
                  </div>
                  <div className="rounded-2xl border border-white/10 bg-black/30 p-4">
                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Owner Trust</p>
                    <p className="mt-2 text-lg font-bold text-white">{detail.ownerTrustScore == null ? '-' : detail.ownerTrustScore.toFixed(2)}</p>
                    <p className="mt-1 text-xs text-zinc-500">Linked portfolio {detail.linkedPortfolioName || 'not available'}</p>
                  </div>
                </div>
              </div>
            </section>

            <section className="rounded-3xl border border-white/10 bg-white/[0.02] p-6">
              <div className="flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between">
                <div>
                  <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Scoped Analytics</p>
                  <h2 className="mt-2 text-xl font-black text-white">Change the lens without leaving the public bot detail.</h2>
                </div>
                <div className="flex flex-wrap gap-2">
                  {(['ALL', 'BACKTEST', 'FORWARD_TEST'] as const).map((value) => (
                    <button key={value} type="button" onClick={() => updateScope(value, lookback)} className={`rounded-full border px-3 py-1.5 text-xs font-semibold transition ${runMode === value ? 'border-green-500/35 bg-green-500/15 text-green-300' : 'border-white/10 bg-white/5 text-zinc-400 hover:text-white'}`}>
                      {runModeLabel(value)}
                    </button>
                  ))}
                  {(['ALL', '7', '30', '90'] as const).map((value) => (
                    <button key={value} type="button" onClick={() => updateScope(runMode, value)} className={`rounded-full border px-3 py-1.5 text-xs font-semibold transition ${lookback === value ? 'border-green-500/35 bg-green-500/15 text-green-300' : 'border-white/10 bg-white/5 text-zinc-400 hover:text-white'}`}>
                      {lookbackLabel(value)}
                    </button>
                  ))}
                </div>
              </div>

              <div className="mt-5 grid gap-3 md:grid-cols-4">
                <div className="rounded-xl border border-white/5 bg-black/30 p-4"><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Avg Return</p><p className="mt-2 text-xl font-bold text-white">{fmtPercent(detail.analytics.avgReturnPercent)}</p></div>
                <div className="rounded-xl border border-white/5 bg-black/30 p-4"><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Avg Profit Factor</p><p className="mt-2 text-xl font-bold text-white">{detail.analytics.avgProfitFactor == null ? '-' : detail.analytics.avgProfitFactor.toFixed(2)}</p></div>
                <div className="rounded-xl border border-white/5 bg-black/30 p-4"><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Avg Win Rate</p><p className="mt-2 text-xl font-bold text-white">{fmtPercent(detail.analytics.avgWinRate)}</p></div>
                <div className="rounded-xl border border-white/5 bg-black/30 p-4"><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Total Runs</p><p className="mt-2 text-xl font-bold text-white">{detail.analytics.totalRuns}</p></div>
                <div className="rounded-xl border border-white/5 bg-black/30 p-4"><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Completed</p><p className="mt-2 text-xl font-bold text-white">{detail.analytics.completedRuns}</p></div>
                <div className="rounded-xl border border-white/5 bg-black/30 p-4"><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Running</p><p className="mt-2 text-xl font-bold text-white">{detail.analytics.runningRuns}</p></div>
                <div className="rounded-xl border border-white/5 bg-black/30 p-4"><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Failed</p><p className="mt-2 text-xl font-bold text-white">{detail.analytics.failedRuns}</p></div>
                <div className="rounded-xl border border-white/5 bg-black/30 p-4"><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Simulated Trades</p><p className="mt-2 text-xl font-bold text-white">{detail.analytics.totalSimulatedTrades}</p></div>
              </div>
            </section>

            <div className="grid gap-6 xl:grid-cols-[0.95fr_1.05fr]">
              <section className="rounded-3xl border border-white/10 bg-white/[0.02] p-6">
                <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Risk Envelope</p>
                <div className="mt-4 grid gap-3 sm:grid-cols-2">
                  <div className="rounded-xl border border-white/5 bg-black/30 p-4"><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Max Position</p><p className="mt-2 text-lg font-bold text-white">{fmtPercent(detail.maxPositionSizePercent)}</p></div>
                  <div className="rounded-xl border border-white/5 bg-black/30 p-4"><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Stop Loss</p><p className="mt-2 text-lg font-bold text-white">{fmtPercent(detail.stopLossPercent)}</p></div>
                  <div className="rounded-xl border border-white/5 bg-black/30 p-4"><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Take Profit</p><p className="mt-2 text-lg font-bold text-white">{fmtPercent(detail.takeProfitPercent)}</p></div>
                  <div className="rounded-xl border border-white/5 bg-black/30 p-4"><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Cooldown</p><p className="mt-2 text-lg font-bold text-white">{detail.cooldownMinutes == null ? '-' : `${detail.cooldownMinutes}m`}</p></div>
                </div>
                <div className="mt-5 grid gap-4">
                  <div className="rounded-2xl border border-white/5 bg-black/30 p-4">
                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Entry Rules</p>
                    <pre className="mt-3 overflow-x-auto whitespace-pre-wrap text-xs leading-6 text-zinc-300">{pretty(detail.entryRules)}</pre>
                  </div>
                  <div className="rounded-2xl border border-white/5 bg-black/30 p-4">
                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Exit Rules</p>
                    <pre className="mt-3 overflow-x-auto whitespace-pre-wrap text-xs leading-6 text-zinc-300">{pretty(detail.exitRules)}</pre>
                  </div>
                </div>
              </section>

              <section className="rounded-3xl border border-white/10 bg-white/[0.02] p-6">
                <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Recent Scorecards</p>
                <div className="mt-4 space-y-3">
                  {detail.analytics.recentScorecards.length === 0 ? (
                    <div className="rounded-2xl border border-dashed border-white/10 px-4 py-12 text-center text-sm text-zinc-500">No runs inside this scope yet.</div>
                  ) : detail.analytics.recentScorecards.map((scorecard) => (
                    <div key={scorecard.id} className="rounded-2xl border border-white/5 bg-black/30 p-4">
                      <div className="flex flex-wrap items-center justify-between gap-3">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="rounded-full border border-white/10 bg-black/35 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.24em] text-zinc-400">{scorecard.runMode}</span>
                          <span className="rounded-full border border-white/10 bg-black/35 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.24em] text-zinc-400">{scorecard.status}</span>
                        </div>
                        <p className="text-xs text-zinc-500">Requested {fmtDate(scorecard.requestedAt)}</p>
                      </div>
                      <div className="mt-3 grid gap-3 sm:grid-cols-4">
                        <div><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Return</p><p className="mt-1 text-sm font-semibold text-white">{fmtPercent(scorecard.returnPercent)}</p></div>
                        <div><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Net PnL</p><p className="mt-1 text-sm font-semibold text-white">{fmtCurrency(scorecard.netPnl)}</p></div>
                        <div><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Win Rate</p><p className="mt-1 text-sm font-semibold text-white">{fmtPercent(scorecard.winRate)}</p></div>
                        <div><p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Profit Factor</p><p className="mt-1 text-sm font-semibold text-white">{scorecard.profitFactor == null ? '-' : scorecard.profitFactor.toFixed(2)}</p></div>
                      </div>
                      <div className="mt-3 flex flex-wrap gap-3 text-xs text-zinc-500">
                        <span>Trades {scorecard.tradeCount ?? '-'}</span>
                        <span>Expectancy {fmtCurrency(scorecard.expectancyPerTrade)}</span>
                        <span>Time In Market {fmtPercent(scorecard.timeInMarketPercent)}</span>
                        <span>Completed {fmtDate(scorecard.completedAt)}</span>
                      </div>
                      {scorecard.errorMessage && <p className="mt-3 text-xs text-red-300">{scorecard.errorMessage}</p>}
                    </div>
                  ))}
                </div>
              </section>
            </div>

            <section className="rounded-3xl border border-white/10 bg-white/[0.02] p-6">
              <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Driver Totals</p>
              <div className="mt-4 grid gap-6 md:grid-cols-2">
                <div>
                  <p className="text-sm font-semibold text-white">Entry Drivers</p>
                  <div className="mt-3 flex flex-wrap gap-2">
                    {Object.entries(detail.analytics.entryDriverTotals || {}).length === 0 ? <span className="text-sm text-zinc-500">No entry driver totals in this scope.</span> : Object.entries(detail.analytics.entryDriverTotals).map(([reason, count]) => (
                      <span key={reason} className="rounded-full border border-white/10 bg-black/30 px-3 py-1 text-xs font-semibold text-zinc-300">{reason} | {count}</span>
                    ))}
                  </div>
                </div>
                <div>
                  <p className="text-sm font-semibold text-white">Exit Drivers</p>
                  <div className="mt-3 flex flex-wrap gap-2">
                    {Object.entries(detail.analytics.exitDriverTotals || {}).length === 0 ? <span className="text-sm text-zinc-500">No exit driver totals in this scope.</span> : Object.entries(detail.analytics.exitDriverTotals).map(([reason, count]) => (
                      <span key={reason} className="rounded-full border border-white/10 bg-black/30 px-3 py-1 text-xs font-semibold text-zinc-300">{reason} | {count}</span>
                    ))}
                  </div>
                </div>
              </div>
            </section>
          </div>
        )}
      </div>
    </div>
  );
}
