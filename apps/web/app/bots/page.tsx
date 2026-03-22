'use client';

import Link from 'next/link';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import { apiFetch } from '../../lib/api-client';
import { extractContent } from '../../lib/page';

type BoardSort = 'AVG_RETURN' | 'AVG_WIN_RATE' | 'AVG_PROFIT_FACTOR' | 'TOTAL_RUNS' | 'LATEST_REQUESTED_AT';
type Direction = 'ASC' | 'DESC';
type RunMode = 'ALL' | 'BACKTEST' | 'FORWARD_TEST';
type LookbackOption = 'ALL' | '7' | '30' | '90';

interface StrategyBotRunScorecard {
  id: string;
  runMode: 'BACKTEST' | 'FORWARD_TEST';
  returnPercent: number | null;
  netPnl: number | null;
}

interface PublicStrategyBotBoardEntry {
  strategyBotId: string;
  description: string | null;
  name: string;
  status: 'READY' | 'ARCHIVED';
  market: string;
  symbol: string;
  timeframe: string;
  linkedPortfolioId: string | null;
  linkedPortfolioName: string | null;
  ownerId: string | null;
  ownerUsername: string | null;
  ownerDisplayName: string | null;
  ownerTrustScore: number | null;
  totalRuns: number;
  totalSimulatedTrades: number;
  avgReturnPercent: number | null;
  avgWinRate: number | null;
  avgProfitFactor: number | null;
  latestRequestedAt: string | null;
  bestRun: StrategyBotRunScorecard | null;
  activeForwardRun: StrategyBotRunScorecard | null;
}

const PRESETS = [
  { key: 'edge', label: 'All-Time Edge', sortBy: 'AVG_RETURN', direction: 'DESC', runMode: 'ALL', lookback: 'ALL' },
  { key: 'quality', label: 'Backtest Quality', sortBy: 'AVG_PROFIT_FACTOR', direction: 'DESC', runMode: 'BACKTEST', lookback: '90' },
  { key: 'live', label: 'Live Forward', sortBy: 'AVG_RETURN', direction: 'DESC', runMode: 'FORWARD_TEST', lookback: '30' },
  { key: 'density', label: 'Run Density', sortBy: 'TOTAL_RUNS', direction: 'DESC', runMode: 'ALL', lookback: 'ALL' },
] as const satisfies ReadonlyArray<{
  key: string;
  label: string;
  sortBy: BoardSort;
  direction: Direction;
  runMode: RunMode;
  lookback: LookbackOption;
}>;

function readPageMeta(payload: unknown): { totalElements: number; totalPages: number } {
  if (!payload || typeof payload !== 'object') {
    return { totalElements: 0, totalPages: 0 };
  }
  const page = (payload as { page?: { totalElements?: number; totalPages?: number } }).page;
  return {
    totalElements: typeof page?.totalElements === 'number' ? page.totalElements : 0,
    totalPages: typeof page?.totalPages === 'number' ? page.totalPages : 0,
  };
}

function formatPercent(value: number | null): string {
  if (value == null) return '-';
  return `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`;
}

function formatCurrency(value: number | null): string {
  if (value == null) return '-';
  return `$${value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatDate(value: string | null): string {
  if (!value) return '-';
  return new Date(value).toLocaleDateString();
}

function runModeLabel(value: RunMode): string {
  if (value === 'BACKTEST') return 'Backtests';
  if (value === 'FORWARD_TEST') return 'Forward Tests';
  return 'All Runs';
}

function lookbackLabel(value: LookbackOption): string {
  return value === 'ALL' ? 'All Time' : `${value}D`;
}

function parseBoardSort(value: string | null): BoardSort {
  return value === 'AVG_WIN_RATE'
    || value === 'AVG_PROFIT_FACTOR'
    || value === 'TOTAL_RUNS'
    || value === 'LATEST_REQUESTED_AT'
    ? value
    : 'AVG_RETURN';
}

function parseDirection(value: string | null): Direction {
  return value === 'ASC' ? 'ASC' : 'DESC';
}

function parseRunMode(value: string | null): RunMode {
  return value === 'BACKTEST' || value === 'FORWARD_TEST' ? value : 'ALL';
}

function parseLookback(value: string | null): LookbackOption {
  return value === '7' || value === '30' || value === '90' ? value : 'ALL';
}

function parsePageIndex(value: string | null): number {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : 0;
}

export default function PublicStrategyBotsPage() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const [bots, setBots] = useState<PublicStrategyBotBoardEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState(() => searchParams.get('q')?.trim() ?? '');
  const [searchInput, setSearchInput] = useState(() => searchParams.get('q') ?? '');
  const [sortBy, setSortBy] = useState<BoardSort>(() => parseBoardSort(searchParams.get('sortBy')));
  const [direction, setDirection] = useState<Direction>(() => parseDirection(searchParams.get('direction')));
  const [runMode, setRunMode] = useState<RunMode>(() => parseRunMode(searchParams.get('runMode')));
  const [lookback, setLookback] = useState<LookbackOption>(() => parseLookback(searchParams.get('lookbackDays')));
  const [pageIndex, setPageIndex] = useState(() => parsePageIndex(searchParams.get('page')));
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [exportingBoardFormat, setExportingBoardFormat] = useState<'csv' | 'json' | null>(null);
  const [copyingLens, setCopyingLens] = useState(false);

  useEffect(() => {
    const params = new URLSearchParams();
    params.set('sortBy', sortBy);
    params.set('direction', direction);
    params.set('runMode', runMode);
    if (lookback !== 'ALL') {
      params.set('lookbackDays', lookback);
    }
    if (query) {
      params.set('q', query);
    }
    if (pageIndex > 0) {
      params.set('page', String(pageIndex));
    }
    const next = params.toString();
    router.replace(next ? `${pathname}?${next}` : pathname, { scroll: false });
  }, [direction, lookback, pageIndex, pathname, query, router, runMode, sortBy]);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setPageIndex(0);
      setQuery(searchInput.trim());
    }, 250);
    return () => window.clearTimeout(timeoutId);
  }, [searchInput]);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        const url = new URL('/api/v1/strategy-bots/discover', window.location.origin);
        url.searchParams.set('page', String(pageIndex));
        url.searchParams.set('size', '12');
        url.searchParams.set('sortBy', sortBy);
        url.searchParams.set('direction', direction);
        url.searchParams.set('runMode', runMode);
        if (lookback !== 'ALL') url.searchParams.set('lookbackDays', lookback);
        if (query) url.searchParams.set('q', query);
        const res = await apiFetch(`${url.pathname}${url.search}`);
        if (!res.ok) throw new Error(`Failed to load public bots: ${res.status}`);
        const payload = await res.json();
        setBots(extractContent<PublicStrategyBotBoardEntry>(payload));
        const meta = readPageMeta(payload);
        setTotalElements(meta.totalElements);
        setTotalPages(meta.totalPages);
      } catch (error) {
        console.error(error);
        setBots([]);
        setTotalPages(0);
        setTotalElements(0);
      } finally {
        setLoading(false);
      }
    };

    load();
  }, [pageIndex, sortBy, direction, runMode, lookback, query]);

  const activePreset = useMemo(() => (
    PRESETS.find((preset) => preset.sortBy === sortBy && preset.direction === direction && preset.runMode === runMode && preset.lookback === lookback)?.key ?? null
  ), [sortBy, direction, runMode, lookback]);

  const averageVisibleReturn = useMemo(() => {
    const visible = bots.filter((bot) => bot.avgReturnPercent != null);
    if (visible.length === 0) return null;
    return visible.reduce((sum, bot) => sum + (bot.avgReturnPercent ?? 0), 0) / visible.length;
  }, [bots]);

  const visibleTrades = useMemo(() => bots.reduce((sum, bot) => sum + bot.totalSimulatedTrades, 0), [bots]);

  function buildLensSearchParams(includePage = true): URLSearchParams {
    const params = new URLSearchParams();
    params.set('sortBy', sortBy);
    params.set('direction', direction);
    params.set('runMode', runMode);
    if (lookback !== 'ALL') {
      params.set('lookbackDays', lookback);
    }
    if (query) {
      params.set('q', query);
    }
    if (includePage && pageIndex > 0) {
      params.set('page', String(pageIndex));
    }
    return params;
  }

  async function exportBoard(format: 'csv' | 'json') {
    setExportingBoardFormat(format);
    try {
      const params = buildLensSearchParams(false);
      params.set('format', format);
      const res = await apiFetch(`/api/v1/strategy-bots/discover/export?${params.toString()}`);
      if (!res.ok) {
        throw new Error(`Failed to export public strategy bots: ${res.status}`);
      }
      const blob = await res.blob();
      const objectUrl = window.URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = format === 'csv' ? 'public-strategy-bot-board.csv' : 'public-strategy-bot-board.json';
      anchor.click();
      window.URL.revokeObjectURL(objectUrl);
    } catch (error) {
      console.error(error);
    } finally {
      setExportingBoardFormat(null);
    }
  }

  async function copyLensLink() {
    setCopyingLens(true);
    try {
      const href = `${window.location.origin}${pathname}?${buildLensSearchParams(true).toString()}`;
      await navigator.clipboard.writeText(href);
    } catch (error) {
      console.error(error);
    } finally {
      setCopyingLens(false);
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
          <Link href="/dashboard" className="transition-colors hover:text-white">Dashboard</Link>
          <Link href="/dashboard/leaderboard" className="transition-colors hover:text-white">Leaderboard</Link>
          <Link href="/discover" className="transition-colors hover:text-white">Discover</Link>
          <Link href="/bots" className="text-white">Bots</Link>
        </div>
      </nav>

      <div className="mx-auto max-w-6xl px-6 py-10">
        <div className="mb-8">
          <h1 className="text-3xl font-bold">Discover <span className="text-green-400">Strategy Bots</span></h1>
          <p className="mt-2 max-w-3xl text-sm text-zinc-400">
            Public bots are limited to non-draft rule engines linked to public portfolios. The board is read-only and optimized for comparison, not execution.
          </p>
        </div>

        <section className="rounded-2xl border border-white/10 bg-white/[0.02] p-5">
          <div className="flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between">
            <div>
              <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Comparison Lens</p>
              <h2 className="mt-2 text-xl font-black text-white">Read public bots with the same scoped metrics used in the private workspace.</h2>
            </div>
            <input
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder="Search by bot, owner, symbol, market, or timeframe"
              className="w-full rounded-xl border border-white/10 bg-black/30 px-4 py-2.5 text-sm text-white outline-none transition placeholder:text-zinc-600 focus:border-green-500/35 xl:w-96"
            />
          </div>

          <div className="mt-4 flex flex-wrap gap-2">
            {PRESETS.map((preset) => (
              <button
                key={preset.key}
                type="button"
                onClick={() => {
                  setPageIndex(0);
                  setSortBy(preset.sortBy);
                  setDirection(preset.direction);
                  setRunMode(preset.runMode);
                  setLookback(preset.lookback);
                }}
                className={`rounded-full border px-3 py-1.5 text-xs font-semibold transition ${activePreset === preset.key ? 'border-green-500/35 bg-green-500/15 text-green-300' : 'border-white/10 bg-white/5 text-zinc-400 hover:text-white'}`}
              >
                {preset.label}
              </button>
            ))}
          </div>

          <div className="mt-4 flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => void exportBoard('csv')}
              disabled={exportingBoardFormat !== null}
              className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-xs font-semibold text-zinc-300 transition hover:text-white disabled:cursor-not-allowed disabled:opacity-50"
            >
              {exportingBoardFormat === 'csv' ? 'Exporting CSV...' : 'Export Board CSV'}
            </button>
            <button
              type="button"
              onClick={() => void exportBoard('json')}
              disabled={exportingBoardFormat !== null}
              className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-xs font-semibold text-zinc-300 transition hover:text-white disabled:cursor-not-allowed disabled:opacity-50"
            >
              {exportingBoardFormat === 'json' ? 'Exporting JSON...' : 'Export Board JSON'}
            </button>
            <button
              type="button"
              onClick={() => void copyLensLink()}
              disabled={copyingLens}
              className="rounded-full border border-green-500/20 bg-green-500/10 px-3 py-1.5 text-xs font-semibold text-green-300 transition hover:bg-green-500/15 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {copyingLens ? 'Copying Link...' : 'Copy Lens Link'}
            </button>
          </div>

          <div className="mt-4 grid gap-4 xl:grid-cols-3">
            <div>
              <p className="mb-2 text-[10px] uppercase tracking-[0.24em] text-zinc-500">Run Scope</p>
              <div className="flex flex-wrap gap-2">
                {(['ALL', 'BACKTEST', 'FORWARD_TEST'] as const).map((value) => (
                  <button
                    key={value}
                    type="button"
                    onClick={() => { setPageIndex(0); setRunMode(value); }}
                    className={`rounded-full border px-3 py-1.5 text-xs font-semibold transition ${runMode === value ? 'border-green-500/35 bg-green-500/15 text-green-300' : 'border-white/10 bg-white/5 text-zinc-400 hover:text-white'}`}
                  >
                    {runModeLabel(value)}
                  </button>
                ))}
              </div>
            </div>
            <div>
              <p className="mb-2 text-[10px] uppercase tracking-[0.24em] text-zinc-500">Lookback</p>
              <div className="flex flex-wrap gap-2">
                {(['ALL', '7', '30', '90'] as const).map((value) => (
                  <button
                    key={value}
                    type="button"
                    onClick={() => { setPageIndex(0); setLookback(value); }}
                    className={`rounded-full border px-3 py-1.5 text-xs font-semibold transition ${lookback === value ? 'border-green-500/35 bg-green-500/15 text-green-300' : 'border-white/10 bg-white/5 text-zinc-400 hover:text-white'}`}
                  >
                    {lookbackLabel(value)}
                  </button>
                ))}
              </div>
            </div>
            <div>
              <p className="mb-2 text-[10px] uppercase tracking-[0.24em] text-zinc-500">Sort</p>
              <div className="flex flex-wrap gap-2">
                {([
                  { key: 'AVG_RETURN', label: 'Avg Return' },
                  { key: 'AVG_WIN_RATE', label: 'Win Rate' },
                  { key: 'AVG_PROFIT_FACTOR', label: 'Profit Factor' },
                  { key: 'TOTAL_RUNS', label: 'Runs' },
                  { key: 'LATEST_REQUESTED_AT', label: 'Latest' },
                ] as const).map(({ key, label }) => (
                  <button
                    key={key}
                    type="button"
                    onClick={() => { setPageIndex(0); setSortBy(key); }}
                    className={`rounded-full border px-3 py-1.5 text-xs font-semibold transition ${sortBy === key ? 'border-green-500/35 bg-green-500/15 text-green-300' : 'border-white/10 bg-white/5 text-zinc-400 hover:text-white'}`}
                  >
                    {label}
                  </button>
                ))}
                <button
                  type="button"
                  onClick={() => { setPageIndex(0); setDirection((current) => current === 'DESC' ? 'ASC' : 'DESC'); }}
                  className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-xs font-semibold text-zinc-300 transition hover:text-white"
                >
                  {direction === 'DESC' ? 'Descending' : 'Ascending'}
                </button>
              </div>
            </div>
          </div>

          <div className="mt-4 grid gap-3 md:grid-cols-4">
            <div className="rounded-xl border border-white/5 bg-black/30 p-4">
              <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Public Bots</p>
              <p className="mt-2 text-2xl font-bold text-white">{loading ? '...' : totalElements}</p>
            </div>
            <div className="rounded-xl border border-white/5 bg-black/30 p-4">
              <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Visible Trades</p>
              <p className="mt-2 text-2xl font-bold text-green-300">{loading ? '...' : visibleTrades}</p>
            </div>
            <div className="rounded-xl border border-white/5 bg-black/30 p-4">
              <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Avg Visible Return</p>
              <p className="mt-2 text-2xl font-bold text-white">{loading ? '...' : formatPercent(averageVisibleReturn)}</p>
            </div>
            <div className="rounded-xl border border-white/5 bg-black/30 p-4">
              <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Active Scope</p>
              <p className="mt-2 text-sm font-semibold text-white">{runModeLabel(runMode)} / {lookbackLabel(lookback)}</p>
            </div>
          </div>
        </section>

        <div className="mt-6">
          {loading ? (
            <div className="flex justify-center py-20">
              <div className="h-8 w-8 animate-spin rounded-full border-2 border-green-500 border-t-transparent"></div>
            </div>
          ) : bots.length === 0 ? (
            <div className="rounded-xl border border-dashed border-white/10 py-20 text-center">
              <p className="text-lg text-zinc-400">No public strategy bot matches this lens.</p>
              <p className="mt-2 text-sm text-zinc-600">Try a different scope, sort, or search query.</p>
            </div>
          ) : (
            <div className="grid gap-4 lg:grid-cols-2">
              {bots.map((bot) => (
                <article key={bot.strategyBotId} className="rounded-2xl border border-white/10 bg-white/[0.02] p-6">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="text-xl font-black text-white">{bot.name}</h3>
                        <span className="rounded-full border border-green-500/20 bg-green-500/10 px-2 py-0.5 text-[10px] font-bold uppercase tracking-[0.2em] text-green-300">{bot.status}</span>
                      </div>
                      <p className="mt-2 text-sm text-zinc-400">{bot.description || 'No public description was provided for this bot.'}</p>
                    </div>
                    <div className="rounded-xl border border-white/10 bg-black/35 px-3 py-2 text-right">
                      <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Owner Trust</p>
                      <p className="mt-1 text-sm font-bold text-white">{bot.ownerTrustScore == null ? '-' : bot.ownerTrustScore.toFixed(2)}</p>
                    </div>
                  </div>

                  <div className="mt-4 flex flex-wrap gap-2 text-[11px] uppercase tracking-[0.18em] text-zinc-400">
                    <span className="rounded-full border border-white/10 bg-black/35 px-3 py-1">{bot.market}</span>
                    <span className="rounded-full border border-white/10 bg-black/35 px-3 py-1">{bot.symbol}</span>
                    <span className="rounded-full border border-white/10 bg-black/35 px-3 py-1">{bot.timeframe}</span>
                    {bot.linkedPortfolioName && <span className="rounded-full border border-white/10 bg-black/35 px-3 py-1">{bot.linkedPortfolioName}</span>}
                  </div>

                  <div className="mt-4 grid gap-3 sm:grid-cols-4">
                    <div className="rounded-xl border border-white/5 bg-black/30 p-3">
                      <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Avg Return</p>
                      <p className={`mt-2 text-lg font-bold ${bot.avgReturnPercent != null && bot.avgReturnPercent >= 0 ? 'text-green-300' : 'text-white'}`}>{formatPercent(bot.avgReturnPercent)}</p>
                    </div>
                    <div className="rounded-xl border border-white/5 bg-black/30 p-3">
                      <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Win Rate</p>
                      <p className="mt-2 text-lg font-bold text-white">{formatPercent(bot.avgWinRate)}</p>
                    </div>
                    <div className="rounded-xl border border-white/5 bg-black/30 p-3">
                      <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Profit Factor</p>
                      <p className="mt-2 text-lg font-bold text-white">{bot.avgProfitFactor == null ? '-' : bot.avgProfitFactor.toFixed(2)}</p>
                    </div>
                    <div className="rounded-xl border border-white/5 bg-black/30 p-3">
                      <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Runs</p>
                      <p className="mt-2 text-lg font-bold text-white">{bot.totalRuns}</p>
                    </div>
                  </div>

                  <div className="mt-4 grid gap-3 md:grid-cols-2">
                    <div className="rounded-xl border border-white/5 bg-black/25 p-4">
                      <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Owner</p>
                      <p className="mt-2 text-sm font-semibold text-white">{bot.ownerDisplayName || (bot.ownerUsername ? `@${bot.ownerUsername}` : 'Unknown owner')}</p>
                      <p className="mt-1 text-xs text-zinc-500">Latest activity {formatDate(bot.latestRequestedAt)}</p>
                    </div>
                    <div className="rounded-xl border border-white/5 bg-black/25 p-4">
                      <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Best Run</p>
                      <p className="mt-2 text-sm font-semibold text-white">{bot.bestRun ? formatPercent(bot.bestRun.returnPercent) : '-'}</p>
                      <p className="mt-1 text-xs text-zinc-500">{bot.bestRun ? `${bot.bestRun.runMode} | ${formatCurrency(bot.bestRun.netPnl)}` : 'No completed run yet'}</p>
                    </div>
                  </div>

                  <div className="mt-5 flex flex-wrap gap-3">
                    <Link
                      href={`/bots/${bot.strategyBotId}?${buildLensSearchParams(true).toString()}`}
                      className="rounded-full border border-emerald-500/20 bg-emerald-500/10 px-4 py-2 text-xs font-semibold text-emerald-300 transition hover:bg-emerald-500/15"
                    >
                      View Bot
                    </Link>
                    {bot.ownerId && <Link href={`/profile/${bot.ownerId}`} className="rounded-full border border-white/10 bg-white/5 px-4 py-2 text-xs font-semibold text-zinc-300 transition hover:text-white">View Profile</Link>}
                    {bot.linkedPortfolioId && <Link href={`/dashboard/portfolio/${bot.linkedPortfolioId}`} className="rounded-full border border-green-500/20 bg-green-500/10 px-4 py-2 text-xs font-semibold text-green-300 transition hover:bg-green-500/15">Open Portfolio</Link>}
                    <div className="rounded-full border border-white/10 bg-black/30 px-4 py-2 text-xs font-semibold text-zinc-400">
                      {bot.activeForwardRun ? `Forward ${formatPercent(bot.activeForwardRun.returnPercent)}` : 'No active forward test'}
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}
        </div>

        <div className="mt-6 flex flex-col gap-3 rounded-2xl border border-white/10 bg-white/[0.02] p-5 md:flex-row md:items-center md:justify-between">
          <div className="text-sm text-zinc-400">
            Showing page <span className="font-semibold text-white">{totalPages === 0 ? 0 : pageIndex + 1}</span> of <span className="font-semibold text-white">{Math.max(totalPages, 1)}</span> from <span className="font-semibold text-white">{totalElements}</span> public bots.
          </div>
          <div className="flex gap-2">
            <button type="button" onClick={() => setPageIndex((current) => Math.max(0, current - 1))} disabled={pageIndex === 0} className="rounded-full border border-white/10 bg-white/5 px-4 py-2 text-xs font-semibold text-zinc-300 transition hover:text-white disabled:cursor-not-allowed disabled:opacity-40">Prev</button>
            <button type="button" onClick={() => setPageIndex((current) => (current + 1 < totalPages ? current + 1 : current))} disabled={totalPages === 0 || pageIndex + 1 >= totalPages} className="rounded-full border border-white/10 bg-white/5 px-4 py-2 text-xs font-semibold text-zinc-300 transition hover:text-white disabled:cursor-not-allowed disabled:opacity-40">Next</button>
          </div>
        </div>
      </div>
    </div>
  );
}
