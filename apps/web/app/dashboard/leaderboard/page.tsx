'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import Link from 'next/link';
import { apiFetch } from '../../../lib/api-client';
import {
    fetchUserPreferences,
    updateLeaderboardPreferences,
    type LeaderboardDirection,
    type LeaderboardPeriod,
    type LeaderboardSortBy,
} from '../../../lib/user-preferences';

interface PortfolioLeaderboardEntry {
    rank: number;
    portfolioId: string;
    portfolioName: string;
    ownerId: string;
    ownerName: string;
    returnPercentage: number;
    totalEquity: number;
    profitLoss: number;
    startEquity: number;
}

interface AccountLeaderboardEntry {
    rank: number;
    ownerId: string;
    ownerName: string;
    publicPortfolioCount: number;
    trustScore: number;
    winRate: number;
    returnPercentage: number;
    totalEquity: number;
    profitLoss: number;
    startEquity: number;
}

const DASHBOARD_LEADERBOARD_PREFERENCES_KEY = 'dashboard_leaderboard_preferences_v1';
const PERIOD_OPTIONS = ['1D', '1W', '1M', 'ALL'] as const;
const SORT_BY_OPTIONS: LeaderboardSortBy[] = ['RETURN_PERCENTAGE', 'PROFIT_LOSS', 'WIN_RATE', 'TRUST_SCORE'];
const SORT_DIRECTION_OPTIONS: LeaderboardDirection[] = ['DESC', 'ASC'];
const ACCOUNT_SORT_OPTIONS: LeaderboardSortBy[] = ['WIN_RATE', 'TRUST_SCORE'];

export default function LeaderboardPage() {
    const [entries, setEntries] = useState<(PortfolioLeaderboardEntry | AccountLeaderboardEntry)[]>([]);
    const [period, setPeriod] = useState<LeaderboardPeriod>('1D');
    const [sortBy, setSortBy] = useState<LeaderboardSortBy>('RETURN_PERCENTAGE');
    const [direction, setDirection] = useState<LeaderboardDirection>('DESC');
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [totalElements, setTotalElements] = useState(0);
    const [preferencesReady, setPreferencesReady] = useState(false);
    const [refreshing, setRefreshing] = useState(false);
    const [loading, setLoading] = useState(true);
    const skipFirstPersistRef = useRef(true);
    const isAccountMode = ACCOUNT_SORT_OPTIONS.includes(sortBy);

    useEffect(() => {
        let cancelled = false;
        const hydratePreferences = async () => {
            try {
                const saved = window.localStorage.getItem(DASHBOARD_LEADERBOARD_PREFERENCES_KEY);
                if (saved) {
                    const parsed: Partial<{
                        period: LeaderboardPeriod;
                        sortBy: LeaderboardSortBy;
                        direction: LeaderboardDirection;
                    }> = JSON.parse(saved);
                    if (typeof parsed.period === 'string' && PERIOD_OPTIONS.includes(parsed.period as LeaderboardPeriod)) {
                        setPeriod(parsed.period as LeaderboardPeriod);
                    }
                    if (typeof parsed.sortBy === 'string' && SORT_BY_OPTIONS.includes(parsed.sortBy as LeaderboardSortBy)) {
                        setSortBy(parsed.sortBy as LeaderboardSortBy);
                    }
                    if (typeof parsed.direction === 'string' && SORT_DIRECTION_OPTIONS.includes(parsed.direction as LeaderboardDirection)) {
                        setDirection(parsed.direction as LeaderboardDirection);
                    }
                }

                const userId = window.localStorage.getItem('userId');
                if (userId) {
                    const remote = await fetchUserPreferences(userId);
                    const dashboard = remote?.leaderboard?.dashboard;
                    if (!cancelled && dashboard) {
                        if (PERIOD_OPTIONS.includes(dashboard.period)) {
                            setPeriod(dashboard.period);
                        }
                        if (SORT_BY_OPTIONS.includes(dashboard.sortBy)) {
                            setSortBy(dashboard.sortBy);
                        }
                        if (SORT_DIRECTION_OPTIONS.includes(dashboard.direction)) {
                            setDirection(dashboard.direction);
                        }
                    }
                }
            } catch (error) {
                console.error('Failed to load leaderboard preferences:', error);
            } finally {
                if (!cancelled) {
                    setPreferencesReady(true);
                }
            }
        };

        hydratePreferences();

        return () => {
            cancelled = true;
        };
    }, []);

    const fetchLeaderboard = useCallback(async () => {
        try {
            const params = new URLSearchParams({
                period,
                sortBy,
                direction,
                page: page.toString(),
                size: '20',
            });
            const endpoint = isAccountMode ? '/api/v1/leaderboards/accounts' : '/api/v1/leaderboards';
            const res = await apiFetch(`${endpoint}?${params.toString()}`);
            if (res.ok) {
                const data = await res.json();
                setEntries(data.content || []);
                setTotalPages(data?.page?.totalPages || 1);
                setTotalElements(data?.page?.totalElements || 0);
            } else {
                console.error('Failed to fetch leaderboard');
            }
        } catch (error) {
            console.error(error);
        } finally {
            setLoading(false);
        }
    }, [isAccountMode, page, period, sortBy, direction]);

    useEffect(() => {
        if (!preferencesReady) {
            return;
        }
        setLoading(true);
        fetchLeaderboard();
        const interval = setInterval(fetchLeaderboard, 10000);
        return () => clearInterval(interval);
    }, [preferencesReady, fetchLeaderboard]);

    useEffect(() => {
        setPage(0);
    }, [period, sortBy, direction]);

    useEffect(() => {
        if (!preferencesReady) {
            return;
        }
        if (skipFirstPersistRef.current) {
            skipFirstPersistRef.current = false;
            return;
        }
        try {
            window.localStorage.setItem(
                DASHBOARD_LEADERBOARD_PREFERENCES_KEY,
                JSON.stringify({ period, sortBy, direction }),
            );
        } catch (error) {
            console.error('Failed to save leaderboard preferences:', error);
        }

        const userId = window.localStorage.getItem('userId');
        if (!userId) {
            return;
        }

        updateLeaderboardPreferences(userId, {
            dashboard: { period, sortBy, direction },
        }).catch(() => { });
    }, [preferencesReady, period, sortBy, direction]);

    const handleManualRefresh = async () => {
        setRefreshing(true);
        try {
            await apiFetch('/api/v1/leaderboards/refresh', { method: 'POST' });
            await fetchLeaderboard();
        } catch (error) {
            console.error(error);
        } finally {
            setRefreshing(false);
        }
    };

    return (
        <div className="p-8 pb-20 space-y-8 relative z-10 w-full max-w-[1200px] mx-auto">
            <header className="flex justify-between items-center bg-black/40 backdrop-blur-xl border border-white/10 p-6 rounded-2xl shadow-2xl relative overflow-hidden mb-8">
                <div className="absolute top-0 left-0 w-64 h-64 bg-green-500/10 rounded-full blur-[100px] pointer-events-none"></div>
                <div className="relative z-10">
                    <h1 className="text-3xl font-bold bg-gradient-to-r from-green-400 to-emerald-600 bg-clip-text text-transparent uppercase tracking-tighter">
                        Leaderboard
                    </h1>
                    <p className="text-zinc-500 text-sm mt-1">
                        {isAccountMode
                            ? 'Top accounts ranked by trust score or platform win rate'
                            : 'Top public portfolios ranked by selected period performance'}
                    </p>
                    <div className="mt-3 flex items-center gap-3 text-xs uppercase tracking-[0.2em]">
                        <span className={`rounded-full border px-3 py-1 ${isAccountMode ? 'border-blue-500/30 bg-blue-500/10 text-blue-300' : 'border-green-500/30 bg-green-500/10 text-green-300'}`}>
                            {isAccountMode ? 'Account Mode' : 'Portfolio Mode'}
                        </span>
                        <span className="text-zinc-600">
                            {totalElements} result{totalElements === 1 ? '' : 's'}
                        </span>
                    </div>
                </div>
                <nav className="flex gap-4 items-center">
                    <button
                        onClick={handleManualRefresh}
                        disabled={refreshing}
                        className={`px-4 py-2 rounded border transition-all text-sm font-bold uppercase tracking-wide flex items-center gap-2 ${refreshing
                            ? 'bg-zinc-800 border-zinc-700 text-zinc-500 cursor-not-allowed'
                            : 'bg-green-600/10 border-green-500/20 text-green-400 hover:bg-green-600/20 hover:border-green-500/40'
                            }`}
                    >
                        {refreshing ? (
                            <div className="w-3 h-3 border-2 border-green-400 border-t-transparent rounded-full animate-spin"></div>
                        ) : '↻'}
                        {refreshing ? 'Refreshing...' : 'Refresh'}
                    </button>
                    <Link href="/dashboard/analysis" className="px-4 py-2 rounded bg-zinc-900 border border-zinc-700 hover:bg-white/5 transition-colors text-sm font-bold uppercase tracking-wide text-zinc-400 hover:text-blue-400">
                        Analyses
                    </Link>
                    <Link href="/dashboard" className="px-4 py-2 rounded bg-zinc-900 border border-zinc-700 hover:bg-zinc-800 transition-colors text-sm font-bold uppercase tracking-wide">
                        Dashboard
                    </Link>
                </nav>
            </header>

            <div className="flex justify-center gap-2 mb-8">
                {PERIOD_OPTIONS.map((p) => (
                    <button
                        key={p}
                        onClick={() => setPeriod(p)}
                        disabled={isAccountMode}
                        className={`px-6 py-2 rounded-full text-sm font-bold uppercase tracking-widest transition-all ${period === p
                            ? 'bg-green-600 text-white shadow-[0_0_20px_rgba(34,197,94,0.4)]'
                            : 'bg-zinc-900 text-zinc-500 hover:bg-zinc-800'
                            }`}
                    >
                        {p}
                    </button>
                ))}
            </div>

            {isAccountMode && (
                <p className="text-center text-xs text-zinc-500 -mt-5 mb-6">
                    Win rate and trust score are account-level signals, so period tabs do not affect this mode.
                </p>
            )}

            <div className="flex justify-center gap-3 mb-8">
                <select
                    value={sortBy}
                    onChange={(e) => setSortBy(e.target.value as LeaderboardSortBy)}
                    className="px-4 py-2 rounded-lg bg-zinc-900 border border-zinc-700 text-sm font-bold uppercase tracking-wide text-zinc-200 focus:outline-none focus:border-green-500"
                >
                    <option value="RETURN_PERCENTAGE">Sort: Return %</option>
                    <option value="PROFIT_LOSS">Sort: P/L ($)</option>
                    <option value="WIN_RATE">Sort: Win Rate</option>
                    <option value="TRUST_SCORE">Sort: Trust</option>
                </select>
                <button
                    onClick={() => setDirection((prev) => (prev === 'DESC' ? 'ASC' : 'DESC'))}
                    className="px-4 py-2 rounded-lg bg-zinc-900 border border-zinc-700 text-sm font-bold uppercase tracking-wide text-zinc-200 hover:bg-zinc-800 transition-colors"
                >
                    Direction: {direction === 'DESC' ? 'Descending' : 'Ascending'}
                </button>
            </div>

            <div className="bg-black/40 backdrop-blur-xl border border-white/10 rounded-2xl overflow-hidden shadow-2xl relative">
                <table className="w-full text-left relative z-10">
                    <thead className="bg-zinc-950/50 text-zinc-500 text-[10px] uppercase tracking-tighter">
                        <tr>
                            <th className="p-4 font-bold w-16 text-center">Rank</th>
                            <th className="p-4 font-bold text-left">{isAccountMode ? 'Trader' : 'Portfolio'}</th>
                            <th className="p-4 font-bold text-right">{isAccountMode ? 'Public Portfolios' : 'Owner'}</th>
                            {isAccountMode && <th className="p-4 font-bold text-right">Win Rate</th>}
                            {isAccountMode && <th className="p-4 font-bold text-right">Trust</th>}
                            <th className="p-4 font-bold text-right">Return %</th>
                            <th className="p-4 font-bold text-right">P/L ($)</th>
                            <th className="p-4 font-bold text-right">Total Equity</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-zinc-800/50">
                        {loading ? (
                            <tr>
                                <td colSpan={isAccountMode ? 8 : 6} className="p-8 text-center text-zinc-500 animate-pulse">
                                    Loading rankings...
                                </td>
                            </tr>
                        ) : entries.length === 0 ? (
                            <tr>
                                <td colSpan={isAccountMode ? 8 : 6} className="p-8 text-center text-zinc-600 italic">
                                    {isAccountMode
                                        ? 'No public accounts found. Users appear here when they have at least one public portfolio.'
                                        : 'No public portfolios found for this period. Set your portfolio visibility to PUBLIC to appear here.'}
                                </td>
                            </tr>
                        ) : (
                            entries.map((entry, idx) => (
                                <tr key={`${'portfolioId' in entry ? entry.portfolioId : entry.ownerId}-${idx}`} className="hover:bg-white/5 transition-colors group">
                                    <td className="p-4 text-center font-bold font-mono text-zinc-400 group-hover:text-white">
                                        #{entry.rank}
                                    </td>
                                    <td className="p-4 font-bold text-white group-hover:text-green-400 transition-colors">
                                        {'portfolioId' in entry ? (
                                            <Link href={`/dashboard/portfolio/${entry.portfolioId}`}>
                                                {entry.portfolioName}
                                            </Link>
                                        ) : (
                                            <Link href={`/profile/${entry.ownerId}`}>
                                                {entry.ownerName || `User ${entry.ownerId.substring(0, 8)}`}
                                            </Link>
                                        )}
                                    </td>
                                    {'portfolioId' in entry ? (
                                        <td className="p-4 text-right text-zinc-500 text-xs font-mono group-hover:text-zinc-300">
                                            <Link href={`/profile/${entry.ownerId}`} className="hover:underline decoration-zinc-700">
                                                {entry.ownerName || `User ${entry.ownerId.substring(0, 8)}`}
                                            </Link>
                                        </td>
                                    ) : (
                                        <td className="p-4 text-right text-zinc-300 font-mono">
                                            {entry.publicPortfolioCount}
                                        </td>
                                    )}
                                    {'portfolioId' in entry ? null : (
                                        <>
                                            <td className={`p-4 text-right font-bold font-mono ${entry.winRate >= 50 ? 'text-green-500' : 'text-red-500'}`}>
                                                {entry.winRate.toFixed(1)}%
                                            </td>
                                            <td className={`p-4 text-right font-bold font-mono ${entry.trustScore >= 70 ? 'text-green-500' : entry.trustScore >= 40 ? 'text-yellow-500' : 'text-red-500'}`}>
                                                {entry.trustScore.toFixed(1)}
                                            </td>
                                        </>
                                    )}
                                    <td className={`p-4 text-right font-bold font-mono ${entry.returnPercentage >= 0 ? 'text-green-500' : 'text-red-500'
                                        }`}>
                                        {entry.returnPercentage > 0 ? '+' : ''}{entry.returnPercentage.toFixed(2)}%
                                    </td>
                                    <td className={`p-4 text-right font-bold font-mono ${entry.profitLoss >= 0 ? 'text-green-500' : 'text-red-500'
                                        }`}>
                                        {entry.profitLoss > 0 ? '+' : ''}${entry.profitLoss.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                    </td>
                                    <td className="p-4 text-right font-mono text-zinc-300">
                                        ${entry.totalEquity.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                    </td>
                                </tr>
                            ))
                        )}
                    </tbody>
                </table>
            </div>

            <div className="flex items-center justify-between rounded-2xl border border-white/10 bg-black/30 px-4 py-3">
                <p className="text-xs uppercase tracking-[0.2em] text-zinc-500">
                    Page {Math.min(page + 1, Math.max(totalPages, 1))} / {Math.max(totalPages, 1)}
                </p>
                <div className="flex gap-2">
                    <button
                        onClick={() => setPage((prev) => Math.max(prev - 1, 0))}
                        disabled={page === 0}
                        className="rounded-lg border border-zinc-700 bg-zinc-900 px-4 py-2 text-xs font-bold uppercase tracking-wide text-zinc-300 transition-colors hover:bg-zinc-800 disabled:cursor-not-allowed disabled:opacity-40"
                    >
                        Previous
                    </button>
                    <button
                        onClick={() => setPage((prev) => Math.min(prev + 1, Math.max(totalPages - 1, 0)))}
                        disabled={page >= totalPages - 1}
                        className="rounded-lg border border-zinc-700 bg-zinc-900 px-4 py-2 text-xs font-bold uppercase tracking-wide text-zinc-300 transition-colors hover:bg-zinc-800 disabled:cursor-not-allowed disabled:opacity-40"
                    >
                        Next
                    </button>
                </div>
            </div>
        </div>
    );
}
