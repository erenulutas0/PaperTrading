'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import Link from 'next/link';
import { apiFetch } from '../../lib/api-client';
import {
    fetchUserPreferences,
    updateLeaderboardPreferences,
    type LeaderboardDirection,
    type LeaderboardSortBy,
} from '../../lib/user-preferences';

interface LeaderboardEntry {
    portfolioId: string;
    portfolioName: string;
    ownerId: string;
    returnPercentage: number;
    totalEquity: number;
    profitLoss: number;
    startEquity: number;
}

const PUBLIC_LEADERBOARD_PREFERENCES_KEY = 'public_leaderboard_preferences_v1';
const SORT_BY_OPTIONS: LeaderboardSortBy[] = ['RETURN_PERCENTAGE', 'PROFIT_LOSS'];
const SORT_DIRECTION_OPTIONS: LeaderboardDirection[] = ['DESC', 'ASC'];

export default function LeaderboardPage() {
    const [entries, setEntries] = useState<LeaderboardEntry[]>([]);
    const [sortBy, setSortBy] = useState<LeaderboardSortBy>('RETURN_PERCENTAGE');
    const [direction, setDirection] = useState<LeaderboardDirection>('DESC');
    const [preferencesReady, setPreferencesReady] = useState(false);
    const [loading, setLoading] = useState(true);
    const skipFirstPersistRef = useRef(true);

    useEffect(() => {
        let cancelled = false;
        const hydratePreferences = async () => {
            try {
                const saved = window.localStorage.getItem(PUBLIC_LEADERBOARD_PREFERENCES_KEY);
                if (saved) {
                    const parsed: Partial<{ sortBy: LeaderboardSortBy; direction: LeaderboardDirection }> = JSON.parse(saved);
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
                    const publicPage = remote?.leaderboard?.publicPage;
                    if (!cancelled && publicPage) {
                        if (SORT_BY_OPTIONS.includes(publicPage.sortBy)) {
                            setSortBy(publicPage.sortBy);
                        }
                        if (SORT_DIRECTION_OPTIONS.includes(publicPage.direction)) {
                            setDirection(publicPage.direction);
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
                period: '1D',
                page: '0',
                size: '50',
                sortBy,
                direction,
            });
            const res = await apiFetch(`/api/v1/leaderboards?${params.toString()}`);
            if (res.ok) {
                const data = await res.json();
                setEntries(data.content || []);
            }
        } catch (error) {
            console.error('Failed to fetch leaderboard:', error);
        } finally {
            setLoading(false);
        }
    }, [sortBy, direction]);

    useEffect(() => {
        if (!preferencesReady) {
            return;
        }
        fetchLeaderboard();
        const interval = setInterval(fetchLeaderboard, 5000);
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
                PUBLIC_LEADERBOARD_PREFERENCES_KEY,
                JSON.stringify({ sortBy, direction }),
            );
        } catch (error) {
            console.error('Failed to save leaderboard preferences:', error);
        }

        const userId = window.localStorage.getItem('userId');
        if (!userId) {
            return;
        }

        updateLeaderboardPreferences(userId, {
            publicPage: { sortBy, direction },
        }).catch(() => { });
    }, [preferencesReady, sortBy, direction]);

    return (
        <div className="min-h-screen bg-black text-white p-8">
            <header className="mb-12 flex justify-between items-center max-w-6xl mx-auto">
                <div className="flex items-center gap-4">
                    <Link href="/" className="text-2xl font-bold tracking-tight">PaperTrade<span className="text-green-500">Pro</span></Link>
                    <span className="text-zinc-600">|</span>
                    <h1 className="text-xl font-medium text-zinc-300">Leaderboards</h1>
                </div>
                <div className="flex gap-4">
                    <select
                        value={sortBy}
                        onChange={(e) => setSortBy(e.target.value as LeaderboardSortBy)}
                        className="text-sm bg-white/10 hover:bg-white/20 px-3 py-2 rounded border border-white/10"
                    >
                        <option value="RETURN_PERCENTAGE">Return %</option>
                        <option value="PROFIT_LOSS">P/L ($)</option>
                    </select>
                    <button
                        onClick={() => setDirection((prev) => (prev === 'DESC' ? 'ASC' : 'DESC'))}
                        className="text-sm bg-white/10 hover:bg-white/20 px-3 py-2 rounded transition-colors"
                    >
                        {direction === 'DESC' ? 'Desc' : 'Asc'}
                    </button>
                    <Link href="/dashboard" className="text-sm bg-white/10 hover:bg-white/20 px-4 py-2 rounded transition-colors">My Dashboard</Link>
                </div>
            </header>

            <main className="max-w-6xl mx-auto">
                <div className="bg-zinc-900/50 border border-zinc-800 rounded-2xl overflow-hidden backdrop-blur-sm">
                    {/* Table Header */}
                    <div className="grid grid-cols-12 gap-4 p-4 border-b border-zinc-800 text-xs font-semibold text-zinc-500 uppercase tracking-wider">
                        <div className="col-span-1 text-center">Rank</div>
                        <div className="col-span-11 md:col-span-4">Portfolio</div>
                        <div className="hidden md:block col-span-2 text-right">P/L ($)</div>
                        <div className="hidden md:block col-span-2 text-right">Return %</div>
                        <div className="hidden md:block col-span-3 text-right">Total Value</div>
                    </div>

                    {/* Table Body */}
                    {loading ? (
                        <div className="p-12 text-center text-zinc-500">Updating ranks...</div>
                    ) : (
                        entries.map((entry, index) => {
                            const roi = entry.returnPercentage;
                            const isPositive = roi >= 0;
                            const pl = entry.profitLoss || 0;
                            const plPositive = pl >= 0;

                            // Top 3 Styling
                            let rankStyle = "bg-zinc-800 text-zinc-400";
                            if (index === 0) rankStyle = "bg-yellow-500/20 text-yellow-500 border border-yellow-500/20";
                            if (index === 1) rankStyle = "bg-zinc-300/20 text-zinc-300 border border-zinc-300/20";
                            if (index === 2) rankStyle = "bg-orange-700/20 text-orange-400 border border-orange-700/20";

                            return (
                                <div key={entry.portfolioId} className="grid grid-cols-12 gap-4 p-4 border-b border-zinc-800/50 hover:bg-white/5 transition-colors items-center">
                                    <div className="col-span-1 flex justify-center">
                                        <div className={`w-8 h-8 rounded-full flex items-center justify-center font-bold text-sm ${rankStyle}`}>
                                            {index + 1}
                                        </div>
                                    </div>

                                    <div className="col-span-11 md:col-span-4">
                                        <h3 className="font-bold text-lg">{entry.portfolioName}</h3>
                                        <p className="text-xs text-zinc-500 font-mono">ID: {entry.portfolioId.substring(0, 8)}...</p>
                                    </div>

                                    <div className="col-span-6 md:col-span-2 text-right flex flex-col justify-center">
                                        <span className="block md:hidden text-xs text-zinc-500 mb-1">P/L</span>
                                        <span className={`font-mono text-lg font-bold ${plPositive ? 'text-green-500' : 'text-red-500'}`}>
                                            {plPositive ? '+' : ''}${pl.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                        </span>
                                    </div>

                                    <div className="col-span-6 md:col-span-2 text-right flex flex-col justify-center">
                                        <span className="block md:hidden text-xs text-zinc-500 mb-1">ROI</span>
                                        <span className={`font-mono text-lg font-bold ${isPositive ? 'text-green-500' : 'text-red-500'}`}>
                                            {isPositive ? '+' : ''}{roi.toFixed(2)}%
                                        </span>
                                    </div>

                                    <div className="hidden md:block col-span-3 text-right flex flex-col justify-center">
                                        <span className="font-mono text-lg font-bold">${entry.totalEquity.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
                                    </div>
                                </div>
                            );
                        })
                    )}
                </div>
            </main>
        </div>
    );
}
