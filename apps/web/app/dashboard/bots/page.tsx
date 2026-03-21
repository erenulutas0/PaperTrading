'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { FormEvent, useEffect, useMemo, useState } from 'react';
import { apiFetch } from '../../../lib/api-client';
import { extractContent } from '../../../lib/page';

type Tab = 'OVERVIEW' | 'BOTS' | 'RUNS';
type BotStatus = 'DRAFT' | 'READY' | 'ARCHIVED';
type RunMode = 'BACKTEST' | 'FORWARD_TEST';

type PortfolioOption = { id: string; name: string; balance: number; visibility?: 'PUBLIC' | 'PRIVATE' };
type StrategyBot = {
    id: string; linkedPortfolioId: string; name: string; description?: string; status: BotStatus;
    market: string; symbol: string; timeframe: string; entryRules: unknown; exitRules: unknown;
    maxPositionSizePercent?: number; stopLossPercent?: number; takeProfitPercent?: number; cooldownMinutes?: number; updatedAt: string;
};
type StrategyBotRun = {
    id: string; runMode: RunMode; status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'; requestedAt: string;
    fromDate?: string; toDate?: string; errorMessage?: string | null;
    summary?: { executionEngineReady?: boolean; unsupportedRules?: string[]; warnings?: string[]; supportedFeatures?: string[]; fills?: unknown[]; equityCurve?: unknown[]; endingEquity?: number; netPnl?: number; returnPercent?: number; tradeCount?: number; maxDrawdownPercent?: number; avgWinPnl?: number | null; avgLossPnl?: number | null; profitFactor?: number | null; expectancyPerTrade?: number | null; bestTradePnl?: number | null; worstTradePnl?: number | null; lastEvaluatedOpenTime?: number | null; positionOpen?: boolean; openQuantity?: number | null; openEntryPrice?: number | null } | null;
};
type StrategyBotRunFill = {
    id: string; sequenceNo: number; side: 'ENTRY' | 'EXIT'; openTime: number;
    price: number; quantity: number; realizedPnl: number; matchedRules: string[];
};
type StrategyBotRunEquityPoint = {
    id: string; sequenceNo: number; openTime: number; closePrice: number; equity: number;
};

const defaultEntryRules = JSON.stringify({ all: ['price_above_ma_20', 'rsi_below_35'] }, null, 2);
const defaultExitRules = JSON.stringify({ any: ['take_profit_hit', 'stop_loss_hit'] }, null, 2);

function numberOrUndefined(value: string) {
    if (!value.trim()) return undefined;
    const parsed = Number(value);
    if (Number.isNaN(parsed)) throw new Error(`Invalid numeric value: ${value}`);
    return parsed;
}

function fmtDate(value?: string | null) { return value ? new Date(value).toLocaleString() : 'N/A'; }
function fmtEpoch(value?: number | null) { return value === undefined || value === null ? 'N/A' : new Date(value).toLocaleString(); }
function fmtCurrency(value?: number | null) { return value === undefined || value === null || Number.isNaN(value) ? 'N/A' : `$${value.toLocaleString(undefined, { maximumFractionDigits: 2 })}`; }
function fmtPercent(value?: number | null) { return value === undefined || value === null || Number.isNaN(value) ? 'N/A' : `${value.toFixed(2)}%`; }
function pretty(value: unknown) { try { return JSON.stringify(value ?? {}, null, 2); } catch { return '{}'; } }
function err(error: unknown) { return error instanceof Error ? error.message : 'Unexpected request failure'; }

function buildSparkline(points: StrategyBotRunEquityPoint[]) {
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

export default function StrategyBotsPage() {
    const router = useRouter();
    const [tab, setTab] = useState<Tab>('OVERVIEW');
    const [userId, setUserId] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [requestingRun, setRequestingRun] = useState(false);
    const [executingRunId, setExecutingRunId] = useState<string | null>(null);
    const [refreshingRunId, setRefreshingRunId] = useState<string | null>(null);
    const [outputsLoading, setOutputsLoading] = useState(false);
    const [pageError, setPageError] = useState<string | null>(null);
    const [actionError, setActionError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [portfolios, setPortfolios] = useState<PortfolioOption[]>([]);
    const [bots, setBots] = useState<StrategyBot[]>([]);
    const [runs, setRuns] = useState<StrategyBotRun[]>([]);
    const [selectedBotId, setSelectedBotId] = useState('');
    const [selectedRunId, setSelectedRunId] = useState('');
    const [selectedRunFills, setSelectedRunFills] = useState<StrategyBotRunFill[]>([]);
    const [selectedRunEquityCurve, setSelectedRunEquityCurve] = useState<StrategyBotRunEquityPoint[]>([]);
    const [editingBotId, setEditingBotId] = useState<string | null>(null);
    const [botForm, setBotForm] = useState({ name: '', description: '', linkedPortfolioId: '', market: 'CRYPTO', symbol: 'BTCUSDT', timeframe: '1h', status: 'DRAFT' as BotStatus, maxPositionSizePercent: '20', stopLossPercent: '3', takeProfitPercent: '8', cooldownMinutes: '60', entryRulesText: defaultEntryRules, exitRulesText: defaultExitRules });
    const [runForm, setRunForm] = useState({ runMode: 'BACKTEST' as RunMode, initialCapital: '', fromDate: '', toDate: '' });

    const selectedBot = useMemo(() => bots.find((bot) => bot.id === selectedBotId) ?? null, [bots, selectedBotId]);
    const latestRun = runs[0] ?? null;
    const selectedRun = useMemo(() => runs.find((run) => run.id === selectedRunId) ?? latestRun ?? null, [runs, selectedRunId, latestRun]);

    useEffect(() => {
        const currentUserId = localStorage.getItem('userId');
        if (!currentUserId) { router.push('/auth/login'); return; }
        setUserId(currentUserId);
        void bootstrap(currentUserId);
    }, [router]);

    useEffect(() => {
        if (selectedBotId) {
            void loadRuns(selectedBotId);
        } else {
            setRuns([]);
            setSelectedRunId('');
            setSelectedRunFills([]);
            setSelectedRunEquityCurve([]);
        }
    }, [selectedBotId]);

    useEffect(() => {
        if (selectedBotId && selectedRunId) {
            void loadRunOutputs(selectedBotId, selectedRunId);
        } else {
            setSelectedRunFills([]);
            setSelectedRunEquityCurve([]);
        }
    }, [selectedBotId, selectedRunId]);

    async function bootstrap(currentUserId: string) {
        setLoading(true); setPageError(null);
        try {
            const [portfolioRes, botRes] = await Promise.all([
                apiFetch(`/api/v1/portfolios?ownerId=${encodeURIComponent(currentUserId)}&size=50`, { cache: 'no-store' }),
                apiFetch('/api/v1/strategy-bots?size=50', { cache: 'no-store' }),
            ]);
            if (!portfolioRes.ok) throw new Error(`Failed to load portfolios (${portfolioRes.status})`);
            if (!botRes.ok) throw new Error(`Failed to load strategy bots (${botRes.status})`);
            const nextPortfolios = extractContent<PortfolioOption>(await portfolioRes.json());
            const nextBots = extractContent<StrategyBot>(await botRes.json());
            setPortfolios(nextPortfolios);
            setBots(nextBots);
            setSelectedBotId((current) => current && nextBots.some((bot) => bot.id === current) ? current : nextBots[0]?.id ?? '');
            setBotForm((current) => current.linkedPortfolioId ? current : { ...current, linkedPortfolioId: nextPortfolios[0]?.id ?? '' });
        } catch (error) { setPageError(err(error)); } finally { setLoading(false); }
    }

    async function loadRuns(botId: string) {
        try {
            const response = await apiFetch(`/api/v1/strategy-bots/${botId}/runs?size=20`, { cache: 'no-store' });
            if (!response.ok) throw new Error(`Failed to load bot runs (${response.status})`);
            const nextRuns = extractContent<StrategyBotRun>(await response.json());
            setRuns(nextRuns);
            setSelectedRunId((current) => current && nextRuns.some((run) => run.id === current) ? current : nextRuns[0]?.id ?? '');
        } catch (error) { setActionError(err(error)); }
    }

    async function loadRunOutputs(botId: string, runId: string) {
        setOutputsLoading(true);
        try {
            const [fillsRes, curveRes] = await Promise.all([
                apiFetch(`/api/v1/strategy-bots/${botId}/runs/${runId}/fills?size=200`, { cache: 'no-store' }),
                apiFetch(`/api/v1/strategy-bots/${botId}/runs/${runId}/equity-curve?size=1000`, { cache: 'no-store' }),
            ]);
            if (!fillsRes.ok) throw new Error(`Failed to load run fills (${fillsRes.status})`);
            if (!curveRes.ok) throw new Error(`Failed to load run equity curve (${curveRes.status})`);
            setSelectedRunFills(extractContent<StrategyBotRunFill>(await fillsRes.json()));
            setSelectedRunEquityCurve(extractContent<StrategyBotRunEquityPoint>(await curveRes.json()));
        } catch (error) {
            setActionError(err(error));
        } finally {
            setOutputsLoading(false);
        }
    }

    function resetBotForm() {
        setEditingBotId(null);
        setBotForm({ name: '', description: '', linkedPortfolioId: portfolios[0]?.id ?? '', market: 'CRYPTO', symbol: 'BTCUSDT', timeframe: '1h', status: 'DRAFT', maxPositionSizePercent: '20', stopLossPercent: '3', takeProfitPercent: '8', cooldownMinutes: '60', entryRulesText: defaultEntryRules, exitRulesText: defaultExitRules });
    }

    async function saveBot(event: FormEvent<HTMLFormElement>) {
        event.preventDefault(); setSaving(true); setActionError(null); setNotice(null);
        try {
            const response = await apiFetch(editingBotId ? `/api/v1/strategy-bots/${editingBotId}` : '/api/v1/strategy-bots', {
                method: editingBotId ? 'PUT' : 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: botForm.name.trim(), description: botForm.description.trim() || null, linkedPortfolioId: botForm.linkedPortfolioId, market: botForm.market.trim(), symbol: botForm.symbol.trim().toUpperCase(), timeframe: botForm.timeframe.trim(), status: botForm.status, maxPositionSizePercent: numberOrUndefined(botForm.maxPositionSizePercent), stopLossPercent: numberOrUndefined(botForm.stopLossPercent), takeProfitPercent: numberOrUndefined(botForm.takeProfitPercent), cooldownMinutes: numberOrUndefined(botForm.cooldownMinutes), entryRules: JSON.parse(botForm.entryRulesText), exitRules: JSON.parse(botForm.exitRulesText) }),
            });
            if (!response.ok) throw new Error(await response.text() || `Strategy bot save failed (${response.status})`);
            const bot = await response.json() as StrategyBot;
            if (userId) await bootstrap(userId);
            setSelectedBotId(bot.id); setNotice(editingBotId ? 'Strategy bot updated' : 'Strategy bot created'); resetBotForm(); setTab('RUNS');
        } catch (error) { setActionError(err(error)); } finally { setSaving(false); }
    }

    async function deleteBot(botId: string) {
        if (!confirm('Delete this strategy bot and its run journal?')) return;
        setActionError(null); setNotice(null);
        try {
            const response = await apiFetch(`/api/v1/strategy-bots/${botId}`, { method: 'DELETE' });
            if (!response.ok) throw new Error(await response.text() || `Strategy bot delete failed (${response.status})`);
            if (userId) await bootstrap(userId);
            if (editingBotId === botId) resetBotForm();
            setNotice('Strategy bot deleted');
        } catch (error) { setActionError(err(error)); }
    }

    async function requestRun(event: FormEvent<HTMLFormElement>) {
        event.preventDefault(); if (!selectedBotId) { setActionError('Select a strategy bot before requesting a run'); return; }
        setRequestingRun(true); setActionError(null); setNotice(null);
        try {
            const response = await apiFetch(`/api/v1/strategy-bots/${selectedBotId}/runs`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ runMode: runForm.runMode, initialCapital: numberOrUndefined(runForm.initialCapital), fromDate: runForm.fromDate || null, toDate: runForm.toDate || null }) });
            if (!response.ok) throw new Error(await response.text() || `Run request failed (${response.status})`);
            const createdRun = await response.json() as StrategyBotRun;
            setRunForm({ runMode: 'BACKTEST', initialCapital: '', fromDate: '', toDate: '' });
            await loadRuns(selectedBotId);
            setSelectedRunId(createdRun.id);
            setNotice('Run queued');
        } catch (error) { setActionError(err(error)); } finally { setRequestingRun(false); }
    }

    async function executeRun(runId: string) {
        if (!selectedBotId) return;
        setExecutingRunId(runId); setActionError(null); setNotice(null);
        try {
            const response = await apiFetch(`/api/v1/strategy-bots/${selectedBotId}/runs/${runId}/execute`, { method: 'POST' });
            if (!response.ok) throw new Error(await response.text() || `Run execute failed (${response.status})`);
            await loadRuns(selectedBotId);
            setSelectedRunId(runId);
            setNotice('Run executed');
        } catch (error) { setActionError(err(error)); } finally { setExecutingRunId(null); }
    }

    async function refreshRun(runId: string) {
        if (!selectedBotId) return;
        setRefreshingRunId(runId); setActionError(null); setNotice(null);
        try {
            const response = await apiFetch(`/api/v1/strategy-bots/${selectedBotId}/runs/${runId}/refresh`, { method: 'POST' });
            if (!response.ok) throw new Error(await response.text() || `Run refresh failed (${response.status})`);
            await loadRuns(selectedBotId);
            setSelectedRunId(runId);
            await loadRunOutputs(selectedBotId, runId);
            setNotice('Forward test refreshed');
        } catch (error) { setActionError(err(error)); } finally { setRefreshingRunId(null); }
    }

    return (
        <div className="p-8 pb-20 text-white">
            <header className="rounded-3xl border border-white/10 bg-black/40 p-6 shadow-2xl backdrop-blur-xl">
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                    <div>
                        <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Strategy Workspace</p>
                        <h1 className="mt-2 bg-gradient-to-r from-cyan-300 via-emerald-300 to-lime-300 bg-clip-text text-3xl font-black text-transparent">Paper bots, deterministic rules, audited runs.</h1>
                        <p className="mt-3 max-w-3xl text-sm leading-7 text-zinc-400">Build rule-based paper bots, link them to owned portfolios, queue backtests, and inspect compiler plus execution summaries from one dashboard surface.</p>
                    </div>
                    <div className="flex flex-wrap gap-3">
                        <Link href="/dashboard" className="rounded-full border border-white/10 bg-white/5 px-4 py-2 text-xs font-semibold uppercase tracking-[0.24em] text-zinc-300 transition hover:text-white">Dashboard</Link>
                        <Link href="/watchlist" className="rounded-full border border-white/10 bg-white/5 px-4 py-2 text-xs font-semibold uppercase tracking-[0.24em] text-zinc-300 transition hover:text-white">Markets</Link>
                    </div>
                </div>
            </header>

            <section className="mt-8 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <div className="rounded-2xl border border-white/10 bg-black/35 px-5 py-4 backdrop-blur-xl">
                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Bots</p>
                    <p className="mt-2 text-2xl font-bold text-white">{bots.length}</p>
                    <p className="mt-1 text-[11px] text-zinc-500">{bots.filter((bot) => bot.status === 'READY').length} ready for runs</p>
                </div>
                <div className="rounded-2xl border border-white/10 bg-black/35 px-5 py-4 backdrop-blur-xl">
                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Runs</p>
                    <p className="mt-2 text-2xl font-bold text-white">{runs.length}</p>
                    <p className="mt-1 text-[11px] text-zinc-500">{runs.filter((run) => run.status === 'COMPLETED').length} completed</p>
                </div>
                <div className="rounded-2xl border border-white/10 bg-black/35 px-5 py-4 backdrop-blur-xl">
                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Portfolios</p>
                    <p className="mt-2 text-2xl font-bold text-white">{portfolios.length}</p>
                    <p className="mt-1 text-[11px] text-zinc-500">paper accounts available</p>
                </div>
                <div className="rounded-2xl border border-white/10 bg-black/35 px-5 py-4 backdrop-blur-xl">
                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Selection</p>
                    <p className="mt-2 truncate text-xl font-bold text-white">{selectedBot?.name ?? 'None selected'}</p>
                    <p className="mt-1 text-[11px] text-zinc-500">{selectedBot ? `${selectedBot.symbol} ${selectedBot.timeframe}` : 'choose a bot'}</p>
                </div>
            </section>

            <section className="mt-8 rounded-2xl border border-white/10 bg-black/30 p-6 backdrop-blur-xl">
                <div className="flex flex-wrap gap-2">
                    {(['OVERVIEW', 'BOTS', 'RUNS'] as const).map((item) => (
                        <button
                            key={item}
                            type="button"
                            onClick={() => setTab(item)}
                            className={`rounded-full border px-4 py-2 text-xs font-semibold uppercase tracking-[0.22em] transition ${
                                tab === item
                                    ? 'border-cyan-500/35 bg-cyan-500/15 text-cyan-100'
                                    : 'border-white/10 bg-white/5 text-zinc-400 hover:text-white'
                            }`}
                        >
                            {item}
                        </button>
                    ))}
                </div>
            </section>

            {(pageError || actionError || notice) && (
                <section className="mt-6 space-y-3">
                    {pageError && <div className="rounded-2xl border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-100">{pageError}</div>}
                    {actionError && <div className="rounded-2xl border border-amber-500/20 bg-amber-500/10 px-4 py-3 text-sm text-amber-100">{actionError}</div>}
                    {notice && <div className="rounded-2xl border border-emerald-500/20 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-100">{notice}</div>}
                </section>
            )}

            {loading && (
                <section className="mt-8 grid gap-6 lg:grid-cols-2">
                    <div className="h-96 animate-pulse rounded-3xl bg-white/5" />
                    <div className="h-96 animate-pulse rounded-3xl bg-white/5" />
                </section>
            )}

            {!loading && tab === 'OVERVIEW' && (
                <section className="mt-8 grid gap-6 xl:grid-cols-2">
                    <div className="rounded-3xl border border-white/10 bg-black/35 p-6 backdrop-blur-xl">
                        <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Workflow</p>
                        <h2 className="mt-3 text-xl font-bold text-white">Deterministic before autonomous.</h2>
                        <div className="mt-5 space-y-3 text-sm leading-7 text-zinc-300">
                            <p>1. Create a bot with supported rule tokens.</p>
                            <p>2. Link it to an owned paper portfolio and move it to `READY` when the risk profile is sane.</p>
                            <p>3. Queue a backtest, review compiler warnings, then execute and inspect fills plus equity curve summary.</p>
                        </div>
                    </div>
                    <div className="rounded-3xl border border-white/10 bg-black/35 p-6 backdrop-blur-xl">
                        <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Latest Snapshot</p>
                        <h2 className="mt-3 text-xl font-bold text-white">{selectedBot?.name ?? 'No bot selected'}</h2>
                        <div className="mt-5 grid gap-4 md:grid-cols-2">
                            <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                <p className="text-xs uppercase tracking-wide text-zinc-500">Run Status</p>
                                <p className="mt-2 text-lg font-bold text-white">{latestRun?.status ?? 'No run yet'}</p>
                                <p className="mt-1 text-xs text-zinc-500">{latestRun ? fmtDate(latestRun.requestedAt) : 'Request the first run from Runs tab.'}</p>
                            </div>
                            <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                <p className="text-xs uppercase tracking-wide text-zinc-500">Return</p>
                                <p className="mt-2 text-lg font-bold text-emerald-300">{fmtPercent(latestRun?.summary?.returnPercent)}</p>
                                <p className="mt-1 text-xs text-zinc-500">PnL {fmtCurrency(latestRun?.summary?.netPnl)}</p>
                            </div>
                            <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                <p className="text-xs uppercase tracking-wide text-zinc-500">Compiler</p>
                                <p className="mt-2 text-lg font-bold text-white">{latestRun?.summary?.executionEngineReady ? 'Executable' : 'Review warnings'}</p>
                                <p className="mt-1 text-xs text-zinc-500">{(latestRun?.summary?.unsupportedRules ?? []).length} unsupported rules</p>
                            </div>
                            <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                <p className="text-xs uppercase tracking-wide text-zinc-500">Drawdown</p>
                                <p className="mt-2 text-lg font-bold text-amber-300">{fmtPercent(latestRun?.summary?.maxDrawdownPercent)}</p>
                                <p className="mt-1 text-xs text-zinc-500">{latestRun?.summary?.tradeCount ?? 0} trades</p>
                            </div>
                        </div>
                    </div>
                </section>
            )}

            {!loading && tab === 'BOTS' && (
                <section className="mt-8 grid gap-6 xl:grid-cols-[0.95fr_1.05fr]">
                    <div className="rounded-3xl border border-white/10 bg-black/35 p-6 backdrop-blur-xl">
                        <div className="flex items-start justify-between gap-4">
                            <div>
                                <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Builder</p>
                                <h2 className="mt-2 text-xl font-bold text-white">{editingBotId ? 'Edit bot' : 'Create bot'}</h2>
                            </div>
                            {editingBotId && <button type="button" onClick={resetBotForm} className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-[11px] font-bold uppercase tracking-[0.2em] text-zinc-300">Reset</button>}
                        </div>
                        <form className="mt-6 space-y-4" onSubmit={saveBot}>
                            <div className="grid gap-4 md:grid-cols-2">
                                <input value={botForm.name} onChange={(event) => setBotForm((current) => ({ ...current, name: event.target.value }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" placeholder="BTC pullback bot" required />
                                <select value={botForm.linkedPortfolioId} onChange={(event) => setBotForm((current) => ({ ...current, linkedPortfolioId: event.target.value }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" required>
                                    {portfolios.map((portfolio) => <option key={portfolio.id} value={portfolio.id}>{portfolio.name} ({portfolio.visibility ?? 'PRIVATE'})</option>)}
                                </select>
                            </div>
                            <textarea value={botForm.description} onChange={(event) => setBotForm((current) => ({ ...current, description: event.target.value }))} className="min-h-20 w-full rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" placeholder="Mean reversion with explicit risk exits." />
                            <div className="grid gap-4 md:grid-cols-4">
                                <input value={botForm.market} onChange={(event) => setBotForm((current) => ({ ...current, market: event.target.value.toUpperCase() }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" placeholder="Market" required />
                                <input value={botForm.symbol} onChange={(event) => setBotForm((current) => ({ ...current, symbol: event.target.value.toUpperCase() }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" placeholder="Symbol" required />
                                <input value={botForm.timeframe} onChange={(event) => setBotForm((current) => ({ ...current, timeframe: event.target.value }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" placeholder="Timeframe" required />
                                <select value={botForm.status} onChange={(event) => setBotForm((current) => ({ ...current, status: event.target.value as BotStatus }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40">
                                    <option value="DRAFT">DRAFT</option>
                                    <option value="READY">READY</option>
                                    <option value="ARCHIVED">ARCHIVED</option>
                                </select>
                            </div>
                            <div className="grid gap-4 md:grid-cols-4">
                                <input value={botForm.maxPositionSizePercent} onChange={(event) => setBotForm((current) => ({ ...current, maxPositionSizePercent: event.target.value }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" placeholder="Max position %" />
                                <input value={botForm.stopLossPercent} onChange={(event) => setBotForm((current) => ({ ...current, stopLossPercent: event.target.value }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" placeholder="Stop loss %" />
                                <input value={botForm.takeProfitPercent} onChange={(event) => setBotForm((current) => ({ ...current, takeProfitPercent: event.target.value }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" placeholder="Take profit %" />
                                <input value={botForm.cooldownMinutes} onChange={(event) => setBotForm((current) => ({ ...current, cooldownMinutes: event.target.value }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" placeholder="Cooldown min" />
                            </div>
                            <div className="grid gap-4 lg:grid-cols-2">
                                <textarea value={botForm.entryRulesText} onChange={(event) => setBotForm((current) => ({ ...current, entryRulesText: event.target.value }))} className="min-h-52 rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 font-mono text-xs text-white outline-none focus:border-cyan-500/40" />
                                <textarea value={botForm.exitRulesText} onChange={(event) => setBotForm((current) => ({ ...current, exitRulesText: event.target.value }))} className="min-h-52 rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 font-mono text-xs text-white outline-none focus:border-cyan-500/40" />
                            </div>
                            <div className="flex flex-wrap gap-3">
                                <button type="submit" disabled={saving || portfolios.length === 0} className="rounded-2xl border border-cyan-500/30 bg-cyan-500/15 px-5 py-3 text-sm font-bold text-cyan-100 disabled:opacity-60">{saving ? 'Saving...' : editingBotId ? 'Update Bot' : 'Create Bot'}</button>
                                <button type="button" onClick={resetBotForm} className="rounded-2xl border border-white/10 bg-white/5 px-5 py-3 text-sm font-semibold text-zinc-300">Clear</button>
                            </div>
                        </form>
                    </div>
                    <div className="rounded-3xl border border-white/10 bg-black/35 p-6 backdrop-blur-xl">
                        <div className="flex items-start justify-between gap-4">
                            <div>
                                <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Bot List</p>
                                <h2 className="mt-2 text-xl font-bold text-white">Drafts, ready bots, archives</h2>
                            </div>
                            <button type="button" onClick={() => userId && void bootstrap(userId)} className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-[11px] font-bold uppercase tracking-[0.2em] text-zinc-300">Refresh</button>
                        </div>
                        <div className="mt-6 space-y-4">
                            {bots.length === 0 ? (
                                <div className="rounded-2xl border border-dashed border-white/10 bg-black/20 px-5 py-8 text-sm text-zinc-400">No strategy bots yet.</div>
                            ) : bots.map((bot) => (
                                <div key={bot.id} className={`rounded-2xl border px-5 py-4 ${selectedBotId === bot.id ? 'border-cyan-500/35 bg-cyan-500/10' : 'border-white/10 bg-black/20'}`}>
                                    <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                                        <div className="min-w-0">
                                            <div className="flex flex-wrap items-center gap-2">
                                                <p className="text-lg font-bold text-white">{bot.name}</p>
                                                <span className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${bot.status === 'READY' ? 'bg-emerald-500/15 text-emerald-200' : bot.status === 'ARCHIVED' ? 'bg-zinc-500/15 text-zinc-300' : 'bg-amber-500/15 text-amber-200'}`}>{bot.status}</span>
                                            </div>
                                            <p className="mt-1 text-sm text-zinc-400">{bot.description || 'No description yet.'}</p>
                                            <p className="mt-2 text-xs text-zinc-500">{bot.market} / {bot.symbol} / {bot.timeframe} / updated {fmtDate(bot.updatedAt)}</p>
                                        </div>
                                        <div className="flex flex-wrap gap-2">
                                            <button type="button" onClick={() => { setSelectedBotId(bot.id); setTab('RUNS'); }} className="rounded-xl border border-cyan-500/20 bg-cyan-500/10 px-3 py-2 text-xs font-semibold text-cyan-100">Runs</button>
                                            <button type="button" onClick={() => { setEditingBotId(bot.id); setBotForm({ name: bot.name, description: bot.description ?? '', linkedPortfolioId: bot.linkedPortfolioId, market: bot.market, symbol: bot.symbol, timeframe: bot.timeframe, status: bot.status, maxPositionSizePercent: bot.maxPositionSizePercent?.toString() ?? '', stopLossPercent: bot.stopLossPercent?.toString() ?? '', takeProfitPercent: bot.takeProfitPercent?.toString() ?? '', cooldownMinutes: bot.cooldownMinutes?.toString() ?? '', entryRulesText: pretty(bot.entryRules), exitRulesText: pretty(bot.exitRules) }); setNotice(`Editing ${bot.name}`); setActionError(null); }} className="rounded-xl border border-white/10 bg-white/5 px-3 py-2 text-xs font-semibold text-zinc-200">Edit</button>
                                            <button type="button" onClick={() => void deleteBot(bot.id)} className="rounded-xl border border-red-500/20 bg-red-500/10 px-3 py-2 text-xs font-semibold text-red-100">Delete</button>
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                </section>
            )}

            {!loading && tab === 'RUNS' && (
                <section className="mt-8 grid gap-6 xl:grid-cols-[0.85fr_1.15fr]">
                    <div className="rounded-3xl border border-white/10 bg-black/35 p-6 backdrop-blur-xl">
                        <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Run Control</p>
                        <h2 className="mt-2 text-xl font-bold text-white">{selectedBot?.name ?? 'Select a bot first'}</h2>
                        <div className="mt-5 space-y-4">
                            <select value={selectedBotId} onChange={(event) => setSelectedBotId(event.target.value)} className="w-full rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40">
                                <option value="">Select bot</option>
                                {bots.map((bot) => <option key={bot.id} value={bot.id}>{bot.name} ({bot.status})</option>)}
                            </select>
                            {selectedBot && (
                                <div className="grid gap-3 md:grid-cols-2">
                                    <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                        <p className="text-xs uppercase tracking-wide text-zinc-500">Risk Envelope</p>
                                        <p className="mt-2 text-sm text-zinc-300">{fmtPercent(selectedBot.maxPositionSizePercent)} max position, stop {fmtPercent(selectedBot.stopLossPercent)}, take {fmtPercent(selectedBot.takeProfitPercent)}</p>
                                    </div>
                                    <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                        <p className="text-xs uppercase tracking-wide text-zinc-500">Compiler Input</p>
                                        <p className="mt-2 text-sm text-zinc-300">{pretty(selectedBot.entryRules).split('\n')[0]}</p>
                                    </div>
                                </div>
                            )}
                            <form className="space-y-4" onSubmit={requestRun}>
                                <div className="grid gap-4 md:grid-cols-2">
                                    <select value={runForm.runMode} onChange={(event) => setRunForm((current) => ({ ...current, runMode: event.target.value as RunMode }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40">
                                        <option value="BACKTEST">BACKTEST</option>
                                        <option value="FORWARD_TEST">FORWARD_TEST</option>
                                    </select>
                                    <input value={runForm.initialCapital} onChange={(event) => setRunForm((current) => ({ ...current, initialCapital: event.target.value }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" placeholder="Initial capital (optional)" />
                                </div>
                                <div className="grid gap-4 md:grid-cols-2">
                                    <input type="date" value={runForm.fromDate} onChange={(event) => setRunForm((current) => ({ ...current, fromDate: event.target.value }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" />
                                    <input type="date" value={runForm.toDate} onChange={(event) => setRunForm((current) => ({ ...current, toDate: event.target.value }))} className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none focus:border-cyan-500/40" />
                                </div>
                                <div className="flex flex-wrap gap-3">
                                    <button type="submit" disabled={!selectedBotId || requestingRun} className="rounded-2xl border border-emerald-500/30 bg-emerald-500/15 px-5 py-3 text-sm font-bold text-emerald-100 disabled:opacity-60">{requestingRun ? 'Queueing...' : 'Request Run'}</button>
                                    <button type="button" onClick={() => setRunForm({ runMode: 'BACKTEST', initialCapital: '', fromDate: '', toDate: '' })} className="rounded-2xl border border-white/10 bg-white/5 px-5 py-3 text-sm font-semibold text-zinc-300">Reset</button>
                                </div>
                            </form>
                        </div>
                    </div>
                    <div className="rounded-3xl border border-white/10 bg-black/35 p-6 backdrop-blur-xl">
                        <div className="flex items-start justify-between gap-4">
                            <div>
                                <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Run Journal</p>
                                <h2 className="mt-2 text-xl font-bold text-white">Queued, executed, and summarized runs</h2>
                            </div>
                            <button type="button" onClick={() => selectedBotId && void loadRuns(selectedBotId)} className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-[11px] font-bold uppercase tracking-[0.2em] text-zinc-300">Refresh</button>
                        </div>
                        <div className="mt-6 space-y-4">
                            {!selectedBotId ? (
                                <div className="rounded-2xl border border-dashed border-white/10 bg-black/20 px-5 py-8 text-sm text-zinc-400">Select a bot to load runs.</div>
                            ) : runs.length === 0 ? (
                                <div className="rounded-2xl border border-dashed border-white/10 bg-black/20 px-5 py-8 text-sm text-zinc-400">No runs yet for this bot.</div>
                            ) : runs.map((run) => (
                                <div key={run.id} className={`rounded-2xl border p-5 ${selectedRunId === run.id ? 'border-cyan-500/35 bg-cyan-500/10' : 'border-white/10 bg-black/20'}`}>
                                    <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                                        <div>
                                            <div className="flex flex-wrap items-center gap-2">
                                                <p className="text-lg font-bold text-white">{run.runMode}</p>
                                                <span className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${run.status === 'COMPLETED' ? 'bg-emerald-500/15 text-emerald-200' : run.status === 'FAILED' ? 'bg-red-500/15 text-red-200' : run.status === 'RUNNING' ? 'bg-cyan-500/15 text-cyan-200' : 'bg-amber-500/15 text-amber-200'}`}>{run.status}</span>
                                            </div>
                                            <p className="mt-1 text-xs text-zinc-500">Requested {fmtDate(run.requestedAt)}</p>
                                            <p className="mt-2 text-sm text-zinc-400">Window {run.fromDate ?? 'auto'} to {run.toDate ?? 'auto'} | Equity {fmtCurrency(run.summary?.endingEquity)}</p>
                                        </div>
                                        <div className="flex flex-wrap gap-2">
                                            {run.status === 'QUEUED' && (
                                                <button type="button" onClick={() => void executeRun(run.id)} disabled={executingRunId === run.id} className="rounded-xl border border-cyan-500/20 bg-cyan-500/10 px-3 py-2 text-xs font-semibold text-cyan-100 disabled:opacity-60">
                                                    {executingRunId === run.id ? 'Executing...' : run.runMode === 'FORWARD_TEST' ? 'Start Forward Test' : 'Execute Backtest'}
                                                </button>
                                            )}
                                            {run.runMode === 'FORWARD_TEST' && run.status === 'RUNNING' && (
                                                <button type="button" onClick={() => void refreshRun(run.id)} disabled={refreshingRunId === run.id} className="rounded-xl border border-emerald-500/20 bg-emerald-500/10 px-3 py-2 text-xs font-semibold text-emerald-100 disabled:opacity-60">
                                                    {refreshingRunId === run.id ? 'Refreshing...' : 'Refresh Snapshot'}
                                                </button>
                                            )}
                                            <button type="button" onClick={() => setSelectedRunId(run.id)} className="rounded-xl border border-white/10 bg-white/5 px-3 py-2 text-xs font-semibold text-zinc-200">
                                                {selectedRunId === run.id ? 'Selected' : 'Inspect'}
                                            </button>
                                        </div>
                                    </div>
                                    {run.errorMessage && <div className="mt-4 rounded-xl border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-100">{run.errorMessage}</div>}
                                    <div className="mt-4 grid gap-3 md:grid-cols-4">
                                        <div className="rounded-xl border border-white/5 bg-black/20 p-3"><p className="text-[10px] uppercase tracking-[0.22em] text-zinc-500">Return</p><p className="mt-2 text-sm font-bold text-emerald-300">{fmtPercent(run.summary?.returnPercent)}</p></div>
                                        <div className="rounded-xl border border-white/5 bg-black/20 p-3"><p className="text-[10px] uppercase tracking-[0.22em] text-zinc-500">PnL</p><p className="mt-2 text-sm font-bold text-white">{fmtCurrency(run.summary?.netPnl)}</p></div>
                                        <div className="rounded-xl border border-white/5 bg-black/20 p-3"><p className="text-[10px] uppercase tracking-[0.22em] text-zinc-500">Trades</p><p className="mt-2 text-sm font-bold text-white">{run.summary?.tradeCount ?? 0}</p></div>
                                        <div className="rounded-xl border border-white/5 bg-black/20 p-3"><p className="text-[10px] uppercase tracking-[0.22em] text-zinc-500">Drawdown</p><p className="mt-2 text-sm font-bold text-amber-300">{fmtPercent(run.summary?.maxDrawdownPercent)}</p></div>
                                    </div>
                                    {selectedRunId === run.id && (
                                        <div className="mt-4 grid gap-4 xl:grid-cols-[0.8fr_1.2fr]">
                                            <div className="space-y-4">
                                                <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Compiler</p>
                                                    <p className="mt-3 text-sm text-zinc-300">Ready: {selectedRun?.summary?.executionEngineReady ? 'Yes' : 'No'}</p>
                                                    <p className="mt-2 text-sm text-zinc-300">Features: {(selectedRun?.summary?.supportedFeatures ?? []).join(', ') || 'None'}</p>
                                                    <p className="mt-2 text-sm text-zinc-300">Unsupported: {(selectedRun?.summary?.unsupportedRules ?? []).join(', ') || 'None'}</p>
                                                    <p className="mt-2 text-sm text-zinc-300">Warnings: {(selectedRun?.summary?.warnings ?? []).join(' | ') || 'None'}</p>
                                                    {selectedRun?.runMode === 'FORWARD_TEST' && (
                                                        <>
                                                            <p className="mt-2 text-sm text-zinc-300">Last Evaluated: {selectedRun?.summary?.lastEvaluatedOpenTime ? fmtEpoch(selectedRun.summary.lastEvaluatedOpenTime) : 'N/A'}</p>
                                                            <p className="mt-2 text-sm text-zinc-300">Live Position: {selectedRun?.summary?.positionOpen ? `Open @ ${fmtCurrency(selectedRun?.summary?.openEntryPrice)} / ${selectedRun?.summary?.openQuantity?.toFixed(4)}` : 'Flat'}</p>
                                                        </>
                                                    )}
                                                </div>
                                                <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Persisted Fills</p>
                                                    {outputsLoading ? (
                                                        <div className="mt-3 h-24 animate-pulse rounded-xl bg-white/5" />
                                                    ) : selectedRunFills.length === 0 ? (
                                                        <p className="mt-3 text-sm text-zinc-500">No persisted fills yet for this run.</p>
                                                    ) : (
                                                        <div className="mt-3 space-y-2">
                                                            {selectedRunFills.slice(0, 6).map((fill) => (
                                                                <div key={fill.id} className="rounded-xl border border-white/5 bg-black/25 px-3 py-2 text-xs text-zinc-300">
                                                                    <div className="flex items-center justify-between gap-3">
                                                                        <span className={`font-bold ${fill.side === 'ENTRY' ? 'text-cyan-200' : 'text-emerald-200'}`}>{fill.side}</span>
                                                                        <span>{fmtEpoch(fill.openTime)}</span>
                                                                    </div>
                                                                    <div className="mt-2 flex flex-wrap gap-3 text-zinc-400">
                                                                        <span>Price {fmtCurrency(fill.price)}</span>
                                                                        <span>Qty {fill.quantity.toFixed(4)}</span>
                                                                        <span>PnL {fmtCurrency(fill.realizedPnl)}</span>
                                                                    </div>
                                                                </div>
                                                            ))}
                                                        </div>
                                                    )}
                                                </div>
                                                <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Outcome Quality</p>
                                                    <div className="mt-3 grid gap-3 sm:grid-cols-2">
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Avg Win</p>
                                                            <p className="mt-1 font-bold text-emerald-200">{fmtCurrency(selectedRun?.summary?.avgWinPnl)}</p>
                                                        </div>
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Avg Loss</p>
                                                            <p className="mt-1 font-bold text-red-200">{fmtCurrency(selectedRun?.summary?.avgLossPnl)}</p>
                                                        </div>
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Profit Factor</p>
                                                            <p className="mt-1 font-bold text-white">{selectedRun?.summary?.profitFactor?.toFixed(2) ?? 'N/A'}</p>
                                                        </div>
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Expectancy / Trade</p>
                                                            <p className="mt-1 font-bold text-white">{fmtCurrency(selectedRun?.summary?.expectancyPerTrade)}</p>
                                                        </div>
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Best Trade</p>
                                                            <p className="mt-1 font-bold text-emerald-200">{fmtCurrency(selectedRun?.summary?.bestTradePnl)}</p>
                                                        </div>
                                                        <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                            <p className="text-zinc-500">Worst Trade</p>
                                                            <p className="mt-1 font-bold text-red-200">{fmtCurrency(selectedRun?.summary?.worstTradePnl)}</p>
                                                        </div>
                                                    </div>
                                                </div>
                                            </div>
                                            <div className="space-y-4">
                                                <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Persisted Equity Curve</p>
                                                    {outputsLoading ? (
                                                        <div className="mt-3 h-32 animate-pulse rounded-xl bg-white/5" />
                                                    ) : selectedRunEquityCurve.length < 2 ? (
                                                        <p className="mt-3 text-sm text-zinc-500">No persisted equity points yet for this run.</p>
                                                    ) : (
                                                        <>
                                                            <div className="mt-3 overflow-hidden rounded-xl border border-white/5 bg-black/25 p-3">
                                                                <svg viewBox="0 0 320 96" className="h-32 w-full">
                                                                    <defs>
                                                                        <linearGradient id="bot-equity-fill" x1="0" x2="0" y1="0" y2="1">
                                                                            <stop offset="0%" stopColor="rgba(34,211,238,0.35)" />
                                                                            <stop offset="100%" stopColor="rgba(34,211,238,0.02)" />
                                                                        </linearGradient>
                                                                    </defs>
                                                                    <polyline
                                                                        fill="none"
                                                                        stroke="#67e8f9"
                                                                        strokeWidth="2.5"
                                                                        points={buildSparkline(selectedRunEquityCurve)}
                                                                    />
                                                                </svg>
                                                            </div>
                                                            <div className="mt-3 grid gap-3 md:grid-cols-3">
                                                                <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                                    <p className="text-zinc-500">Start</p>
                                                                    <p className="mt-1 font-bold text-white">{fmtCurrency(selectedRunEquityCurve[0]?.equity)}</p>
                                                                </div>
                                                                <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                                    <p className="text-zinc-500">End</p>
                                                                    <p className="mt-1 font-bold text-white">{fmtCurrency(selectedRunEquityCurve[selectedRunEquityCurve.length - 1]?.equity)}</p>
                                                                </div>
                                                                <div className="rounded-xl border border-white/5 bg-black/25 p-3 text-xs text-zinc-300">
                                                                    <p className="text-zinc-500">Points</p>
                                                                    <p className="mt-1 font-bold text-white">{selectedRunEquityCurve.length}</p>
                                                                </div>
                                                            </div>
                                                        </>
                                                    )}
                                                </div>
                                                <div className="rounded-2xl border border-white/5 bg-black/20 p-4">
                                                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Raw Outputs</p>
                                                    <div className="mt-3 max-h-56 overflow-auto rounded-xl border border-white/5 bg-black/25 p-3">
                                                        <pre className="whitespace-pre-wrap text-xs leading-6 text-zinc-300">{pretty({ fills: selectedRunFills, equityCurve: selectedRunEquityCurve })}</pre>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>
                </section>
            )}
        </div>
    );
}
