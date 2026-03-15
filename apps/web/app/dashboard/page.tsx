'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import TradeModal from '../../components/TradeModal';
import ActivityFeed from '../../components/ActivityFeed';
import LogoutButton from '../../components/LogoutButton';
import { extractContent } from '../../lib/page';
import { apiFetch } from '../../lib/api-client';

interface Portfolio {
    id: string;
    name: string;
    ownerId: string;
    visibility?: 'PUBLIC' | 'PRIVATE';
    balance: number;
    items?: {
        id: string;
        symbol: string;
        quantity: number;
        averagePrice: number;
        leverage: number;
        side: 'LONG' | 'SHORT';
    }[];
    createdAt: string;
}

interface CurrentUserProfile {
    id: string;
    displayName: string;
    username: string;
    trustScore?: number;
}

export default function Dashboard() {
    const router = useRouter();
    const [portfolios, setPortfolios] = useState<Portfolio[]>([]);
    const [compareTargets, setCompareTargets] = useState<Record<string, string>>({});
    const [prices, setPrices] = useState<Record<string, number>>({});
    const [name, setName] = useState('');
    const [currentUserId, setCurrentUserId] = useState<string | null>(null);
    const [currentProfile, setCurrentProfile] = useState<CurrentUserProfile | null>(null);
    const [loading, setLoading] = useState(true);
    const [visibilityUpdatingId, setVisibilityUpdatingId] = useState<string | null>(null);

    // Trade Modal state
    const [tradeConfig, setTradeConfig] = useState<{ symbol: string; portfolioId?: string } | null>(null);

    const formatCompactCurrency = (value: number) =>
        `$${value.toLocaleString(undefined, { maximumFractionDigits: 0 })}`;

    const DashboardEmptyPanel = ({
        title,
        body,
    }: {
        title: string;
        body: string;
    }) => (
        <div className="rounded-2xl border border-dashed border-white/10 bg-black/30 px-5 py-6 text-zinc-400">
            <p className="text-sm font-medium text-zinc-200">{title}</p>
            <p className="mt-2 text-sm text-zinc-500">{body}</p>
        </div>
    );

    useEffect(() => {
        const userId = localStorage.getItem('userId');
        if (!userId) {
            router.push('/auth/login');
            return;
        }

        setCurrentUserId(userId);
        fetchPortfolios(userId);
        fetchCurrentProfile(userId);
        const interval = setInterval(fetchPrices, 3000);
        fetchPrices();
        return () => clearInterval(interval);
    }, []);

    const fetchCurrentProfile = async (userId: string) => {
        try {
            const res = await apiFetch(`/api/v1/users/${userId}/profile`);
            if (!res.ok) {
                return;
            }
            setCurrentProfile(await res.json());
        } catch (error) {
            console.error('Error fetching profile:', error);
        }
    };

    const fetchPrices = async () => {
        try {
            const res = await apiFetch('/api/v1/market/prices');
            if (res.ok) {
                const data = await res.json();
                setPrices(data);
            }
        } catch (error) {
            console.error('Error fetching prices:', error);
        }
    };

    const fetchPortfolios = async (userId: string) => {
        try {
            const res = await apiFetch(`/api/v1/portfolios?ownerId=${encodeURIComponent(userId)}&t=${Date.now()}`, {
                cache: 'no-store',
            });
            if (!res.ok) throw new Error('Failed to fetch');
            const data = await res.json();
            const nextPortfolios = extractContent<Portfolio>(data);
            setPortfolios(nextPortfolios);
            setCompareTargets((current) => {
                const nextState: Record<string, string> = {};
                nextPortfolios.forEach((portfolio) => {
                    const currentTarget = current[portfolio.id];
                    nextState[portfolio.id] = currentTarget && currentTarget !== portfolio.id
                        ? currentTarget
                        : '';
                });
                return nextState;
            });
        } catch (error) {
            console.error(error);
        } finally {
            setLoading(false);
        }
    };

    const createPortfolio = async (e: React.FormEvent) => {
        e.preventDefault();
        const userId = localStorage.getItem('userId');
        try {
            const res = await apiFetch('/api/v1/portfolios', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name, ownerId: userId }),
            });
            if (res.ok) {
                setName('');
                if (userId) fetchPortfolios(userId);
            }
        } catch (error) {
            console.error(error);
        }
    };

    const deletePortfolio = async (id: string) => {
        if (!confirm('Are you sure you want to delete this portfolio?')) return;
        try {
            const res = await apiFetch(`/api/v1/portfolios/${id}`, {
                method: 'DELETE',
            });
            if (res.ok) {
                const userId = localStorage.getItem('userId');
                if (userId) fetchPortfolios(userId);
            }
        } catch (error) {
            console.error(error);
        }
    };

    const toggleVisibility = async (portfolio: Portfolio) => {
        const currentVisibility = portfolio.visibility ?? 'PRIVATE';
        const newVisibility = currentVisibility === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC';

        setVisibilityUpdatingId(portfolio.id);
        try {
            const res = await apiFetch(`/api/v1/portfolios/${portfolio.id}/visibility`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ visibility: newVisibility }),
            });

            if (!res.ok) {
                const message = await res.text();
                throw new Error(message || `Failed to update visibility (${res.status})`);
            }

            const updated = await res.json();
            setPortfolios((prev) =>
                prev.map((p) =>
                    p.id === portfolio.id
                        ? { ...p, visibility: updated.visibility ?? newVisibility }
                        : p,
                ),
            );

            const userId = localStorage.getItem('userId');
            if (userId) {
                await fetchPortfolios(userId);
            }
        } catch (error) {
            console.error(error);
            alert(`Could not update visibility: ${error instanceof Error ? error.message : 'Unknown error'}`);
        } finally {
            setVisibilityUpdatingId(null);
        }
    };

    return (
        <div className="p-8 pb-20 relative z-10">
            <header className="mb-10 flex justify-between items-center bg-black/40 backdrop-blur-xl border border-white/10 p-6 rounded-2xl shadow-2xl">
                <div>
                    <h1 className="text-3xl font-bold bg-gradient-to-r from-emerald-400 via-green-500 to-teal-500 bg-clip-text text-transparent">Terminal</h1>
                    {currentProfile && (
                        <Link
                            href={`/profile/${currentProfile.id}`}
                            className="mt-2 inline-flex items-center gap-3 rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs text-zinc-300 hover:border-emerald-500/40 hover:text-white transition-colors"
                        >
                            <span>@{currentProfile.username}</span>
                            <span className="text-zinc-600">|</span>
                            <span className="uppercase tracking-[0.2em] text-zinc-500">Trust</span>
                            <span className={`font-bold ${currentProfile.trustScore !== undefined && currentProfile.trustScore >= 70
                                    ? 'text-emerald-400'
                                    : currentProfile.trustScore !== undefined && currentProfile.trustScore >= 40
                                        ? 'text-amber-400'
                                        : 'text-red-400'
                                }`}>
                                {currentProfile.trustScore !== undefined ? currentProfile.trustScore.toFixed(1) : 'N/A'}
                            </span>
                        </Link>
                    )}
                </div>
                <div className="flex gap-6 items-center">
                    {currentUserId && (
                        <Link href={`/profile/${currentUserId}`} className="text-xs uppercase tracking-[0.2em] font-bold text-zinc-400 hover:text-cyan-400 transition-colors">Profile</Link>
                    )}
                    <Link href="/dashboard/analysis" className="text-xs uppercase tracking-[0.2em] font-bold text-zinc-400 hover:text-green-400 transition-colors">Analyses</Link>
                    <Link href="/dashboard/leaderboard" className="text-xs uppercase tracking-[0.2em] font-bold text-zinc-400 hover:text-green-400 transition-colors">Leaderboard</Link>
                    <Link href="/discover" className="text-xs uppercase tracking-[0.2em] font-bold text-zinc-400 hover:text-blue-400 transition-colors">Discover</Link>
                    <Link href="/watchlist" className="text-xs uppercase tracking-[0.2em] font-bold text-zinc-400 hover:text-amber-400 transition-colors">Watchlist</Link>
                    <Link href="/tournaments" className="text-xs uppercase tracking-[0.2em] font-bold text-zinc-400 hover:text-yellow-400 transition-colors">Tournaments</Link>
                    <div className="w-px h-4 bg-white/10"></div>
                    <LogoutButton className="text-xs uppercase tracking-[0.2em] font-bold text-zinc-400 hover:text-white transition-colors disabled:opacity-60" />
                </div>
            </header>

            <div className="grid grid-cols-1 lg:grid-cols-4 gap-8">
                {/* Sidebar: Market + Social */}
                <div className="lg:col-span-1 space-y-8">
                    {/* Live Market */}
                    <section className="bg-black/40 backdrop-blur-xl border border-white/10 rounded-2xl p-6 shadow-2xl relative overflow-hidden">
                        <div className="absolute top-0 right-0 w-32 h-32 bg-green-500/10 rounded-full blur-3xl"></div>
                        <h2 className="text-[10px] font-bold uppercase tracking-widest mb-4 text-green-500 flex items-center gap-2">
                            <span className="w-2 h-2 rounded-full bg-green-500 animate-pulse"></span> Market Data
                        </h2>
                        <div className="divide-y divide-white/5 relative z-10">
                            {Object.entries(prices).length === 0 ? (
                                <div className="space-y-3 py-2">
                                    <div className="h-3 w-28 animate-pulse rounded bg-white/10"></div>
                                    <div className="h-10 rounded-xl animate-pulse bg-white/5"></div>
                                    <div className="h-10 rounded-xl animate-pulse bg-white/5"></div>
                                    <p className="text-zinc-600 text-xs italic">Connecting to Binance WS...</p>
                                </div>
                            ) : (
                                Object.entries(prices).map(([symbol, price]) => (
                                    <div
                                        key={symbol}
                                        className="py-3 flex justify-between items-center hover:px-2 transition-all duration-300 group cursor-pointer"
                                        onClick={() => setTradeConfig({ symbol })}
                                    >
                                        <div className="flex items-center gap-3">
                                            <p className="text-xs font-bold text-zinc-300 group-hover:text-white transition-colors">{symbol.replace('USDT', '')}</p>
                                            <span className="text-[9px] text-zinc-600 font-mono tracking-widest">USDT</span>
                                        </div>
                                        <div className="text-right">
                                            <p className="font-mono text-sm text-green-400 font-bold">${price.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</p>
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    </section>

                    {/* Activity Feed */}
                    <ActivityFeed />
                </div>

                {/* Main Content: Portfolios */}
                <div className="lg:col-span-3 grid grid-cols-1 md:grid-cols-2 gap-8">
                    <div className="bg-black/40 backdrop-blur-xl border border-white/10 p-6 rounded-2xl h-fit shadow-2xl relative overflow-hidden">
                        <div className="absolute top-0 right-0 w-32 h-32 bg-emerald-500/10 rounded-full blur-3xl"></div>
                        <h2 className="text-xl font-semibold mb-4 text-zinc-200 relative z-10">New Portfolio</h2>
                        <form onSubmit={createPortfolio} className="space-y-4">
                            <input
                                type="text"
                                placeholder="Portfolio Name"
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                                className="w-full bg-black/40 border border-zinc-700 rounded p-3 text-white focus:outline-none focus:border-green-500 transition-all placeholder:text-zinc-600"
                                required
                            />
                            <button
                                type="submit"
                                className="w-full bg-green-600 hover:bg-green-500 text-white font-bold py-3 rounded transition-all shadow-lg hover:shadow-green-900/20"
                            >
                                Create Portfolio
                            </button>
                        </form>
                    </div>

                    {/* List Portfolios */}
                    {loading ? (
                        Array.from({ length: 2 }).map((_, index) => (
                            <div key={index} className="bg-black/40 backdrop-blur-xl border border-white/10 p-6 rounded-2xl shadow-2xl">
                                <div className="h-6 w-40 animate-pulse rounded bg-white/10"></div>
                                <div className="mt-2 h-3 w-24 animate-pulse rounded bg-white/5"></div>
                                <div className="mt-6 grid gap-3 sm:grid-cols-2">
                                    <div className="h-16 rounded-xl animate-pulse bg-white/5"></div>
                                    <div className="h-16 rounded-xl animate-pulse bg-white/5"></div>
                                </div>
                                <div className="mt-6 h-28 rounded-xl animate-pulse bg-white/5"></div>
                                <div className="mt-6 h-12 rounded-xl animate-pulse bg-white/5"></div>
                            </div>
                        ))
                    ) : portfolios.length === 0 ? (
                        <div className="md:col-span-2">
                            <DashboardEmptyPanel
                                title="No portfolios yet"
                                body="Create your first portfolio to start building history, analytics, and leaderboard-eligible performance."
                            />
                        </div>
                    ) : portfolios.map((p) => (
                        <div key={p.id} className="bg-black/40 backdrop-blur-xl border border-white/10 p-6 rounded-2xl hover:border-green-500/30 hover:shadow-[0_0_30px_rgba(34,197,94,0.1)] transition-all duration-300 flex flex-col relative group overflow-hidden">
                            <div className="absolute top-0 left-0 w-32 h-32 bg-green-500/5 rounded-full blur-3xl pointer-events-none"></div>
                            <button
                                type="button"
                                onClick={() => deletePortfolio(p.id)}
                                className="absolute top-4 right-4 text-zinc-600 hover:text-red-500 transition-colors opacity-0 group-hover:opacity-100 z-20"
                            >
                                <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                                    <path fillRule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clipRule="evenodd" />
                                </svg>
                            </button>

                            <div className="flex justify-between items-start mb-6 relative z-10">
                                <div className="relative z-20">
                                    <h2 className="text-xl font-bold text-white">{p.name}</h2>
                                    <p className="text-[10px] text-zinc-500 font-mono tracking-tighter">ID: {p.id}</p>
                                    <p className="mt-1 text-[10px] uppercase tracking-widest">
                                        <span className={
                                            p.visibility === 'PUBLIC'
                                                ? 'text-emerald-400'
                                                : 'text-amber-400'
                                        }>
                                            {p.visibility}
                                        </span>
                                        {p.visibility !== 'PUBLIC' && (
                                            <span className="text-zinc-500 ml-2">Not on leaderboard</span>
                                        )}
                                    </p>
                                    <button
                                        type="button"
                                        onClick={() => toggleVisibility(p)}
                                        disabled={visibilityUpdatingId === p.id}
                                        className={`mt-2 px-3 py-1 rounded-md text-[10px] font-bold uppercase tracking-widest border transition-all ${p.visibility === 'PUBLIC'
                                            ? 'border-blue-500/40 text-blue-400 hover:bg-blue-500/10'
                                            : 'border-emerald-500/40 text-emerald-400 hover:bg-emerald-500/10'
                                            } disabled:opacity-60 disabled:cursor-not-allowed cursor-pointer relative z-20`}
                                    >
                                        {visibilityUpdatingId === p.id
                                            ? 'Updating...'
                                            : p.visibility === 'PUBLIC'
                                                ? 'Make Private'
                                                : 'Make Public'}
                                    </button>
                                </div>
                                <div className="text-right">
                                    <p className="text-[10px] text-zinc-500 uppercase font-bold tracking-widest">Available Cash</p>
                                    <p className="font-mono text-green-400 font-bold text-xl">${p.balance?.toLocaleString() ?? '0.00'}</p>
                                </div>
                            </div>

                            {/* Assets Summary */}
                            <div className="mb-6 flex-1">
                                <p className="text-[10px] text-zinc-500 mb-2 uppercase font-bold tracking-widest">Holdings</p>
                                <div className="space-y-2">
                                    {p.items && p.items.length > 0 ? (
                                        p.items.map((item) => {
                                            const currentPrice = prices[item.symbol] ?? item.averagePrice;
                                            const value = item.quantity * currentPrice;
                                            return (
                                                <div key={item.id} className="flex justify-between text-sm items-center py-1 border-b border-white/5">
                                                    <span className="text-zinc-300 font-medium">
                                                        {item.symbol}
                                                        <span className={`ml-1 text-[10px] px-1 rounded font-bold ${item.side === 'SHORT' ? 'bg-red-500/20 text-red-500' : 'bg-green-500/20 text-green-500'}`}>
                                                            {item.side} {item.leverage > 1 ? `${item.leverage}x` : ''}
                                                        </span>
                                                    </span>
                                                    <span className="text-zinc-400 text-xs font-mono">
                                                        {item.quantity}  <span className="text-zinc-700">|</span>  ${value.toLocaleString(undefined, { maximumFractionDigits: 0 })}
                                                    </span>
                                                </div>
                                            );
                                        })
                                    ) : (
                                        <p className="text-sm text-zinc-600 italic">No open positions</p>
                                    )}
                                </div>
                            </div>

                            {(() => {
                                let totalCostBasis = 0;
                                let totalCurrentValue = 0;
                                const compareCandidates = portfolios.filter((candidate) => candidate.id !== p.id);
                                const compareTargetId = compareTargets[p.id] ?? '';
                                const compareHref = compareTargetId
                                    ? `/analytics/${p.id}?compare=${encodeURIComponent(compareTargetId)}`
                                    : `/analytics/${p.id}`;

                                p.items?.forEach(item => {
                                    const currentPrice = prices[item.symbol] ?? item.averagePrice;
                                    const lev = item.leverage || 1;
                                    const costBasis = (item.quantity * item.averagePrice) / lev;

                                    let unrealizedPL;
                                    if (item.side === 'SHORT') {
                                        unrealizedPL = (item.averagePrice - currentPrice) * item.quantity;
                                    } else {
                                        unrealizedPL = (currentPrice - item.averagePrice) * item.quantity;
                                    }

                                    totalCostBasis += costBasis;
                                    totalCurrentValue += (costBasis + unrealizedPL);
                                });

                                const pl = totalCurrentValue - totalCostBasis;
                                const plPercent = totalCostBasis > 0 ? (pl / totalCostBasis) * 100 : 0;
                                const isPositive = pl >= 0;
                                const holdingsCount = p.items?.length ?? 0;
                                const grossExposure = totalCostBasis;
                                const estimatedEquity = (p.balance ?? 0) + totalCurrentValue;
                                const createdLabel = p.createdAt
                                    ? new Date(p.createdAt).toLocaleDateString([], { year: 'numeric', month: 'short', day: '2-digit' })
                                    : 'N/A';

                                return (
                                    <div className="mt-auto pt-6 border-t border-zinc-800">
                                        <div className="mb-4 grid gap-3 sm:grid-cols-3 relative z-10">
                                            <div className="rounded-xl border border-white/5 bg-black/20 px-4 py-3">
                                                <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Portfolio Health</p>
                                                <p className="mt-2 text-lg font-bold text-white">{formatCompactCurrency(estimatedEquity)}</p>
                                                <p className="mt-1 text-[11px] text-zinc-500">Cash plus live marked positions</p>
                                            </div>
                                            <div className="rounded-xl border border-white/5 bg-black/20 px-4 py-3">
                                                <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Open Risk</p>
                                                <p className="mt-2 text-lg font-bold text-zinc-200">{formatCompactCurrency(grossExposure)}</p>
                                                <p className="mt-1 text-[11px] text-zinc-500">{holdingsCount} active holdings</p>
                                            </div>
                                            <div className="rounded-xl border border-white/5 bg-black/20 px-4 py-3">
                                                <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Analytics Context</p>
                                                <p className="mt-2 text-sm font-bold text-zinc-200">{p.visibility === 'PUBLIC' ? 'Leaderboard eligible' : 'Private analytics only'}</p>
                                                <p className="mt-1 text-[11px] text-zinc-500">Created {createdLabel}</p>
                                            </div>
                                        </div>

                                        <div className="flex justify-between items-center mb-4">
                                            <div className="flex flex-col relative z-10">
                                                <span className="text-[10px] text-zinc-500 uppercase font-bold tracking-widest">Unrealized P/L</span>
                                                <span className={`font-mono text-sm font-bold ${isPositive ? 'text-green-500' : 'text-red-500'}`}>
                                                    {isPositive ? '▲' : '▼'} {Math.abs(plPercent).toFixed(2)}% (${Math.abs(pl).toLocaleString(undefined, { maximumFractionDigits: 2 })})
                                                </span>
                                            </div>
                                            <div className="flex gap-2 relative z-10 flex-wrap justify-end">
                                                <Link href={`/analytics/${p.id}`} className="text-xs bg-blue-500/10 border border-blue-500/20 hover:bg-blue-500/20 px-4 py-2 rounded-lg transition-colors text-blue-300">
                                                    Analytics
                                                </Link>
                                                <Link href={`/dashboard/portfolio/${p.id}`} className="text-xs bg-white/5 border border-white/10 hover:bg-white/10 px-4 py-2 rounded-lg transition-colors text-zinc-300">
                                                    View History
                                                </Link>
                                            </div>
                                        </div>

                                        <div className="mb-4 rounded-xl border border-white/5 bg-black/20 p-3 relative z-10">
                                            <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
                                                <div className="min-w-0 flex-1">
                                                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Quick Compare</p>
                                                    <p className="mt-1 text-[11px] text-zinc-600">Open analytics with another portfolio already loaded as the compare target. Best used after both portfolios have enough snapshot history.</p>
                                                </div>
                                                <div className="flex w-full flex-col gap-2 sm:w-auto sm:flex-row">
                                                    <select
                                                        value={compareTargetId}
                                                        onChange={(event) => {
                                                            const nextTarget = event.target.value;
                                                            setCompareTargets((current) => ({
                                                                ...current,
                                                                [p.id]: nextTarget,
                                                            }));
                                                        }}
                                                        disabled={compareCandidates.length === 0}
                                                        className="min-w-[220px] rounded-lg border border-white/10 bg-zinc-950/80 px-3 py-2 text-xs text-white outline-none transition-colors focus:border-cyan-500/40 disabled:cursor-not-allowed disabled:opacity-50"
                                                    >
                                                        <option value="">{compareCandidates.length === 0 ? 'No other portfolios' : 'Select compare portfolio'}</option>
                                                        {compareCandidates.map((candidate) => (
                                                            <option key={candidate.id} value={candidate.id}>
                                                                {candidate.name} {candidate.visibility ? `(${candidate.visibility})` : ''}
                                                            </option>
                                                        ))}
                                                    </select>
                                                    <button
                                                        type="button"
                                                        disabled={!compareTargetId}
                                                        onClick={() => {
                                                            if (!compareTargetId) {
                                                                return;
                                                            }
                                                            router.push(compareHref);
                                                        }}
                                                        className={`inline-flex items-center justify-center rounded-lg border px-3 py-2 text-xs font-bold transition-colors ${
                                                            compareTargetId
                                                                ? 'border-cyan-500/20 bg-cyan-500/10 text-cyan-300 hover:bg-cyan-500/20'
                                                                : 'border-white/10 bg-white/5 text-zinc-500'
                                                        } disabled:cursor-not-allowed disabled:opacity-60`}
                                                    >
                                                        Compare In Analytics
                                                    </button>
                                                </div>
                                            </div>
                                        </div>

                                        <button
                                            onClick={() => setTradeConfig({ symbol: 'BTCUSDT', portfolioId: p.id })}
                                            className="w-full bg-green-600/90 hover:bg-green-500 text-white font-bold py-3 rounded-xl border border-green-500/50 text-sm transition-all shadow-[0_0_15px_rgba(34,197,94,0.2)] active:scale-95 relative z-10"
                                        >
                                            Open Trade Ticket
                                        </button>
                                    </div>
                                );
                            })()}
                        </div>
                    ))}
                </div>
            </div>

            {/* Trade Modal */}
            {tradeConfig && (
                <TradeModal
                    symbol={tradeConfig.symbol}
                    portfolios={portfolios}
                    availablePrices={prices}
                    initialPortfolioId={tradeConfig.portfolioId}
                    onClose={() => setTradeConfig(null)}
                    onSuccess={() => {
                        const userId = localStorage.getItem('userId');
                        if (userId) fetchPortfolios(userId);
                    }}
                />
            )}
        </div>
    );
}
