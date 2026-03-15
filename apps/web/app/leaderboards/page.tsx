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

function LeaderboardEmptyPanel({
    title,
    body,
}: {
    title: string;
    body: string;
}) {
    return (
        <div className="rounded-2xl border border-dashed border-zinc-800 bg-black/30 px-5 py-8 text-center">
            <p className="text-sm font-medium text-white">{title}</p>
            <p className="mt-2 text-sm text-zinc-500">{body}</p>
        </div>
    );
}

export default function LeaderboardPage() {
    const [entries, setEntries] = useState<(PortfolioLeaderboardEntry | AccountLeaderboardEntry)[]>([]);
    const [sortBy, setSortBy] = useState<LeaderboardSortBy>('RETURN_PERCENTAGE');
    const [direction, setDirection] = useState<LeaderboardDirection>('DESC');
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [totalElements, setTotalElements] = useState(0);
    const [preferencesReady, setPreferencesReady] = useState(false);
    const [loading, setLoading] = useState(true);
    const skipFirstPersistRef = useRef(true);
    const isAccountMode = ACCOUNT_SORT_OPTIONS.includes(sortBy);
    const positiveEntries = entries.filter((entry) => entry.returnPercentage >= 0).length;
    const averageEquity = entries.length > 0
        ? entries.reduce((sum, entry) => sum + entry.totalEquity, 0) / entries.length
        : 0;

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
                page: page.toString(),
                size: '20',
                sortBy,
                direction,
            });
            const endpoint = isAccountMode ? '/api/v1/leaderboards/accounts' : '/api/v1/leaderboards';
            const res = await apiFetch(`${endpoint}?${params.toString()}`);
            if (res.ok) {
                const data = await res.json();
                setEntries(data.content || []);
                setTotalPages(data?.page?.totalPages || 1);
                setTotalElements(data?.page?.totalElements || 0);
            }
        } catch (error) {
            console.error('Failed to fetch leaderboard:', error);
        } finally {
            setLoading(false);
        }
    }, [isAccountMode, page, sortBy, direction]);

    useEffect(() => {
        if (!preferencesReady) {
            return;
        }
        fetchLeaderboard();
        const interval = setInterval(fetchLeaderboard, 5000);
        return () => clearInterval(interval);
    }, [preferencesReady, fetchLeaderboard]);

    useEffect(() => {
        setPage(0);
    }, [sortBy, direction]);

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
                <section className="mb-6 grid gap-4 md:grid-cols-3">
                    <div className="rounded-2xl border border-zinc-800 bg-zinc-900/40 px-5 py-4">
                        <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Mode</p>
                        <p className={`mt-2 text-2xl font-bold ${isAccountMode ? 'text-blue-300' : 'text-green-300'}`}>
                            {isAccountMode ? 'Account' : 'Portfolio'}
                        </p>
                        <p className="mt-1 text-[11px] text-zinc-500">
                            {isAccountMode ? 'Trust and win rate rank traders.' : 'Return and P/L rank public portfolios.'}
                        </p>
                    </div>
                    <div className="rounded-2xl border border-zinc-800 bg-zinc-900/40 px-5 py-4">
                        <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Visible Results</p>
                        <p className="mt-2 text-2xl font-bold text-white">{totalElements}</p>
                        <p className="mt-1 text-[11px] text-zinc-500">{positiveEntries} positive rows on current page</p>
                    </div>
                    <div className="rounded-2xl border border-zinc-800 bg-zinc-900/40 px-5 py-4">
                        <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Avg Equity</p>
                        <p className="mt-2 text-2xl font-bold text-zinc-200">
                            ${averageEquity.toLocaleString(undefined, { maximumFractionDigits: 0 })}
                        </p>
                        <p className="mt-1 text-[11px] text-zinc-500">Across current page rows</p>
                    </div>
                </section>
                <div className="mb-4 flex items-center justify-between">
                    <div className="flex items-center gap-3 text-xs uppercase tracking-[0.2em]">
                        <span className={`rounded-full border px-3 py-1 ${isAccountMode ? 'border-blue-500/30 bg-blue-500/10 text-blue-300' : 'border-green-500/30 bg-green-500/10 text-green-300'}`}>
                            {isAccountMode ? 'Account Mode' : 'Portfolio Mode'}
                        </span>
                        <span className="text-zinc-600">
                            {totalElements} result{totalElements === 1 ? '' : 's'}
                        </span>
                    </div>
                </div>
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
                        <div className="p-6 space-y-3">
                            {Array.from({ length: 6 }).map((_, index) => (
                                <div key={index} className="grid grid-cols-12 gap-4 items-center rounded-xl border border-white/5 bg-black/20 p-4">
                                    <div className="col-span-1 flex justify-center">
                                        <div className="h-8 w-8 rounded-full animate-pulse bg-white/10" />
                                    </div>
                                    <div className="col-span-5">
                                        <div className="h-4 w-36 animate-pulse rounded bg-white/10" />
                                        <div className="mt-2 h-3 w-24 animate-pulse rounded bg-white/5" />
                                    </div>
                                    <div className="col-span-2 ml-auto h-4 w-16 animate-pulse rounded bg-white/5" />
                                    <div className="col-span-2 ml-auto h-4 w-20 animate-pulse rounded bg-white/5" />
                                    <div className="col-span-2 ml-auto h-4 w-24 animate-pulse rounded bg-white/10" />
                                </div>
                            ))}
                        </div>
                    ) : entries.length === 0 ? (
                        <div className="p-6">
                            <LeaderboardEmptyPanel
                                title={isAccountMode ? 'No public accounts yet' : 'No public portfolios yet'}
                                body={isAccountMode
                                    ? 'Accounts appear here after users expose at least one public portfolio.'
                                    : 'Public portfolios will appear here once users enable visibility and accumulate performance.'}
                            />
                        </div>
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
                <div className="mt-4 flex items-center justify-between rounded-2xl border border-zinc-800 bg-zinc-900/40 px-4 py-3">
                    <p className="text-xs uppercase tracking-[0.2em] text-zinc-500">
                        Page {Math.min(page + 1, Math.max(totalPages, 1))} / {Math.max(totalPages, 1)}
                    </p>
                    <div className="flex gap-2">
                        <button
                            onClick={() => setPage((prev) => Math.max(prev - 1, 0))}
                            disabled={page === 0}
                            className="rounded-lg border border-zinc-700 bg-black/40 px-4 py-2 text-xs font-bold uppercase tracking-wide text-zinc-300 transition-colors hover:bg-white/5 disabled:cursor-not-allowed disabled:opacity-40"
                        >
                            Previous
                        </button>
                        <button
                            onClick={() => setPage((prev) => Math.min(prev + 1, Math.max(totalPages - 1, 0)))}
                            disabled={page >= totalPages - 1}
                            className="rounded-lg border border-zinc-700 bg-black/40 px-4 py-2 text-xs font-bold uppercase tracking-wide text-zinc-300 transition-colors hover:bg-white/5 disabled:cursor-not-allowed disabled:opacity-40"
                        >
                            Next
                        </button>
                    </div>
                </div>
            </main>
        </div>
    );
}
