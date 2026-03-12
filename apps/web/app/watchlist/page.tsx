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

type ChartRange = '1D' | '1W' | '1M';

export default function WatchlistPage() {
    const [watchlists, setWatchlists] = useState<Watchlist[]>([]);
    const [selectedWatchlist, setSelectedWatchlist] = useState<string | null>(null);
    const [enrichedItems, setEnrichedItems] = useState<WatchlistItem[]>([]);
    const [instrumentUniverse, setInstrumentUniverse] = useState<InstrumentOption[]>([]);
    const [selectedSymbol, setSelectedSymbol] = useState<string>('BTCUSDT');
    const [selectedRange, setSelectedRange] = useState<ChartRange>('1D');
    const [candles, setCandles] = useState<CandlePoint[]>([]);
    const [loading, setLoading] = useState(true);
    const [chartLoading, setChartLoading] = useState(false);

    const [newName, setNewName] = useState('');
    const [addSymbol, setAddSymbol] = useState('BTCUSDT');
    const [addAlertAbove, setAddAlertAbove] = useState('');
    const [addAlertBelow, setAddAlertBelow] = useState('');
    const [addNotes, setAddNotes] = useState('');
    const [showAddForm, setShowAddForm] = useState(false);

    const currentUserId = typeof window !== 'undefined' ? localStorage.getItem('userId') : null;

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
            const res = await apiFetch('/api/v1/market/instruments');
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
            const res = await apiFetch(`/api/v1/watchlists/${selectedWatchlist}/items`);
            if (!res.ok) {
                return;
            }
            const data = await res.json();
            setEnrichedItems(data);
            if (Array.isArray(data) && data.length > 0) {
                setSelectedSymbol((current) => current || data[0].symbol);
            }
        } catch (error) {
            console.error(error);
        }
    }, [currentUserId, selectedWatchlist]);

    const fetchCandles = useCallback(async (symbol: string, range: ChartRange) => {
        if (!symbol) {
            return;
        }
        setChartLoading(true);
        try {
            const res = await apiFetch(`/api/v1/market/candles?symbol=${symbol}&range=${range}`);
            if (!res.ok) {
                return;
            }
            setCandles(await res.json());
        } catch (error) {
            console.error(error);
        } finally {
            setChartLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchWatchlists();
        fetchInstrumentUniverse();
    }, [fetchInstrumentUniverse, fetchWatchlists]);

    useEffect(() => {
        if (!selectedWatchlist) {
            return;
        }
        fetchItems();
        const interval = setInterval(fetchItems, 5000);
        return () => clearInterval(interval);
    }, [fetchItems, selectedWatchlist]);

    useEffect(() => {
        fetchCandles(selectedSymbol, selectedRange);
    }, [fetchCandles, selectedRange, selectedSymbol]);

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

    const selectedInstrument = useMemo(() => {
        return instrumentUniverse.find((item) => item.symbol === selectedSymbol)
            ?? enrichedItems.find((item) => item.symbol === selectedSymbol)
            ?? null;
    }, [enrichedItems, instrumentUniverse, selectedSymbol]);

    const availableSymbols = useMemo(() => {
        const existingSymbols = new Set(enrichedItems.map((item) => item.symbol));
        return instrumentUniverse.filter((item) => !existingSymbols.has(item.symbol) || item.symbol === addSymbol);
    }, [addSymbol, enrichedItems, instrumentUniverse]);

    return (
        <div className="min-h-screen bg-[radial-gradient(circle_at_top_left,_rgba(251,191,36,0.12),_transparent_30%),radial-gradient(circle_at_bottom_right,_rgba(34,197,94,0.12),_transparent_28%),#050505] text-white">
            <nav className="sticky top-0 z-50 flex items-center justify-between border-b border-white/10 bg-black/50 px-6 py-4 backdrop-blur-md">
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

            <div className="mx-auto max-w-[1500px] px-6 py-10">
                <div className="mb-8 flex items-center justify-between gap-6">
                    <div>
                        <h1 className="mb-2 text-3xl font-bold">
                            <span className="bg-gradient-to-r from-amber-400 via-yellow-300 to-orange-500 bg-clip-text text-transparent">Markets</span> Workspace
                        </h1>
                        <p className="text-sm text-zinc-400">
                            Build a right-side watch basket, click any supported instrument, and inspect a candlestick panel with `1D`, `1W`, and `1M` ranges.
                        </p>
                    </div>
                    <form onSubmit={handleCreateWatchlist} className="flex gap-2">
                        <input
                            type="text"
                            value={newName}
                            onChange={(event) => setNewName(event.target.value)}
                            placeholder="New watchlist name..."
                            className="w-48 rounded-lg border border-zinc-700 bg-zinc-900 px-3 py-2 text-sm text-white outline-none transition-colors focus:border-amber-500"
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
                    <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
                        <section className="space-y-5">
                            <div className="rounded-3xl border border-white/10 bg-black/40 p-5 shadow-[0_0_60px_rgba(0,0,0,0.4)] backdrop-blur-xl">
                                <div className="flex flex-wrap items-start justify-between gap-4">
                                    <div>
                                        <p className="text-[11px] uppercase tracking-[0.3em] text-zinc-500">Instrument</p>
                                        <h2 className="mt-2 text-3xl font-bold text-white">
                                            {selectedInstrument ? ('displayName' in selectedInstrument ? selectedInstrument.displayName : selectedInstrument.symbol) : selectedSymbol}
                                        </h2>
                                        <p className="mt-1 text-sm text-zinc-500">
                                            {selectedSymbol} · {selectedInstrument && 'assetType' in selectedInstrument ? selectedInstrument.assetType : 'WATCHLIST'}
                                        </p>
                                    </div>
                                    <div className="text-right">
                                        <p className="text-[11px] uppercase tracking-[0.3em] text-zinc-500">Spot</p>
                                        <p className="mt-2 font-mono text-3xl font-bold text-white">
                                            ${Number(selectedInstrument?.currentPrice ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                        </p>
                                        <p className={`mt-1 text-sm font-semibold ${Number(selectedInstrument?.changePercent24h ?? 0) >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>
                                            {Number(selectedInstrument?.changePercent24h ?? 0) >= 0 ? '+' : ''}{Number(selectedInstrument?.changePercent24h ?? 0).toFixed(2)}% 24h
                                        </p>
                                    </div>
                                </div>

                                <div className="mt-5 flex flex-wrap gap-2">
                                    {(['1D', '1W', '1M'] as ChartRange[]).map((range) => (
                                        <button
                                            key={range}
                                            onClick={() => setSelectedRange(range)}
                                            className={`rounded-full border px-4 py-2 text-xs font-bold uppercase tracking-[0.2em] transition ${selectedRange === range
                                                ? 'border-amber-400/40 bg-amber-400/10 text-amber-300'
                                                : 'border-white/10 bg-white/[0.03] text-zinc-400 hover:text-white'
                                                }`}
                                        >
                                            {range}
                                        </button>
                                    ))}
                                </div>

                                <div className="mt-6 rounded-3xl border border-white/8 bg-zinc-950/60 p-4">
                                    {chartLoading ? (
                                        <div className="flex h-[420px] items-center justify-center">
                                            <div className="h-8 w-8 animate-spin rounded-full border-2 border-amber-400 border-t-transparent" />
                                        </div>
                                    ) : (
                                        <MarketWorkspaceChart data={candles} />
                                    )}
                                </div>
                            </div>

                            <div className="grid gap-4 md:grid-cols-3">
                                <article className="rounded-2xl border border-white/10 bg-black/35 p-4 backdrop-blur-xl">
                                    <p className="text-[11px] uppercase tracking-[0.25em] text-zinc-500">Selected Range</p>
                                    <p className="mt-2 text-xl font-semibold text-white">{selectedRange}</p>
                                    <p className="mt-1 text-xs text-zinc-500">Quickly switch between intraday, weekly, and monthly candlestick windows.</p>
                                </article>
                                <article className="rounded-2xl border border-white/10 bg-black/35 p-4 backdrop-blur-xl">
                                    <p className="text-[11px] uppercase tracking-[0.25em] text-zinc-500">Watchlists</p>
                                    <p className="mt-2 text-xl font-semibold text-white">{watchlists.length}</p>
                                    <p className="mt-1 text-xs text-zinc-500">Use separate baskets for macro, majors, or experimental setups.</p>
                                </article>
                                <article className="rounded-2xl border border-white/10 bg-black/35 p-4 backdrop-blur-xl">
                                    <p className="text-[11px] uppercase tracking-[0.25em] text-zinc-500">Tracked Symbols</p>
                                    <p className="mt-2 text-xl font-semibold text-white">{enrichedItems.length}</p>
                                    <p className="mt-1 text-xs text-zinc-500">Each watchlist item can drive the main chart with a single click.</p>
                                </article>
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
                                                : 'border-white/10 hover:border-white/20 hover:bg-white/[0.03]'
                                                }`}
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
                                                ✕
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
                                                    : 'border-white/10 bg-white/[0.02] hover:border-white/20'
                                                    }`}
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
                                                        <p className="font-mono text-lg font-bold text-white">
                                                            ${Number(item.currentPrice).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                                        </p>
                                                        <p className={`text-xs font-semibold ${Number(item.changePercent24h) >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>
                                                            {Number(item.changePercent24h) >= 0 ? '+' : ''}{Number(item.changePercent24h).toFixed(2)}% today
                                                        </p>
                                                    </div>
                                                    <div className="text-right text-[10px] uppercase tracking-[0.2em] text-zinc-500">
                                                        {item.alertPriceAbove ? <p>↑ {item.alertPriceAbove}</p> : <p>↑ —</p>}
                                                        {item.alertPriceBelow ? <p className="mt-1">↓ {item.alertPriceBelow}</p> : <p className="mt-1">↓ —</p>}
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
                        </aside>
                    </div>
                )}
            </div>
        </div>
    );
}
