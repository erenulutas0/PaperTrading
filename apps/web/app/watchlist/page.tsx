'use client';


import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { apiFetch } from '../../lib/api-client';
import MarketWorkspaceChart from '../../components/MarketWorkspaceChart';

interface WatchlistItem {
    id: string;
    symbol: string;
    currentPrice: number;
    changePercent24h: number;
    alertPriceAbove: string | number;
    alertPriceBelow: string | number;
    alertAboveTriggered: boolean;
    alertBelowTriggered: boolean;
    notes: string;
}

interface Watchlist {
    id: string;
    name: string;
    userId: string;
    createdAt: string;
    items: WatchlistItem[];
}

interface InstrumentOption {
    symbol: string;
    displayName: string;
    assetType: string;
    currentPrice: number;
    changePercent24h: number;
}

interface CandlePoint {
    openTime: number;
    open: number;
    high: number;
    low: number;
    close: number;
    volume: number;
}

type ChartRange = '1D' | '1W' | '1M' | 'ALL';
type ChartInterval = '1m' | '15m' | '30m' | '1h' | '4h' | '1d';

const RANGE_OPTIONS: ChartRange[] = ['1D', '1W', '1M', 'ALL'];
const INTERVAL_OPTIONS: ChartInterval[] = ['1m', '15m', '30m', '1h', '4h', '1d'];
const ALL_HISTORY_CHUNK = 1000;

function getInitialAllHistoryChunkCount(interval: ChartInterval) {
    switch (interval) {
        case '1d':
            return 6;
        case '4h':
            return 5;
        case '1h':
            return 4;
        case '30m':
            return 3;
        case '15m':
            return 2;
        default:
            return 1;
    }
}

function formatMoney(value: number) {
    return `$${Number(value).toLocaleString(undefined, {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
    })}`;
}

function formatPercent(value: number) {
    const normalized = Number(value ?? 0);
    return `${normalized >= 0 ? '+' : ''}${normalized.toFixed(2)}%`;
}

export default function WatchlistPage() {
    const [watchlists, setWatchlists] = useState<Watchlist[]>([]);
    const [selectedWatchlist, setSelectedWatchlist] = useState<string | null>(null);
    const [enrichedItems, setEnrichedItems] = useState<WatchlistItem[]>([]);
    const [instrumentUniverse, setInstrumentUniverse] = useState<InstrumentOption[]>([]);
    const [instrumentQuery, setInstrumentQuery] = useState('');
    const [selectedSymbol, setSelectedSymbol] = useState<string>('BTCUSDT');
    const [selectedRange, setSelectedRange] = useState<ChartRange>('1D');
    const [selectedInterval, setSelectedInterval] = useState<ChartInterval>('1h');
    const [candles, setCandles] = useState<CandlePoint[]>([]);
    const [loading, setLoading] = useState(true);
    const [chartLoading, setChartLoading] = useState(false);
    const [loadingMoreHistory, setLoadingMoreHistory] = useState(false);
    const [hasMoreHistory, setHasMoreHistory] = useState(true);

    const [newName, setNewName] = useState('');
    const [addSymbol, setAddSymbol] = useState('BTCUSDT');
    const [addAlertAbove, setAddAlertAbove] = useState('');
    const [addAlertBelow, setAddAlertBelow] = useState('');
    const [addNotes, setAddNotes] = useState('');
    const [showAddForm, setShowAddForm] = useState(false);

    const currentUserId = typeof window !== 'undefined' ? localStorage.getItem('userId') : null;

    const selectedInstrument = useMemo(() => {
        return instrumentUniverse.find((item) => item.symbol === selectedSymbol)
            ?? enrichedItems.find((item) => item.symbol === selectedSymbol)
            ?? null;
    }, [enrichedItems, instrumentUniverse, selectedSymbol]);

    const selectedDisplayName = useMemo(() => {
        if (!selectedInstrument) {
            return selectedSymbol;
        }
        if ('displayName' in selectedInstrument) {
            return selectedInstrument.displayName;
        }
        return selectedInstrument.symbol;
    }, [selectedInstrument, selectedSymbol]);

    const selectedAssetType = useMemo(() => {
        if (!selectedInstrument) {
            return 'CRYPTO';
        }
        if ('assetType' in selectedInstrument) {
            return selectedInstrument.assetType;
        }
        return 'WATCHLIST';
    }, [selectedInstrument]);

    const loadedHistoryLabel = useMemo(() => {
        if (candles.length === 0) {
            return 'No candles loaded yet.';
        }
        const oldest = new Date(candles[0].openTime).toLocaleDateString();
        const newest = new Date(candles[candles.length - 1].openTime).toLocaleDateString();
        return `${oldest} → ${newest}`;
    }, [candles]);

    const filteredInstruments = useMemo(() => {
        const query = instrumentQuery.trim().toLowerCase();
        if (!query) {
            return instrumentUniverse;
        }
        return instrumentUniverse.filter((instrument) =>
            instrument.symbol.toLowerCase().includes(query)
            || instrument.displayName.toLowerCase().includes(query));
    }, [instrumentQuery, instrumentUniverse]);

    const availableSymbols = useMemo(() => {
        const existingSymbols = new Set(enrichedItems.map((item) => item.symbol));
        return instrumentUniverse.filter((item) => !existingSymbols.has(item.symbol) || item.symbol === addSymbol);
    }, [addSymbol, enrichedItems, instrumentUniverse]);

    const fetchWatchlists = useCallback(async () => {
        if (!currentUserId) {
            return;
        }
        try {
            const res = await apiFetch('/api/v1/watchlists');
            if (!res.ok) {
                return;
            }
            const data = await res.json();
            setWatchlists(data);
            if (data.length > 0 && !selectedWatchlist) {
                setSelectedWatchlist(data[0].id);
            }
        } catch (error) {
            console.error(error);
        } finally {
            setLoading(false);
        }
    }, [currentUserId, selectedWatchlist]);

    const fetchInstrumentUniverse = useCallback(async () => {
        try {
            const res = await apiFetch('/api/v1/market/instruments', { cache: 'no-store' });
            if (!res.ok) {
                return;
            }
            const data = await res.json();
            setInstrumentUniverse(data);
            if (Array.isArray(data) && data.length > 0) {
                setAddSymbol((current) => current || data[0].symbol);
                setSelectedSymbol((current) => current || data[0].symbol);
            }
        } catch (error) {
            console.error(error);
        }
    }, []);

    const fetchItems = useCallback(async () => {
        if (!selectedWatchlist || !currentUserId) {
            return;
        }
        try {
            const res = await apiFetch(`/api/v1/watchlists/${selectedWatchlist}/items`, { cache: 'no-store' });
            if (!res.ok) {
                return;
            }
            const data = await res.json();
            setEnrichedItems(data);
        } catch (error) {
            console.error(error);
        }
    }, [currentUserId, selectedWatchlist]);

    const fetchCandleChunk = useCallback(async (
        symbol: string,
        range: ChartRange,
        interval: ChartInterval,
        beforeOpenTime?: number | null,
    ): Promise<CandlePoint[]> => {
        const url = new URL('/api/v1/market/candles', window.location.origin);
        url.searchParams.set('symbol', symbol);
        url.searchParams.set('range', range);
        url.searchParams.set('interval', interval);
        if (range === 'ALL') {
            url.searchParams.set('limit', String(ALL_HISTORY_CHUNK));
        }
        if (beforeOpenTime) {
            url.searchParams.set('beforeOpenTime', String(beforeOpenTime));
        }

        const res = await apiFetch(`${url.pathname}${url.search}`, { cache: 'no-store' });
        if (!res.ok) {
            return [];
        }
        return res.json();
    }, []);

    const fetchCandles = useCallback(async (
        symbol: string,
        range: ChartRange,
        interval: ChartInterval,
        mode: 'reset' | 'prepend' = 'reset',
    ) => {
        if (!symbol) {
            return;
        }

        const beforeOpenTime = mode === 'prepend' && candles.length > 0 ? candles[0].openTime : null;

        if (mode === 'prepend') {
            setLoadingMoreHistory(true);
        } else {
            setChartLoading(true);
        }

        try {
            const nextCandles = await fetchCandleChunk(symbol, range, interval, beforeOpenTime);
            if (range === 'ALL') {
                setHasMoreHistory(nextCandles.length === ALL_HISTORY_CHUNK);
            } else {
                setHasMoreHistory(false);
            }

            if (mode === 'prepend') {
                setCandles((current) => {
                    const deduped = new Map<number, CandlePoint>();
                    [...nextCandles, ...current].forEach((candle) => deduped.set(candle.openTime, candle));
                    return Array.from(deduped.values()).sort((a, b) => a.openTime - b.openTime);
                });
                return;
            }

            if (range !== 'ALL') {
                setCandles(nextCandles);
                return;
            }

            let merged = [...nextCandles];
            let cursor = merged.length > 0 ? merged[0].openTime : null;
            let hasMore = nextCandles.length === ALL_HISTORY_CHUNK;
            let remainingChunks = getInitialAllHistoryChunkCount(interval) - 1;

            while (hasMore && cursor && remainingChunks > 0) {
                const olderChunk = await fetchCandleChunk(symbol, range, interval, cursor);
                if (olderChunk.length === 0) {
                    hasMore = false;
                    break;
                }
                const deduped = new Map<number, CandlePoint>();
                [...olderChunk, ...merged].forEach((candle) => deduped.set(candle.openTime, candle));
                merged = Array.from(deduped.values()).sort((a, b) => a.openTime - b.openTime);
                cursor = olderChunk[0]?.openTime ?? null;
                hasMore = olderChunk.length === ALL_HISTORY_CHUNK;
                remainingChunks -= 1;
            }

            setHasMoreHistory(hasMore);
            setCandles(merged);
        } catch (error) {
            console.error(error);
        } finally {
            setChartLoading(false);
            setLoadingMoreHistory(false);
        }
    }, [candles, fetchCandleChunk]);

    useEffect(() => {
        fetchWatchlists();
        fetchInstrumentUniverse();
    }, [fetchInstrumentUniverse, fetchWatchlists]);

    useEffect(() => {
        const interval = setInterval(fetchInstrumentUniverse, 10000);
        return () => clearInterval(interval);
    }, [fetchInstrumentUniverse]);

    useEffect(() => {
        if (!selectedWatchlist) {
            return;
        }
        fetchItems();
        const interval = setInterval(fetchItems, 5000);
        return () => clearInterval(interval);
    }, [fetchItems, selectedWatchlist]);

    useEffect(() => {
        if (selectedRange === 'ALL' && selectedInterval === '1m') {
            setSelectedInterval('15m');
        }
    }, [selectedInterval, selectedRange]);

    useEffect(() => {
        fetchCandles(selectedSymbol, selectedRange, selectedInterval, 'reset');
    }, [fetchCandles, selectedInterval, selectedRange, selectedSymbol]);

    const handleCreateWatchlist = async (event: React.FormEvent) => {
        event.preventDefault();
        if (!currentUserId) {
            return;
        }
        try {
            const res = await apiFetch('/api/v1/watchlists', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: newName || 'My Watchlist' }),
            });
            if (!res.ok) {
                return;
            }
            setNewName('');
            await fetchWatchlists();
        } catch (error) {
            console.error(error);
        }
    };

    const handleAddItem = async (event: React.FormEvent) => {
        event.preventDefault();
        if (!selectedWatchlist || !currentUserId) {
            return;
        }
        try {
            const res = await apiFetch(`/api/v1/watchlists/${selectedWatchlist}/items`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    symbol: addSymbol,
                    alertPriceAbove: addAlertAbove ? parseFloat(addAlertAbove) : null,
                    alertPriceBelow: addAlertBelow ? parseFloat(addAlertBelow) : null,
                    notes: addNotes || null,
                }),
            });
            if (!res.ok) {
                return;
            }
            setAddAlertAbove('');
            setAddAlertBelow('');
            setAddNotes('');
            setShowAddForm(false);
            await fetchItems();
            setSelectedSymbol(addSymbol);
        } catch (error) {
            console.error(error);
        }
    };

    const handleRemoveItem = async (itemId: string) => {
        try {
            const res = await apiFetch(`/api/v1/watchlists/items/${itemId}`, { method: 'DELETE' });
            if (!res.ok) {
                return;
            }
            await fetchItems();
        } catch (error) {
            console.error(error);
        }
    };

    const handleDeleteWatchlist = async (id: string) => {
        if (!currentUserId) {
            return;
        }
        try {
            const res = await apiFetch(`/api/v1/watchlists/${id}`, { method: 'DELETE' });
            if (!res.ok) {
                return;
            }
            if (selectedWatchlist === id) {
                setSelectedWatchlist(null);
                setEnrichedItems([]);
            }
            await fetchWatchlists();
        } catch (error) {
            console.error(error);
        }
    };

    const handleLoadMoreHistory = useCallback((oldestOpenTime: number) => {
        if (selectedRange !== 'ALL' || loadingMoreHistory || !hasMoreHistory || oldestOpenTime <= 0) {
            return;
        }
        fetchCandles(selectedSymbol, selectedRange, selectedInterval, 'prepend');
    }, [fetchCandles, hasMoreHistory, loadingMoreHistory, selectedInterval, selectedRange, selectedSymbol]);

    return (
        <div className="min-h-screen bg-[radial-gradient(circle_at_top_left,_rgba(245,158,11,0.10),_transparent_28%),radial-gradient(circle_at_bottom_right,_rgba(34,197,94,0.10),_transparent_28%),#040404] text-white">
            <nav className="sticky top-0 z-50 flex items-center justify-between border-b border-white/10 bg-black/55 px-6 py-4 backdrop-blur-md">
                <Link href="/dashboard" className="flex items-center gap-2 text-xl font-bold tracking-tight">
                    <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-green-500 font-bold text-black">P</div>
                    <span>PaperTrade<span className="text-green-500">Pro</span></span>
                </Link>
                <div className="flex items-center gap-4 text-sm font-medium text-zinc-400">
                    <Link href="/dashboard" className="transition-colors hover:text-white">Dashboard</Link>
                    <Link href="/dashboard/leaderboard" className="transition-colors hover:text-white">Leaderboard</Link>
                    <Link href="/discover" className="transition-colors hover:text-white">Discover</Link>
                    <Link href="/watchlist" className="text-white">Markets</Link>
                </div>
            </nav>

            <div className="mx-auto max-w-[1600px] px-6 py-8">
                <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
                    <div>
                        <h1 className="text-3xl font-bold">
                            <span className="bg-gradient-to-r from-amber-400 via-yellow-300 to-orange-500 bg-clip-text text-transparent">Markets</span> Terminal
                        </h1>
                        <p className="mt-2 text-sm text-zinc-400">
                            Enstruman degistir, interval sec, `ALL` history modunda sola kaydirarak daha eski mumlari yukle.
                        </p>
                    </div>
                    <form onSubmit={handleCreateWatchlist} className="flex gap-2">
                        <input
                            type="text"
                            value={newName}
                            onChange={(event) => setNewName(event.target.value)}
                            placeholder="New watchlist name..."
                            className="w-52 rounded-lg border border-zinc-700 bg-zinc-900 px-3 py-2 text-sm text-white outline-none transition-colors focus:border-amber-500"
                        />
                        <button type="submit" className="rounded-lg border border-amber-500/20 bg-amber-500/10 px-4 py-2 text-sm font-bold text-amber-400 transition-all hover:bg-amber-500/20">
                            + Create
                        </button>
                    </form>
                </div>

                {loading ? (
                    <div className="flex justify-center py-20">
                        <div className="h-8 w-8 animate-spin rounded-full border-2 border-amber-500 border-t-transparent" />
                    </div>
                ) : (
                    <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
                        <section className="space-y-5">
                            <div className="rounded-3xl border border-white/10 bg-black/40 p-5 shadow-[0_0_60px_rgba(0,0,0,0.4)] backdrop-blur-xl">
                                <div className="flex flex-col gap-5">
                                    <div className="flex flex-wrap items-start justify-between gap-4">
                                        <div>
                                            <p className="text-[11px] uppercase tracking-[0.3em] text-zinc-500">Instrument</p>
                                            <h2 className="mt-2 text-3xl font-bold text-white">{selectedDisplayName}</h2>
                                            <p className="mt-1 text-sm text-zinc-500">{selectedSymbol} · {selectedAssetType}</p>
                                        </div>
                                        <div className="text-right">
                                            <p className="text-[11px] uppercase tracking-[0.3em] text-zinc-500">Spot</p>
                                            <p className="mt-2 font-mono text-3xl font-bold text-white">{formatMoney(Number(selectedInstrument?.currentPrice ?? 0))}</p>
                                            <p className={`mt-1 text-sm font-semibold ${Number(selectedInstrument?.changePercent24h ?? 0) >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>
                                                {formatPercent(Number(selectedInstrument?.changePercent24h ?? 0))} 24h
                                            </p>
                                        </div>
                                    </div>

                                    <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_auto_auto]">
                                        <input
                                            type="text"
                                            value={instrumentQuery}
                                            onChange={(event) => setInstrumentQuery(event.target.value)}
                                            placeholder="Search BTC, ETH, Solana..."
                                            className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none transition-colors focus:border-amber-400"
                                        />
                                        <div className="flex flex-wrap gap-2">
                                            {RANGE_OPTIONS.map((range) => (
                                                <button
                                                    key={range}
                                                    onClick={() => setSelectedRange(range)}
                                                    className={`rounded-full border px-4 py-2 text-xs font-bold uppercase tracking-[0.2em] transition ${selectedRange === range
                                                        ? 'border-amber-400/40 bg-amber-400/10 text-amber-300'
                                                        : 'border-white/10 bg-white/[0.03] text-zinc-400 hover:text-white'}`}
                                                >
                                                    {range}
                                                </button>
                                            ))}
                                        </div>
                                        <div className="flex flex-wrap gap-2">
                                            {INTERVAL_OPTIONS.map((interval) => {
                                                const disabled = selectedRange === 'ALL' && interval === '1m';
                                                return (
                                                    <button
                                                        key={interval}
                                                        onClick={() => setSelectedInterval(interval)}
                                                        disabled={disabled}
                                                        className={`rounded-full border px-4 py-2 text-xs font-bold uppercase tracking-[0.2em] transition ${selectedInterval === interval
                                                            ? 'border-green-400/40 bg-green-400/10 text-green-300'
                                                            : 'border-white/10 bg-white/[0.03] text-zinc-400 hover:text-white'} ${disabled ? 'cursor-not-allowed opacity-40' : ''}`}
                                                    >
                                                        {interval}
                                                    </button>
                                                );
                                            })}
                                        </div>
                                    </div>

                                    <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_320px]">
                                        <div className="rounded-3xl border border-white/8 bg-zinc-950/60 p-4">
                                            {(chartLoading && candles.length === 0) ? (
                                                <div className="flex h-[520px] items-center justify-center">
                                                    <div className="h-8 w-8 animate-spin rounded-full border-2 border-amber-400 border-t-transparent" />
                                                </div>
                                            ) : (
                                                <MarketWorkspaceChart
                                                    data={candles}
                                                    resetKey={`${selectedSymbol}-${selectedRange}-${selectedInterval}`}
                                                    onReachStart={handleLoadMoreHistory}
                                                />
                                            )}
                                        </div>

                                        <div className="rounded-3xl border border-white/8 bg-zinc-950/55 p-4">
                                            <p className="text-[11px] uppercase tracking-[0.3em] text-zinc-500">Instrument Universe</p>
                                            <p className="mt-2 text-sm text-zinc-400">Chart sembolunu watchlist'e eklemeden de dogrudan degistirebilirsin.</p>
                                            <div className="mt-4 max-h-[520px] space-y-2 overflow-y-auto pr-1">
                                                {filteredInstruments.map((instrument) => (
                                                    <button
                                                        key={instrument.symbol}
                                                        onClick={() => setSelectedSymbol(instrument.symbol)}
                                                        className={`w-full rounded-2xl border p-3 text-left transition ${selectedSymbol === instrument.symbol
                                                            ? 'border-amber-400/35 bg-amber-400/10'
                                                            : 'border-white/10 bg-white/[0.02] hover:border-white/20'}`}
                                                    >
                                                        <div className="flex items-center justify-between gap-3">
                                                            <div>
                                                                <p className="text-sm font-semibold text-white">{instrument.displayName}</p>
                                                                <p className="text-[11px] font-mono text-zinc-500">{instrument.symbol}</p>
                                                            </div>
                                                            <div className="text-right">
                                                                <p className="font-mono text-sm text-white">{formatMoney(instrument.currentPrice)}</p>
                                                                <p className={`text-[11px] font-semibold ${instrument.changePercent24h >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>
                                                                    {formatPercent(instrument.changePercent24h)}
                                                                </p>
                                                            </div>
                                                        </div>
                                                    </button>
                                                ))}
                                            </div>
                                        </div>
                                    </div>

                                    <div className="grid gap-4 md:grid-cols-4">
                                        <article className="rounded-2xl border border-white/10 bg-black/35 p-4 backdrop-blur-xl">
                                            <p className="text-[11px] uppercase tracking-[0.25em] text-zinc-500">Range</p>
                                            <p className="mt-2 text-xl font-semibold text-white">{selectedRange}</p>
                                            <p className="mt-1 text-xs text-zinc-500">Quick window switch.</p>
                                        </article>
                                        <article className="rounded-2xl border border-white/10 bg-black/35 p-4 backdrop-blur-xl">
                                            <p className="text-[11px] uppercase tracking-[0.25em] text-zinc-500">Interval</p>
                                            <p className="mt-2 text-xl font-semibold text-white">{selectedInterval}</p>
                                            <p className="mt-1 text-xs text-zinc-500">TradingView-style resolution control.</p>
                                        </article>
                                        <article className="rounded-2xl border border-white/10 bg-black/35 p-4 backdrop-blur-xl">
                                            <p className="text-[11px] uppercase tracking-[0.25em] text-zinc-500">Watchlists</p>
                                            <p className="mt-2 text-xl font-semibold text-white">{watchlists.length}</p>
                                            <p className="mt-1 text-xs text-zinc-500">Separate baskets by thesis or regime.</p>
                                        </article>
                                        <article className="rounded-2xl border border-white/10 bg-black/35 p-4 backdrop-blur-xl">
                                            <p className="text-[11px] uppercase tracking-[0.25em] text-zinc-500">History</p>
                                            <p className="mt-2 text-xl font-semibold text-white">{candles.length}</p>
                                            <p className="mt-1 text-xs text-zinc-500">
                                                {selectedRange === 'ALL'
                                                    ? (hasMoreHistory ? 'Scroll left to load older candles.' : 'Loaded oldest available chunk.')
                                                    : 'Window sized to selected range.'}
                                            </p>
                                            <p className="mt-2 text-[11px] font-mono text-zinc-600">{loadedHistoryLabel}</p>
                                        </article>
                                    </div>

                                    {selectedRange === 'ALL' && (
                                        <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-white/10 bg-white/[0.03] px-4 py-3">
                                            <div>
                                                <p className="text-xs font-semibold uppercase tracking-[0.22em] text-zinc-400">All History Mode</p>
                                                <p className="mt-1 text-sm text-zinc-500">
                                                    Finer intervals load progressively. Use `Load older` for deeper history when you need more candles.
                                                </p>
                                            </div>
                                            <button
                                                onClick={() => fetchCandles(selectedSymbol, selectedRange, selectedInterval, 'prepend')}
                                                disabled={loadingMoreHistory || !hasMoreHistory}
                                                className="rounded-full border border-amber-400/30 bg-amber-400/10 px-4 py-2 text-xs font-bold uppercase tracking-[0.2em] text-amber-300 transition hover:bg-amber-400/20 disabled:cursor-not-allowed disabled:opacity-40"
                                            >
                                                {loadingMoreHistory ? 'Loading...' : (hasMoreHistory ? 'Load Older' : 'No More Data')}
                                            </button>
                                        </div>
                                    )}
                                </div>
                            </div>
                        </section>

                        <aside className="rounded-3xl border border-white/10 bg-black/45 p-5 shadow-[0_0_60px_rgba(0,0,0,0.4)] backdrop-blur-xl">
                            <div className="mb-5 flex items-start justify-between gap-4">
                                <div>
                                    <p className="text-[11px] uppercase tracking-[0.3em] text-zinc-500">Watch Basket</p>
                                    <h2 className="mt-2 text-xl font-bold text-white">Right Rail</h2>
                                </div>
                                <button
                                    onClick={() => setShowAddForm(!showAddForm)}
                                    className="rounded-full border border-green-500/20 bg-green-500/10 px-3 py-2 text-[11px] font-bold uppercase tracking-[0.2em] text-green-400 transition hover:bg-green-500/20"
                                >
                                    + Add
                                </button>
                            </div>

                            <div className="space-y-2">
                                {watchlists.length === 0 ? (
                                    <p className="text-sm italic text-zinc-500">No watchlists yet. Create one.</p>
                                ) : (
                                    watchlists.map((watchlist) => (
                                        <div
                                            key={watchlist.id}
                                            className={`group flex cursor-pointer items-center justify-between rounded-2xl border p-3 transition ${selectedWatchlist === watchlist.id
                                                ? 'border-amber-400/30 bg-amber-400/10'
                                                : 'border-white/10 hover:border-white/20 hover:bg-white/[0.03]'}`}
                                            onClick={() => setSelectedWatchlist(watchlist.id)}
                                        >
                                            <div>
                                                <p className={`text-sm font-semibold ${selectedWatchlist === watchlist.id ? 'text-amber-300' : 'text-zinc-200'}`}>{watchlist.name}</p>
                                                <p className="text-[10px] font-mono text-zinc-500">{new Date(watchlist.createdAt).toLocaleDateString()}</p>
                                            </div>
                                            <button
                                                onClick={(event) => {
                                                    event.stopPropagation();
                                                    handleDeleteWatchlist(watchlist.id);
                                                }}
                                                className="text-xs text-red-500 opacity-0 transition group-hover:opacity-100 hover:text-red-400"
                                            >
                                                X
                                            </button>
                                        </div>
                                    ))
                                )}
                            </div>

                            {showAddForm && selectedWatchlist && (
                                <form onSubmit={handleAddItem} className="mt-5 space-y-3 rounded-2xl border border-white/10 bg-zinc-950/60 p-4">
                                    <select
                                        value={addSymbol}
                                        onChange={(event) => setAddSymbol(event.target.value)}
                                        className="w-full rounded-xl border border-zinc-700 bg-black px-3 py-2 text-sm text-white outline-none focus:border-amber-400"
                                    >
                                        {availableSymbols.map((instrument) => (
                                            <option key={instrument.symbol} value={instrument.symbol}>
                                                {instrument.displayName} ({instrument.symbol})
                                            </option>
                                        ))}
                                    </select>
                                    <div className="grid grid-cols-2 gap-3">
                                        <input
                                            type="number"
                                            step="any"
                                            value={addAlertAbove}
                                            onChange={(event) => setAddAlertAbove(event.target.value)}
                                            placeholder="Alert above"
                                            className="rounded-xl border border-zinc-700 bg-black px-3 py-2 text-sm text-white outline-none focus:border-amber-400"
                                        />
                                        <input
                                            type="number"
                                            step="any"
                                            value={addAlertBelow}
                                            onChange={(event) => setAddAlertBelow(event.target.value)}
                                            placeholder="Alert below"
                                            className="rounded-xl border border-zinc-700 bg-black px-3 py-2 text-sm text-white outline-none focus:border-amber-400"
                                        />
                                    </div>
                                    <input
                                        type="text"
                                        value={addNotes}
                                        onChange={(event) => setAddNotes(event.target.value)}
                                        placeholder="Why are you tracking it?"
                                        className="w-full rounded-xl border border-zinc-700 bg-black px-3 py-2 text-sm text-white outline-none focus:border-amber-400"
                                    />
                                    <button type="submit" className="w-full rounded-xl bg-amber-500 px-4 py-2 text-sm font-bold text-black transition hover:bg-amber-400">
                                        Add to basket
                                    </button>
                                </form>
                            )}

                            <div className="mt-6 space-y-2">
                                {selectedWatchlist ? (
                                    enrichedItems.length > 0 ? (
                                        enrichedItems.map((item) => (
                                            <button
                                                key={item.id}
                                                onClick={() => setSelectedSymbol(item.symbol)}
                                                className={`w-full rounded-2xl border p-4 text-left transition ${selectedSymbol === item.symbol
                                                    ? 'border-amber-400/35 bg-amber-400/10'
                                                    : 'border-white/10 bg-white/[0.02] hover:border-white/20'}`}
                                            >
                                                <div className="flex items-start justify-between gap-3">
                                                    <div>
                                                        <p className="text-sm font-bold text-white">{item.symbol}</p>
                                                        <p className="mt-1 text-xs text-zinc-500">{item.notes || 'No note yet'}</p>
                                                    </div>
                                                    <button
                                                        onClick={(event) => {
                                                            event.stopPropagation();
                                                            handleRemoveItem(item.id);
                                                        }}
                                                        className="text-xs text-red-500/70 hover:text-red-400"
                                                    >
                                                        Remove
                                                    </button>
                                                </div>
                                                <div className="mt-4 flex items-end justify-between gap-3">
                                                    <div>
                                                        <p className="font-mono text-lg font-bold text-white">{formatMoney(Number(item.currentPrice))}</p>
                                                        <p className={`text-xs font-semibold ${Number(item.changePercent24h) >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>
                                                            {formatPercent(Number(item.changePercent24h))} today
                                                        </p>
                                                    </div>
                                                    <div className="text-right text-[10px] uppercase tracking-[0.2em] text-zinc-500">
                                                        {item.alertPriceAbove ? <p>UP {item.alertPriceAbove}</p> : <p>UP -</p>}
                                                        {item.alertPriceBelow ? <p className="mt-1">DOWN {item.alertPriceBelow}</p> : <p className="mt-1">DOWN -</p>}
                                                    </div>
                                                </div>
                                            </button>
                                        ))
                                    ) : (
                                        <div className="rounded-2xl border border-dashed border-white/10 p-8 text-center">
                                            <p className="text-zinc-500">This basket is empty.</p>
                                            <p className="mt-1 text-xs text-zinc-600">Add one of the supported instruments and it will appear here.</p>
                                        </div>
                                    )
                                ) : (
                                    <div className="rounded-2xl border border-dashed border-white/10 p-8 text-center">
                                        <p className="text-zinc-500">Select or create a watchlist.</p>
                                    </div>
                                )}
                            </div>

                            {loadingMoreHistory && (
                                <div className="mt-4 rounded-2xl border border-white/10 bg-white/[0.03] px-4 py-3 text-xs text-zinc-400">
                                    Loading older candles...
                                </div>
                            )}
                        </aside>
                    </div>
                )}
            </div>
        </div>
    );
}
