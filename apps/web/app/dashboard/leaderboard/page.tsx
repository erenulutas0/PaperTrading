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

interface LeaderboardEntry {
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

const DASHBOARD_LEADERBOARD_PREFERENCES_KEY = 'dashboard_leaderboard_preferences_v1';
const PERIOD_OPTIONS = ['1D', '1W', '1M', 'ALL'] as const;
const SORT_BY_OPTIONS: LeaderboardSortBy[] = ['RETURN_PERCENTAGE', 'PROFIT_LOSS'];
const SORT_DIRECTION_OPTIONS: LeaderboardDirection[] = ['DESC', 'ASC'];

export default function LeaderboardPage() {
    const [entries, setEntries] = useState<LeaderboardEntry[]>([]);
    const [period, setPeriod] = useState<LeaderboardPeriod>('1D');
    const [sortBy, setSortBy] = useState<LeaderboardSortBy>('RETURN_PERCENTAGE');
    const [direction, setDirection] = useState<LeaderboardDirection>('DESC');
    const [preferencesReady, setPreferencesReady] = useState(false);
    const [refreshing, setRefreshing] = useState(false);
    const [loading, setLoading] = useState(true);
    const skipFirstPersistRef = useRef(true);

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
            });
            const res = await apiFetch(`/api/v1/leaderboards?${params.toString()}`);
            if (res.ok) {
                const data = await res.json();
                setEntries(data.content || []);
            } else {
                console.error('Failed to fetch leaderboard');
            }
        } catch (error) {
            console.error(error);
        } finally {
            setLoading(false);
        }
    }, [period, sortBy, direction]);

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
                    <p className="text-zinc-500 text-sm mt-1">Top performing public portfolios by selected period performance</p>
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
                        className={`px-6 py-2 rounded-full text-sm font-bold uppercase tracking-widest transition-all ${period === p
                            ? 'bg-green-600 text-white shadow-[0_0_20px_rgba(34,197,94,0.4)]'
                            : 'bg-zinc-900 text-zinc-500 hover:bg-zinc-800'
                            }`}
                    >
                        {p}
                    </button>
                ))}
            </div>

            <div className="flex justify-center gap-3 mb-8">
                <select
                    value={sortBy}
                    onChange={(e) => setSortBy(e.target.value as LeaderboardSortBy)}
                    className="px-4 py-2 rounded-lg bg-zinc-900 border border-zinc-700 text-sm font-bold uppercase tracking-wide text-zinc-200 focus:outline-none focus:border-green-500"
                >
                    <option value="RETURN_PERCENTAGE">Sort: Return %</option>
                    <option value="PROFIT_LOSS">Sort: P/L ($)</option>
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
                            <th className="p-4 font-bold text-left">Portfolio</th>
                            <th className="p-4 font-bold text-right">Owner</th>
                            <th className="p-4 font-bold text-right">Return %</th>
                            <th className="p-4 font-bold text-right">P/L ($)</th>
                            <th className="p-4 font-bold text-right">Total Equity</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-zinc-800/50">
                        {loading ? (
                            <tr>
                                <td colSpan={6} className="p-8 text-center text-zinc-500 animate-pulse">
                                    Loading rankings...
                                </td>
                            </tr>
                        ) : entries.length === 0 ? (
                            <tr>
                                <td colSpan={6} className="p-8 text-center text-zinc-600 italic">
                                    No public portfolios found for this period. Set your portfolio visibility to PUBLIC to appear here.
                                </td>
                            </tr>
                        ) : (
                            entries.map((entry, idx) => (
                                <tr key={`${entry.portfolioId}-${idx}`} className="hover:bg-white/5 transition-colors group">
                                    <td className="p-4 text-center font-bold font-mono text-zinc-400 group-hover:text-white">
                                        #{entry.rank}
                                    </td>
                                    <td className="p-4 font-bold text-white group-hover:text-green-400 transition-colors">
                                        <Link href={`/dashboard/portfolio/${entry.portfolioId}`}>
                                            {entry.portfolioName}
                                        </Link>
                                    </td>
                                    <td className="p-4 text-right text-zinc-500 text-xs font-mono group-hover:text-zinc-300">
                                        <Link href={`/profile/${entry.ownerId}`} className="hover:underline decoration-zinc-700">
                                            {entry.ownerName || `User ${entry.ownerId.substring(0, 8)}`}
                                        </Link>
                                    </td>
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
        </div>
    );
}
