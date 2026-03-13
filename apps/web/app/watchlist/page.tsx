'use client';


import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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

type ChartRange = '1D' | '1W' | '1M' | '3M' | '6M' | '1Y' | 'ALL';
type ChartInterval = '1m' | '15m' | '30m' | '1h' | '4h' | '1d';
type DrawingMode = 'none' | 'horizontal' | 'trend';

const RANGE_OPTIONS: ChartRange[] = ['1D', '1W', '1M', '3M', '6M', '1Y', 'ALL'];
const INTERVAL_OPTIONS: ChartInterval[] = ['1m', '15m', '30m', '1h', '4h', '1d'];
const ALL_HISTORY_CHUNK = 1000;
const MARKET_SESSION_STORAGE_KEY = 'market.terminal.session';

interface PersistedMarketSession {
    selectedWatchlist: string | null;
    selectedSymbol: string;
    compareSymbol: string;
    compareVisible: boolean;
    selectedRange: ChartRange;
    selectedInterval: ChartInterval;
}

function readPersistedMarketSession(): PersistedMarketSession | null {
    if (typeof window === 'undefined') {
        return null;
    }
    try {
        const stored = window.localStorage.getItem(MARKET_SESSION_STORAGE_KEY);
        if (!stored) {
            return null;
        }
        const parsed = JSON.parse(stored);
        if (!parsed || typeof parsed !== 'object') {
            return null;
        }
        const selectedRange = RANGE_OPTIONS.includes(parsed.selectedRange) ? parsed.selectedRange : '1D';
        const selectedInterval = INTERVAL_OPTIONS.includes(parsed.selectedInterval) ? parsed.selectedInterval : '1h';
        return {
            selectedWatchlist: typeof parsed.selectedWatchlist === 'string' ? parsed.selectedWatchlist : null,
            selectedSymbol: typeof parsed.selectedSymbol === 'string' && parsed.selectedSymbol ? parsed.selectedSymbol : 'BTCUSDT',
            compareSymbol: typeof parsed.compareSymbol === 'string' ? parsed.compareSymbol : '',
            compareVisible: parsed.compareVisible !== false,
            selectedRange,
            selectedInterval,
        };
    } catch (error) {
        console.error(error);
        return null;
    }
}

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
    const persistedSession = readPersistedMarketSession();
    const [watchlists, setWatchlists] = useState<Watchlist[]>([]);
    const [selectedWatchlist, setSelectedWatchlist] = useState<string | null>(persistedSession?.selectedWatchlist ?? null);
    const [enrichedItems, setEnrichedItems] = useState<WatchlistItem[]>([]);
    const [instrumentUniverse, setInstrumentUniverse] = useState<InstrumentOption[]>([]);
    const [instrumentQuery, setInstrumentQuery] = useState('');
    const [selectedSymbol, setSelectedSymbol] = useState<string>(persistedSession?.selectedSymbol ?? 'BTCUSDT');
    const [compareSymbol, setCompareSymbol] = useState<string>(persistedSession?.compareSymbol ?? '');
    const [compareVisible, setCompareVisible] = useState<boolean>(persistedSession?.compareVisible ?? true);
    const [selectedRange, setSelectedRange] = useState<ChartRange>(persistedSession?.selectedRange ?? '1D');
    const [selectedInterval, setSelectedInterval] = useState<ChartInterval>(persistedSession?.selectedInterval ?? '1h');
    const [drawingMode, setDrawingMode] = useState<DrawingMode>('none');
    const [clearDrawingsToken, setClearDrawingsToken] = useState(0);
    const [favoriteSymbols, setFavoriteSymbols] = useState<string[]>([]);
    const [candles, setCandles] = useState<CandlePoint[]>([]);
    const [compareCandles, setCompareCandles] = useState<CandlePoint[]>([]);
    const [loading, setLoading] = useState(true);
    const [chartLoading, setChartLoading] = useState(false);
    const [loadingMoreHistory, setLoadingMoreHistory] = useState(false);
    const [hasMoreHistory, setHasMoreHistory] = useState(true);
    const candlesRef = useRef<CandlePoint[]>([]);

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

    const selectedCompareInstrument = useMemo(() => {
        if (!compareSymbol) {
            return null;
        }
        return instrumentUniverse.find((item) => item.symbol === compareSymbol)
            ?? enrichedItems.find((item) => item.symbol === compareSymbol)
            ?? null;
    }, [compareSymbol, enrichedItems, instrumentUniverse]);

    const loadedHistoryLabel = useMemo(() => {
        if (candles.length === 0) {
            return 'No candles loaded yet.';
        }
        const oldest = new Date(candles[0].openTime).toLocaleDateString();
        const newest = new Date(candles[candles.length - 1].openTime).toLocaleDateString();
        return `${oldest} → ${newest}`;
    }, [candles]);

    const compareSessionSummary = useMemo(() => {
        if (!compareSymbol || candles.length === 0 || compareCandles.length === 0) {
            return null;
        }
        const primaryBase = candles[0].close || 1;
        const primaryLatest = candles[candles.length - 1].close;
        const compareBase = compareCandles[0].close || 1;
        const compareLatest = compareCandles[compareCandles.length - 1].close;
        const primaryMovePercent = ((primaryLatest / primaryBase) - 1) * 100;
        const compareMovePercent = ((compareLatest / compareBase) - 1) * 100;
        const relativeGapPercent = primaryMovePercent - compareMovePercent;

        return {
            primaryMovePercent,
            compareMovePercent,
            relativeGapPercent,
        };
    }, [candles, compareCandles, compareSymbol]);

    useEffect(() => {
        candlesRef.current = candles;
    }, [candles]);

    useEffect(() => {
        if (compareSymbol === selectedSymbol) {
            setCompareSymbol('');
        }
    }, [compareSymbol, selectedSymbol]);

    useEffect(() => {
        setDrawingMode('none');
    }, [selectedInterval, selectedRange, selectedSymbol]);

    useEffect(() => {
        if (!compareSymbol) {
            setCompareVisible(true);
        }
    }, [compareSymbol]);

    useEffect(() => {
        if (typeof window === 'undefined') {
            return;
        }
        const session: PersistedMarketSession = {
            selectedWatchlist,
            selectedSymbol,
            compareSymbol,
            compareVisible,
            selectedRange,
            selectedInterval,
        };
        window.localStorage.setItem(MARKET_SESSION_STORAGE_KEY, JSON.stringify(session));
    }, [compareSymbol, compareVisible, selectedInterval, selectedRange, selectedSymbol, selectedWatchlist]);

    useEffect(() => {
        if (typeof window === 'undefined') {
            return;
        }
        try {
            const stored = window.localStorage.getItem('market.favoriteSymbols');
            if (!stored) {
                return;
            }
            const parsed = JSON.parse(stored);
            if (Array.isArray(parsed)) {
                setFavoriteSymbols(parsed.filter((value) => typeof value === 'string'));
            }
        } catch (error) {
            console.error(error);
        }
    }, []);

    useEffect(() => {
        if (typeof window === 'undefined') {
            return;
        }
        window.localStorage.setItem('market.favoriteSymbols', JSON.stringify(favoriteSymbols));
    }, [favoriteSymbols]);

    const filteredInstruments = useMemo(() => {
        const query = instrumentQuery.trim().toLowerCase();
        if (!query) {
            return instrumentUniverse;
        }
        return instrumentUniverse.filter((instrument) =>
            instrument.symbol.toLowerCase().includes(query)
            || instrument.displayName.toLowerCase().includes(query));
    }, [instrumentQuery, instrumentUniverse]);

    const favoriteInstruments = useMemo(() => {
        const favoriteSet = new Set(favoriteSymbols);
        return instrumentUniverse.filter((instrument) => favoriteSet.has(instrument.symbol));
    }, [favoriteSymbols, instrumentUniverse]);

    const availableSymbols = useMemo(() => {
        const existingSymbols = new Set(enrichedItems.map((item) => item.symbol));
        return instrumentUniverse.filter((item) => !existingSymbols.has(item.symbol) || item.symbol === addSymbol);
    }, [addSymbol, enrichedItems, instrumentUniverse]);

    const toggleFavoriteSymbol = useCallback((symbol: string) => {
        setFavoriteSymbols((current) => {
            if (current.includes(symbol)) {
                return current.filter((item) => item !== symbol);
            }
            return [...current, symbol];
        });
    }, []);

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
            if (data.length === 0) {
                setSelectedWatchlist(null);
                return;
            }
            const selectedStillExists = selectedWatchlist
                ? data.some((watchlist: Watchlist) => watchlist.id === selectedWatchlist)
                : false;
            if (!selectedStillExists) {
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
                setAddSymbol((current) => data.some((item: InstrumentOption) => item.symbol === current) ? current : data[0].symbol);
                setSelectedSymbol((current) => data.some((item: InstrumentOption) => item.symbol === current) ? current : data[0].symbol);
                setCompareSymbol((current) => {
                    if (!current) {
                        return '';
                    }
                    return data.some((item: InstrumentOption) => item.symbol === current) ? current : '';
                });
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

        const beforeOpenTime = mode === 'prepend' && candlesRef.current.length > 0 ? candlesRef.current[0].openTime : null;

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
    }, [fetchCandleChunk]);

    const fetchCompareCandles = useCallback(async (
        symbol: string,
        range: ChartRange,
        interval: ChartInterval,
    ) => {
        if (!symbol || symbol === selectedSymbol) {
            setCompareCandles([]);
            return;
        }

        try {
            const nextCandles = await fetchCandleChunk(symbol, range, interval);
            setCompareCandles(nextCandles);
        } catch (error) {
            console.error(error);
            setCompareCandles([]);
        }
    }, [fetchCandleChunk, selectedSymbol]);

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

    useEffect(() => {
        fetchCompareCandles(compareSymbol, selectedRange, selectedInterval);
    }, [compareSymbol, fetchCompareCandles, selectedInterval, selectedRange]);

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
                                            <div className="mt-2 flex items-center gap-3">
                                                <h2 className="text-3xl font-bold text-white">{selectedDisplayName}</h2>
                                                <button
                                                    onClick={() => toggleFavoriteSymbol(selectedSymbol)}
                                                    className={`rounded-full border px-3 py-1 text-[10px] font-bold uppercase tracking-[0.18em] transition ${favoriteSymbols.includes(selectedSymbol)
                                                        ? 'border-amber-400/40 bg-amber-400/10 text-amber-300'
                                                        : 'border-white/10 bg-white/[0.03] text-zinc-400 hover:text-white'}`}
                                                >
                                                    {favoriteSymbols.includes(selectedSymbol) ? 'Starred' : 'Star'}
                                                </button>
                                            </div>
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

                                    <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_auto_auto_auto]">
                                        <input
                                            type="text"
                                            value={instrumentQuery}
                                            onChange={(event) => setInstrumentQuery(event.target.value)}
                                            placeholder="Search BTC, ETH, Solana..."
                                            className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3 text-sm text-white outline-none transition-colors focus:border-amber-400"
                                        />
                                        <label className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3">
                                            <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Range</p>
                                            <select
                                                value={selectedRange}
                                                onChange={(event) => setSelectedRange(event.target.value as ChartRange)}
                                                className="mt-2 w-full bg-transparent text-sm font-semibold text-amber-300 outline-none"
                                            >
                                                {RANGE_OPTIONS.map((range) => (
                                                    <option key={range} value={range} className="bg-zinc-950 text-white">
                                                        {range}
                                                    </option>
                                                ))}
                                            </select>
                                        </label>
                                        <label className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3">
                                            <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Interval</p>
                                            <select
                                                value={selectedInterval}
                                                onChange={(event) => setSelectedInterval(event.target.value as ChartInterval)}
                                                className="mt-2 w-full bg-transparent text-sm font-semibold text-green-300 outline-none"
                                            >
                                                {INTERVAL_OPTIONS.map((interval) => {
                                                    const disabled = selectedRange === 'ALL' && interval === '1m';
                                                    return (
                                                        <option
                                                            key={interval}
                                                            value={interval}
                                                            disabled={disabled}
                                                            className="bg-zinc-950 text-white"
                                                        >
                                                            {interval}
                                                        </option>
                                                    );
                                                })}
                                            </select>
                                        </label>
                                        <label className="rounded-2xl border border-white/10 bg-zinc-950/70 px-4 py-3">
                                            <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Compare</p>
                                            <select
                                                value={compareSymbol}
                                                onChange={(event) => setCompareSymbol(event.target.value)}
                                                className="mt-2 w-full bg-transparent text-sm font-semibold text-sky-300 outline-none"
                                            >
                                                <option value="" className="bg-zinc-950 text-white">Off</option>
                                                {instrumentUniverse
                                                    .filter((instrument) => instrument.symbol !== selectedSymbol)
                                                    .map((instrument) => (
                                                        <option key={instrument.symbol} value={instrument.symbol} className="bg-zinc-950 text-white">
                                                            {instrument.symbol}
                                                        </option>
                                                    ))}
                                            </select>
                                        </label>
                                    </div>

                                    <div className="flex flex-wrap items-center gap-2 rounded-2xl border border-white/10 bg-white/[0.03] px-4 py-3">
                                        <p className="mr-2 text-[10px] uppercase tracking-[0.24em] text-zinc-500">Draw</p>
                                        <button
                                            onClick={() => setDrawingMode((current) => current === 'horizontal' ? 'none' : 'horizontal')}
                                            className={`rounded-full border px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] transition ${drawingMode === 'horizontal'
                                                ? 'border-sky-400/30 bg-sky-400/10 text-sky-300'
                                                : 'border-white/10 bg-white/[0.03] text-zinc-300 hover:text-white'}`}
                                        >
                                            Horizontal
                                        </button>
                                        <button
                                            onClick={() => setDrawingMode((current) => current === 'trend' ? 'none' : 'trend')}
                                            className={`rounded-full border px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] transition ${drawingMode === 'trend'
                                                ? 'border-pink-400/30 bg-pink-400/10 text-pink-300'
                                                : 'border-white/10 bg-white/[0.03] text-zinc-300 hover:text-white'}`}
                                        >
                                            Trend Line
                                        </button>
                                        <button
                                            onClick={() => {
                                                setDrawingMode('none');
                                                setClearDrawingsToken((current) => current + 1);
                                            }}
                                            className="rounded-full border border-white/10 bg-white/[0.03] px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] text-zinc-300 transition hover:text-white"
                                        >
                                            Clear Drawings
                                        </button>
                                        <span className="ml-auto text-xs text-zinc-500">
                                            {drawingMode === 'none'
                                                ? 'Select a tool to place levels or trend lines.'
                                                : (drawingMode === 'horizontal'
                                                    ? 'Click once on the chart to place a level.'
                                                    : 'Click two points on the chart to place a trend line.')}
                                        </span>
                                    </div>

                                    {compareSymbol && (
                                        <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-amber-400/15 bg-amber-400/5 px-4 py-3">
                                            <div>
                                                <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Compare Session</p>
                                                <div className="mt-2 flex flex-wrap items-center gap-2">
                                                    <span className="rounded-full border border-amber-400/30 bg-amber-400/10 px-3 py-1 text-xs font-bold uppercase tracking-[0.16em] text-amber-300">
                                                        {selectedSymbol}
                                                    </span>
                                                    <span className="text-zinc-500">vs</span>
                                                    <span className="rounded-full border border-sky-400/30 bg-sky-400/10 px-3 py-1 text-xs font-bold uppercase tracking-[0.16em] text-sky-300">
                                                        {selectedCompareInstrument?.symbol ?? compareSymbol}
                                                    </span>
                                                    {selectedCompareInstrument && 'displayName' in selectedCompareInstrument && (
                                                        <span className="text-sm text-zinc-400">
                                                            {selectedCompareInstrument.displayName}
                                                        </span>
                                                    )}
                                                    {compareSessionSummary && (
                                                        <span className={`rounded-full border px-3 py-1 text-[11px] font-bold uppercase tracking-[0.16em] ${compareSessionSummary.relativeGapPercent >= 0
                                                            ? 'border-emerald-400/30 bg-emerald-400/10 text-emerald-300'
                                                            : 'border-red-400/30 bg-red-400/10 text-red-300'}`}>
                                                            {compareSessionSummary.relativeGapPercent >= 0 ? 'Outperforming' : 'Underperforming'} {formatPercent(compareSessionSummary.relativeGapPercent)}
                                                        </span>
                                                    )}
                                                </div>
                                                {compareSessionSummary && (
                                                    <p className="mt-2 text-sm text-zinc-400">
                                                        {selectedSymbol} {formatPercent(compareSessionSummary.primaryMovePercent)} · {selectedCompareInstrument?.symbol ?? compareSymbol} {formatPercent(compareSessionSummary.compareMovePercent)}
                                                    </p>
                                                )}
                                            </div>
                                            <div className="flex flex-wrap items-center gap-2">
                                                <button
                                                    onClick={() => setCompareVisible((current) => !current)}
                                                    className={`rounded-full border px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] transition ${compareVisible
                                                        ? 'border-sky-400/30 bg-sky-400/10 text-sky-300 hover:bg-sky-400/20'
                                                        : 'border-white/10 bg-white/[0.03] text-zinc-300 hover:text-white'}`}
                                                >
                                                    {compareVisible ? 'Hide Overlay' : 'Show Overlay'}
                                                </button>
                                                <button
                                                    onClick={() => {
                                                        setCompareSymbol('');
                                                        setCompareVisible(true);
                                                    }}
                                                    className="rounded-full border border-white/10 bg-white/[0.03] px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] text-zinc-300 transition hover:text-white"
                                                >
                                                    Clear Compare
                                                </button>
                                            </div>
                                        </div>
                                    )}

                                    {favoriteInstruments.length > 0 && (
                                        <div className="rounded-2xl border border-white/10 bg-white/[0.03] px-4 py-3">
                                            <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Favorites</p>
                                            <div className="mt-3 flex flex-wrap gap-2">
                                                {favoriteInstruments.map((instrument) => (
                                                    <button
                                                        key={instrument.symbol}
                                                        onClick={() => setSelectedSymbol(instrument.symbol)}
                                                        className={`rounded-full border px-4 py-2 text-xs font-bold uppercase tracking-[0.18em] transition ${selectedSymbol === instrument.symbol
                                                            ? 'border-amber-400/40 bg-amber-400/10 text-amber-300'
                                                            : 'border-white/10 bg-white/[0.03] text-zinc-300 hover:text-white'}`}
                                                    >
                                                        {instrument.symbol}
                                                    </button>
                                                ))}
                                            </div>
                                        </div>
                                    )}

                                    <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_320px]">
                                        <div className="rounded-3xl border border-white/8 bg-zinc-950/60 p-4">
                                            {(chartLoading && candles.length === 0) ? (
                                                <div className="flex h-[520px] items-center justify-center">
                                                    <div className="h-8 w-8 animate-spin rounded-full border-2 border-amber-400 border-t-transparent" />
                                                </div>
                                            ) : (
                                                <MarketWorkspaceChart
                                                    data={candles}
                                                    compareData={compareCandles}
                                                    compareLabel={compareSymbol || null}
                                                    compareVisible={compareVisible}
                                                    drawingMode={drawingMode}
                                                    drawingStorageKey={selectedSymbol}
                                                    clearDrawingsToken={clearDrawingsToken}
                                                    onDrawingComplete={() => setDrawingMode('none')}
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
                                                    <div
                                                        key={instrument.symbol}
                                                        className={`w-full rounded-2xl border p-3 text-left transition ${selectedSymbol === instrument.symbol
                                                            ? 'border-amber-400/35 bg-amber-400/10'
                                                            : 'border-white/10 bg-white/[0.02] hover:border-white/20'}`}
                                                    >
                                                        <div className="flex items-center justify-between gap-3">
                                                            <div>
                                                                <div className="flex items-center gap-2">
                                                                    <button onClick={() => setSelectedSymbol(instrument.symbol)} className="text-left">
                                                                        <p className="text-sm font-semibold text-white">{instrument.displayName}</p>
                                                                    </button>
                                                                    <button
                                                                        onClick={() => toggleFavoriteSymbol(instrument.symbol)}
                                                                        className={`rounded-full border px-2 py-0.5 text-[10px] font-bold uppercase tracking-[0.14em] transition ${favoriteSymbols.includes(instrument.symbol)
                                                                            ? 'border-amber-400/40 bg-amber-400/10 text-amber-300'
                                                                            : 'border-white/10 bg-white/[0.03] text-zinc-500 hover:text-white'}`}
                                                                    >
                                                                        ★
                                                                    </button>
                                                                </div>
                                                                <p className="text-[11px] font-mono text-zinc-500">{instrument.symbol}</p>
                                                            </div>
                                                            <div className="text-right">
                                                                <p className="font-mono text-sm text-white">{formatMoney(instrument.currentPrice)}</p>
                                                                <p className={`text-[11px] font-semibold ${instrument.changePercent24h >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>
                                                                    {formatPercent(instrument.changePercent24h)}
                                                                </p>
                                                            </div>
                                                        </div>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    </div>

                                    <div className="grid gap-4 md:grid-cols-6">
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
                                            <p className="text-[11px] uppercase tracking-[0.25em] text-zinc-500">Compare</p>
                                            <p className="mt-2 text-xl font-semibold text-white">{compareSymbol || 'Off'}</p>
                                            <p className="mt-1 text-xs text-zinc-500">
                                                {compareSymbol
                                                    ? (compareVisible ? 'Normalized overlay line is visible.' : 'Overlay selected but hidden.')
                                                    : 'Normalized overlay line.'}
                                            </p>
                                            {compareSessionSummary && (
                                                <p className={`mt-2 text-xs font-semibold ${compareSessionSummary.relativeGapPercent >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>
                                                    {compareSessionSummary.relativeGapPercent >= 0 ? 'Primary leads' : 'Primary trails'} by {formatPercent(Math.abs(compareSessionSummary.relativeGapPercent))}
                                                </p>
                                            )}
                                        </article>
                                        <article className="rounded-2xl border border-white/10 bg-black/35 p-4 backdrop-blur-xl">
                                            <p className="text-[11px] uppercase tracking-[0.25em] text-zinc-500">Draw Mode</p>
                                            <p className="mt-2 text-xl font-semibold text-white">{drawingMode === 'none' ? 'Off' : drawingMode}</p>
                                            <p className="mt-1 text-xs text-zinc-500">Horizontal levels and trend lines.</p>
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
