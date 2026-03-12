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

interface PortfolioLeaderboardEntry {
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

const PUBLIC_LEADERBOARD_PREFERENCES_KEY = 'public_leaderboard_preferences_v1';
const SORT_BY_OPTIONS: LeaderboardSortBy[] = ['RETURN_PERCENTAGE', 'PROFIT_LOSS', 'WIN_RATE', 'TRUST_SCORE'];
const SORT_DIRECTION_OPTIONS: LeaderboardDirection[] = ['DESC', 'ASC'];
const ACCOUNT_SORT_OPTIONS: LeaderboardSortBy[] = ['WIN_RATE', 'TRUST_SCORE'];

export default function LeaderboardPage() {
    const [entries, setEntries] = useState<(PortfolioLeaderboardEntry | AccountLeaderboardEntry)[]>([]);
    const [sortBy, setSortBy] = useState<LeaderboardSortBy>('RETURN_PERCENTAGE');
    const [direction, setDirection] = useState<LeaderboardDirection>('DESC');
    const [preferencesReady, setPreferencesReady] = useState(false);
    const [loading, setLoading] = useState(true);
    const skipFirstPersistRef = useRef(true);
    const isAccountMode = ACCOUNT_SORT_OPTIONS.includes(sortBy);

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
            const endpoint = isAccountMode ? '/api/v1/leaderboards/accounts' : '/api/v1/leaderboards';
            const res = await apiFetch(`${endpoint}?${params.toString()}`);
            if (res.ok) {
                const data = await res.json();
                setEntries(data.content || []);
            }
        } catch (error) {
            console.error('Failed to fetch leaderboard:', error);
        } finally {
            setLoading(false);
        }
    }, [isAccountMode, sortBy, direction]);

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
                    <h1 className="text-xl font-medium text-zinc-300">{isAccountMode ? 'Account Leaderboards' : 'Portfolio Leaderboards'}</h1>
                </div>
                <div className="flex gap-4">
                    <select
                        value={sortBy}
                        onChange={(e) => setSortBy(e.target.value as LeaderboardSortBy)}
                        className="text-sm bg-white/10 hover:bg-white/20 px-3 py-2 rounded border border-white/10"
                    >
                        <option value="RETURN_PERCENTAGE">Return %</option>
                        <option value="PROFIT_LOSS">P/L ($)</option>
                        <option value="WIN_RATE">Win Rate</option>
                        <option value="TRUST_SCORE">Trust</option>
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
                    {isAccountMode && (
                        <div className="px-4 py-3 border-b border-zinc-800 text-xs text-zinc-500">
                            Trust and win rate sort the account itself. Return and P/L sort individual public portfolios.
                        </div>
                    )}
                    {/* Table Header */}
                    <div className="grid grid-cols-12 gap-4 p-4 border-b border-zinc-800 text-xs font-semibold text-zinc-500 uppercase tracking-wider">
                        <div className="col-span-1 text-center">Rank</div>
                        <div className="col-span-11 md:col-span-3">{isAccountMode ? 'Trader' : 'Portfolio'}</div>
                        <div className="hidden md:block col-span-2 text-right">{isAccountMode ? 'Portfolios' : 'Owner'}</div>
                        {isAccountMode && <div className="hidden md:block col-span-2 text-right">Win Rate</div>}
                        {isAccountMode && <div className="hidden md:block col-span-1 text-right">Trust</div>}
                        <div className="hidden md:block col-span-2 text-right">P/L ($)</div>
                        <div className="hidden md:block col-span-1 text-right">Return %</div>
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
                                <div key={`${'portfolioId' in entry ? entry.portfolioId : entry.ownerId}-${index}`} className="grid grid-cols-12 gap-4 p-4 border-b border-zinc-800/50 hover:bg-white/5 transition-colors items-center">
                                    <div className="col-span-1 flex justify-center">
                                        <div className={`w-8 h-8 rounded-full flex items-center justify-center font-bold text-sm ${rankStyle}`}>
                                            {index + 1}
                                        </div>
                                    </div>

                                    <div className="col-span-11 md:col-span-3">
                                        {'portfolioId' in entry ? (
                                            <>
                                                <Link href={`/dashboard/portfolio/${entry.portfolioId}`} className="font-bold text-lg hover:text-green-400 transition-colors">
                                                    {entry.portfolioName}
                                                </Link>
                                                <p className="text-xs text-zinc-500 font-mono">ID: {entry.portfolioId.substring(0, 8)}...</p>
                                            </>
                                        ) : (
                                            <>
                                                <Link href={`/profile/${entry.ownerId}`} className="font-bold text-lg hover:text-green-400 transition-colors">
                                                    {entry.ownerName || `User ${entry.ownerId.substring(0, 8)}`}
                                                </Link>
                                                <p className="text-xs text-zinc-500 font-mono">ID: {entry.ownerId.substring(0, 8)}...</p>
                                            </>
                                        )}
                                    </div>

                                    {'portfolioId' in entry ? (
                                        <div className="col-span-6 md:col-span-2 text-right flex flex-col justify-center">
                                            <span className="block md:hidden text-xs text-zinc-500 mb-1">Owner</span>
                                            <Link href={`/profile/${entry.ownerId}`} className="font-mono text-sm text-zinc-300 hover:text-white transition-colors">
                                                {entry.ownerName || `User ${entry.ownerId.substring(0, 8)}`}
                                            </Link>
                                        </div>
                                    ) : (
                                        <>
                                            <div className="col-span-6 md:col-span-2 text-right flex flex-col justify-center">
                                                <span className="block md:hidden text-xs text-zinc-500 mb-1">Portfolios</span>
                                                <span className="font-mono text-lg font-bold text-zinc-200">{entry.publicPortfolioCount}</span>
                                            </div>
                                            <div className="col-span-6 md:col-span-2 text-right flex flex-col justify-center">
                                                <span className="block md:hidden text-xs text-zinc-500 mb-1">Win Rate</span>
                                                <span className={`font-mono text-lg font-bold ${entry.winRate >= 50 ? 'text-green-500' : 'text-red-500'}`}>
                                                    {entry.winRate.toFixed(1)}%
                                                </span>
                                            </div>
                                            <div className="col-span-6 md:col-span-1 text-right flex flex-col justify-center">
                                                <span className="block md:hidden text-xs text-zinc-500 mb-1">Trust</span>
                                                <span className={`font-mono text-lg font-bold ${entry.trustScore >= 70 ? 'text-green-500' : entry.trustScore >= 40 ? 'text-yellow-500' : 'text-red-500'}`}>
                                                    {entry.trustScore.toFixed(1)}
                                                </span>
                                            </div>
                                        </>
                                    )}

                                    <div className="col-span-6 md:col-span-2 text-right flex flex-col justify-center">
                                        <span className="block md:hidden text-xs text-zinc-500 mb-1">P/L</span>
                                        <span className={`font-mono text-lg font-bold ${plPositive ? 'text-green-500' : 'text-red-500'}`}>
                                            {plPositive ? '+' : ''}${pl.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                        </span>
                                    </div>

                                    <div className="col-span-6 md:col-span-1 text-right flex flex-col justify-center">
                                        <span className="block md:hidden text-xs text-zinc-500 mb-1">ROI</span>
                                        <span className={`font-mono text-lg font-bold ${isPositive ? 'text-green-500' : 'text-red-500'}`}>
                                            {isPositive ? '+' : ''}{roi.toFixed(2)}%
                                        </span>
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
