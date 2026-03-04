'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { apiFetch } from '../../lib/api-client';

interface WatchlistItem {
    id: string;
    symbol: string;
    currentPrice: number;
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

export default function WatchlistPage() {
    const [watchlists, setWatchlists] = useState<Watchlist[]>([]);
    const [selectedWatchlist, setSelectedWatchlist] = useState<string | null>(null);
    const [enrichedItems, setEnrichedItems] = useState<WatchlistItem[]>([]);
    const [loading, setLoading] = useState(true);

    // New watchlist form
    const [newName, setNewName] = useState('');

    // Add item form
    const [addSymbol, setAddSymbol] = useState('');
    const [addAlertAbove, setAddAlertAbove] = useState('');
    const [addAlertBelow, setAddAlertBelow] = useState('');
    const [addNotes, setAddNotes] = useState('');
    const [showAddForm, setShowAddForm] = useState(false);
    const currentUserId = typeof window !== 'undefined' ? localStorage.getItem('userId') : null;

    useEffect(() => {
        fetchWatchlists();
    }, []);

    useEffect(() => {
        if (selectedWatchlist) {
            fetchItems();
            const interval = setInterval(fetchItems, 5000); // Refresh prices every 5s
            return () => clearInterval(interval);
        }
    }, [selectedWatchlist]);

    const fetchWatchlists = async () => {
        if (!currentUserId) return;
        try {
            const res = await apiFetch('/api/v1/watchlists');
            if (res.ok) {
                const data = await res.json();
                setWatchlists(data);
                if (data.length > 0 && !selectedWatchlist) {
                    setSelectedWatchlist(data[0].id);
                }
            }
        } catch (err) {
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const fetchItems = async () => {
        if (!selectedWatchlist || !currentUserId) return;
        try {
            const res = await apiFetch(`/api/v1/watchlists/${selectedWatchlist}/items`);
            if (res.ok) {
                setEnrichedItems(await res.json());
            }
        } catch (err) {
            console.error(err);
        }
    };

    const handleCreateWatchlist = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!currentUserId) return;
        try {
            const res = await apiFetch('/api/v1/watchlists', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: newName || 'My Watchlist' })
            });
            if (res.ok) {
                setNewName('');
                fetchWatchlists();
            }
        } catch (err) {
            console.error(err);
        }
    };

    const handleAddItem = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedWatchlist || !currentUserId) return;
        try {
            const res = await apiFetch(`/api/v1/watchlists/${selectedWatchlist}/items`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    symbol: addSymbol,
                    alertPriceAbove: addAlertAbove ? parseFloat(addAlertAbove) : null,
                    alertPriceBelow: addAlertBelow ? parseFloat(addAlertBelow) : null,
                    notes: addNotes || null
                })
            });
            if (res.ok) {
                setAddSymbol('');
                setAddAlertAbove('');
                setAddAlertBelow('');
                setAddNotes('');
                setShowAddForm(false);
                fetchItems();
            }
        } catch (err) {
            console.error(err);
        }
    };

    const handleRemoveItem = async (itemId: string) => {
        try {
            await apiFetch(`/api/v1/watchlists/items/${itemId}`, { method: 'DELETE' });
            fetchItems();
        } catch (err) {
            console.error(err);
        }
    };

    const handleDeleteWatchlist = async (id: string) => {
        if (!currentUserId) return;
        try {
            await apiFetch(`/api/v1/watchlists/${id}`, {
                method: 'DELETE'
            });
            if (selectedWatchlist === id) setSelectedWatchlist(null);
            fetchWatchlists();
        } catch (err) {
            console.error(err);
        }
    };

    return (
        <div className="min-h-screen bg-black text-white">
            {/* Nav */}
            <nav className="border-b border-white/10 px-6 py-4 flex items-center justify-between backdrop-blur-md bg-black/50 sticky top-0 z-50">
                <Link href="/dashboard" className="font-bold text-xl tracking-tight flex items-center gap-2">
                    <div className="w-8 h-8 bg-green-500 rounded-lg flex items-center justify-center text-black font-bold">P</div>
                    <span>PaperTrade<span className="text-green-500">Pro</span></span>
                </Link>
                <div className="flex gap-4 text-sm font-medium text-zinc-400 items-center">
                    <Link href="/dashboard" className="hover:text-white transition-colors">Dashboard</Link>
                    <Link href="/dashboard/leaderboard" className="hover:text-white transition-colors">Leaderboard</Link>
                    <Link href="/discover" className="hover:text-white transition-colors">Discover</Link>
                    <Link href="/watchlist" className="text-white">Watchlist</Link>
                </div>
            </nav>

            <div className="max-w-6xl mx-auto px-6 py-10">
                {/* Header */}
                <div className="mb-8 flex items-center justify-between">
                    <div>
                        <h1 className="text-3xl font-bold mb-2">
                            👁️ <span className="text-transparent bg-clip-text bg-gradient-to-r from-amber-400 to-orange-500">Watchlist</span> & Alerts
                        </h1>
                        <p className="text-zinc-400 text-sm">
                            Track your favorite pairs and get notified when prices hit your targets.
                        </p>
                    </div>
                    <form onSubmit={handleCreateWatchlist} className="flex gap-2">
                        <input
                            type="text"
                            value={newName}
                            onChange={(e) => setNewName(e.target.value)}
                            placeholder="New watchlist name..."
                            className="bg-zinc-900 border border-zinc-700 rounded-lg px-3 py-2 text-sm text-white focus:border-amber-500 outline-none transition-colors w-48"
                        />
                        <button type="submit" className="bg-amber-500/10 text-amber-400 px-4 py-2 rounded-lg border border-amber-500/20 text-sm font-bold hover:bg-amber-500/20 transition-all">
                            + Create
                        </button>
                    </form>
                </div>

                {loading ? (
                    <div className="flex justify-center py-20">
                        <div className="animate-spin w-8 h-8 border-2 border-amber-500 border-t-transparent rounded-full"></div>
                    </div>
                ) : (
                    <div className="grid grid-cols-12 gap-6">
                        {/* Sidebar: Watchlist selector */}
                        <div className="col-span-3">
                            <div className="space-y-2">
                                {watchlists.length === 0 ? (
                                    <p className="text-zinc-500 text-sm italic">No watchlists yet. Create one!</p>
                                ) : (
                                    watchlists.map(wl => (
                                        <div
                                            key={wl.id}
                                            className={`flex items-center justify-between p-3 rounded-xl border cursor-pointer transition-all group ${selectedWatchlist === wl.id
                                                ? 'bg-amber-500/10 border-amber-500/30 shadow-[0_0_15px_rgba(245,158,11,0.1)]'
                                                : 'border-white/10 hover:border-white/20 hover:bg-white/[0.02]'
                                                }`}
                                            onClick={() => setSelectedWatchlist(wl.id)}
                                        >
                                            <div>
                                                <p className={`font-semibold text-sm ${selectedWatchlist === wl.id ? 'text-amber-400' : 'text-zinc-300'}`}>{wl.name}</p>
                                                <p className="text-[10px] text-zinc-600 font-mono">{new Date(wl.createdAt).toLocaleDateString()}</p>
                                            </div>
                                            <button
                                                onClick={(e) => { e.stopPropagation(); handleDeleteWatchlist(wl.id); }}
                                                className="opacity-0 group-hover:opacity-100 text-red-500 hover:text-red-400 text-xs transition-all"
                                            >
                                                ✕
                                            </button>
                                        </div>
                                    ))
                                )}
                            </div>
                        </div>

                        {/* Main: Items */}
                        <div className="col-span-9">
                            {selectedWatchlist ? (
                                <>
                                    <div className="flex items-center justify-between mb-4">
                                        <h2 className="text-lg font-bold text-zinc-300 uppercase tracking-widest text-[10px]">Tracked Symbols</h2>
                                        <button
                                            onClick={() => setShowAddForm(!showAddForm)}
                                            className="bg-green-500/10 text-green-400 px-3 py-1.5 rounded-lg border border-green-500/20 text-xs font-bold hover:bg-green-500/20 transition-all"
                                        >
                                            + Add Symbol
                                        </button>
                                    </div>

                                    {showAddForm && (
                                        <form onSubmit={handleAddItem} className="bg-zinc-900/50 border border-zinc-800 rounded-xl p-4 mb-4 grid grid-cols-5 gap-3 animate-in fade-in slide-in-from-top-2 duration-200">
                                            <input
                                                type="text"
                                                value={addSymbol}
                                                onChange={(e) => setAddSymbol(e.target.value)}
                                                placeholder="Symbol (e.g. BTCUSDT)"
                                                className="bg-black border border-zinc-700 rounded-lg px-3 py-2 text-sm text-white focus:border-green-500 outline-none"
                                                required
                                            />
                                            <input
                                                type="number"
                                                step="any"
                                                value={addAlertAbove}
                                                onChange={(e) => setAddAlertAbove(e.target.value)}
                                                placeholder="Alert above $"
                                                className="bg-black border border-zinc-700 rounded-lg px-3 py-2 text-sm text-white focus:border-green-500 outline-none"
                                            />
                                            <input
                                                type="number"
                                                step="any"
                                                value={addAlertBelow}
                                                onChange={(e) => setAddAlertBelow(e.target.value)}
                                                placeholder="Alert below $"
                                                className="bg-black border border-zinc-700 rounded-lg px-3 py-2 text-sm text-white focus:border-green-500 outline-none"
                                            />
                                            <input
                                                type="text"
                                                value={addNotes}
                                                onChange={(e) => setAddNotes(e.target.value)}
                                                placeholder="Notes (optional)"
                                                className="bg-black border border-zinc-700 rounded-lg px-3 py-2 text-sm text-white focus:border-green-500 outline-none"
                                            />
                                            <button type="submit" className="bg-green-600 text-white font-bold rounded-lg text-sm hover:bg-green-500 transition-colors">
                                                Add
                                            </button>
                                        </form>
                                    )}

                                    {enrichedItems.length === 0 ? (
                                        <div className="text-center py-16 border border-dashed border-white/10 rounded-xl">
                                            <p className="text-zinc-500 text-lg mb-1">No symbols tracked</p>
                                            <p className="text-zinc-600 text-sm">Add a symbol to start tracking!</p>
                                        </div>
                                    ) : (
                                        <div className="bg-black/40 backdrop-blur-xl border border-white/10 rounded-2xl overflow-hidden shadow-2xl">
                                            <table className="w-full text-left">
                                                <thead className="bg-zinc-950/50 text-zinc-500 text-[10px] uppercase tracking-tighter">
                                                    <tr>
                                                        <th className="p-4 font-bold">Symbol</th>
                                                        <th className="p-4 font-bold text-right">Live Price</th>
                                                        <th className="p-4 font-bold text-center">Alert Above</th>
                                                        <th className="p-4 font-bold text-center">Alert Below</th>
                                                        <th className="p-4 font-bold">Notes</th>
                                                        <th className="p-4 font-bold text-right">Actions</th>
                                                    </tr>
                                                </thead>
                                                <tbody className="divide-y divide-zinc-800/50">
                                                    {enrichedItems.map(item => (
                                                        <tr key={item.id} className="hover:bg-white/5 transition-colors text-sm">
                                                            <td className="p-4 font-bold text-white">{item.symbol}</td>
                                                            <td className="p-4 text-right font-mono text-emerald-400 font-bold">
                                                                ${Number(item.currentPrice).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                                            </td>
                                                            <td className="p-4 text-center">
                                                                {item.alertPriceAbove ? (
                                                                    <span className={`px-2 py-0.5 rounded-full text-xs font-bold border ${item.alertAboveTriggered
                                                                        ? 'bg-green-500/10 text-green-400 border-green-500/20'
                                                                        : 'bg-amber-500/10 text-amber-400 border-amber-500/20'
                                                                        }`}>
                                                                        {item.alertAboveTriggered ? '✓ ' : '⏳ '}${Number(item.alertPriceAbove).toLocaleString()}
                                                                    </span>
                                                                ) : (
                                                                    <span className="text-zinc-600">—</span>
                                                                )}
                                                            </td>
                                                            <td className="p-4 text-center">
                                                                {item.alertPriceBelow ? (
                                                                    <span className={`px-2 py-0.5 rounded-full text-xs font-bold border ${item.alertBelowTriggered
                                                                        ? 'bg-red-500/10 text-red-400 border-red-500/20'
                                                                        : 'bg-amber-500/10 text-amber-400 border-amber-500/20'
                                                                        }`}>
                                                                        {item.alertBelowTriggered ? '✓ ' : '⏳ '}${Number(item.alertPriceBelow).toLocaleString()}
                                                                    </span>
                                                                ) : (
                                                                    <span className="text-zinc-600">—</span>
                                                                )}
                                                            </td>
                                                            <td className="p-4 text-zinc-400 text-xs max-w-[200px] truncate">{item.notes || '—'}</td>
                                                            <td className="p-4 text-right">
                                                                <button
                                                                    onClick={() => handleRemoveItem(item.id)}
                                                                    className="text-red-500/50 hover:text-red-400 text-xs font-bold transition-colors"
                                                                >
                                                                    Remove
                                                                </button>
                                                            </td>
                                                        </tr>
                                                    ))}
                                                </tbody>
                                            </table>
                                        </div>
                                    )}
                                </>
                            ) : (
                                <div className="text-center py-20 border border-dashed border-white/10 rounded-xl">
                                    <p className="text-zinc-500 text-lg mb-1">Select or create a watchlist</p>
                                    <p className="text-zinc-600 text-sm">Your tracked symbols and price alerts will appear here.</p>
                                </div>
                            )}
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}
