'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { extractContent } from '../../lib/page';
import { apiFetch, userIdHeaders } from '../../lib/api-client';

interface PublicPortfolio {
    id: string;
    name: string;
    description: string | null;
    balance: number;
    ownerId: string;
    createdAt: string;
    items?: {
        id: string;
        symbol: string;
        quantity: number;
        averagePrice: number;
        leverage: number;
        side: 'LONG' | 'SHORT';
    }[];
}

interface SuggestedAccount {
    id: string;
    username: string;
    displayName: string;
    avatarUrl?: string | null;
    verified: boolean;
    followerCount: number;
    portfolioCount: number;
    trustScore: number;
    following: boolean;
}

interface DiscoverPortfolioHighlight {
    id: string;
    name: string;
    description: string | null;
    ownerId: string;
    ownerName: string;
    returnPercentage1W: number;
    profitLoss1W: number;
    totalEquity: number;
    createdAt: string;
}

type DiscoverWorkspaceTab = 'OVERVIEW' | 'FEED';
type DiscoverSortPreset = 'LATEST' | 'OLDEST' | 'BALANCE_DESC' | 'BALANCE_ASC';

function resolveSortQuery(sortPreset: DiscoverSortPreset): string {
    switch (sortPreset) {
        case 'OLDEST':
            return 'createdAt,asc';
        case 'BALANCE_DESC':
            return 'balance,desc';
        case 'BALANCE_ASC':
            return 'balance,asc';
        case 'LATEST':
        default:
            return 'createdAt,desc';
    }
}

function readPageMeta(payload: unknown): { totalElements: number; totalPages: number; pageNumber: number } {
    if (!payload || typeof payload !== 'object') {
        return { totalElements: 0, totalPages: 0, pageNumber: 0 };
    }

    const page = (payload as { page?: { totalElements?: number; totalPages?: number; number?: number } }).page;
    return {
        totalElements: typeof page?.totalElements === 'number' ? page.totalElements : 0,
        totalPages: typeof page?.totalPages === 'number' ? page.totalPages : 0,
        pageNumber: typeof page?.number === 'number' ? page.number : 0,
    };
}

export default function DiscoverPage() {
    const [portfolios, setPortfolios] = useState<PublicPortfolio[]>([]);
    const [loading, setLoading] = useState(true);
    const [workspaceTab, setWorkspaceTab] = useState<DiscoverWorkspaceTab>('OVERVIEW');
    const [discoverSort, setDiscoverSort] = useState<DiscoverSortPreset>('LATEST');
    const [discoverQuery, setDiscoverQuery] = useState('');
    const [discoverSearchInput, setDiscoverSearchInput] = useState('');
    const [pageIndex, setPageIndex] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [currentUserId, setCurrentUserId] = useState<string | null>(null);
    const [suggestedAccounts, setSuggestedAccounts] = useState<SuggestedAccount[]>([]);
    const [suggestionsLoading, setSuggestionsLoading] = useState(true);
    const [followLoadingId, setFollowLoadingId] = useState<string | null>(null);
    const [highlightedPortfolios, setHighlightedPortfolios] = useState<DiscoverPortfolioHighlight[]>([]);
    const [highlightsLoading, setHighlightsLoading] = useState(true);

    useEffect(() => {
        if (typeof window === 'undefined') {
            return;
        }
        setCurrentUserId(window.localStorage.getItem('userId'));
    }, []);

    useEffect(() => {
        const timeoutId = window.setTimeout(() => {
            setPageIndex(0);
            setDiscoverQuery(discoverSearchInput.trim());
        }, 250);
        return () => window.clearTimeout(timeoutId);
    }, [discoverSearchInput]);

    useEffect(() => {
        const fetchDiscoverPortfolios = async () => {
            setLoading(true);
            try {
                const url = new URL('/api/v1/portfolios/discover', window.location.origin);
                url.searchParams.set('page', String(pageIndex));
                url.searchParams.set('size', '12');
                url.searchParams.set('sort', resolveSortQuery(discoverSort));
                if (discoverQuery) {
                    url.searchParams.set('q', discoverQuery);
                }
                const res = await apiFetch(`${url.pathname}${url.search}`);
                if (res.ok) {
                    const payload = await res.json();
                    setPortfolios(extractContent<PublicPortfolio>(payload));
                    const meta = readPageMeta(payload);
                    setTotalElements(meta.totalElements);
                    setTotalPages(meta.totalPages);
                }
            } catch (err) {
                console.error(err);
            } finally {
                setLoading(false);
            }
        };

        void fetchDiscoverPortfolios();
    }, [pageIndex, discoverSort, discoverQuery]);

    useEffect(() => {
        void fetchSuggestedAccounts(currentUserId);
    }, [currentUserId]);

    useEffect(() => {
        void fetchDiscoverHighlights();
    }, []);

    const fetchSuggestedAccounts = async (userId: string | null) => {
        setSuggestionsLoading(true);
        try {
            const response = await apiFetch('/api/v1/users/suggestions?limit=6', {
                headers: userId ? userIdHeaders(userId) : undefined,
                cache: 'no-store',
            });
            if (!response.ok) {
                setSuggestedAccounts([]);
                return;
            }
            setSuggestedAccounts(await response.json() as SuggestedAccount[]);
        } catch (error) {
            console.error(error);
            setSuggestedAccounts([]);
        } finally {
            setSuggestionsLoading(false);
        }
    };

    const fetchDiscoverHighlights = async () => {
        setHighlightsLoading(true);
        try {
            const response = await apiFetch('/api/v1/portfolios/discover/highlights?limit=4', {
                cache: 'no-store',
            });
            if (!response.ok) {
                setHighlightedPortfolios([]);
                return;
            }
            setHighlightedPortfolios(await response.json() as DiscoverPortfolioHighlight[]);
        } catch (error) {
            console.error(error);
            setHighlightedPortfolios([]);
        } finally {
            setHighlightsLoading(false);
        }
    };

    const handleFollowSuggestion = async (suggestion: SuggestedAccount) => {
        if (!currentUserId) {
            window.location.href = '/auth/login';
            return;
        }
        setFollowLoadingId(suggestion.id);
        try {
            const response = await apiFetch(`/api/v1/users/${suggestion.id}/follow`, {
                method: 'POST',
                headers: userIdHeaders(currentUserId),
            });
            if (!response.ok) {
                throw new Error(`Failed to follow user (${response.status})`);
            }
            setSuggestedAccounts((current) => current.filter((entry) => entry.id !== suggestion.id));
        } catch (error) {
            console.error(error);
        } finally {
            setFollowLoadingId(null);
        }
    };

    const publicPositionCount = portfolios.reduce((total, portfolio) => total + (portfolio.items?.length ?? 0), 0);
    const averageVisibleBalance = portfolios.length > 0
        ? portfolios.reduce((sum, portfolio) => sum + (portfolio.balance ?? 0), 0) / portfolios.length
        : 0;

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
                    <Link href="/discover" className="text-white">Discover</Link>
                    <Link href="/bots" className="hover:text-white transition-colors">Bots</Link>
                </div>
            </nav>

            <div className="max-w-5xl mx-auto px-6 py-10">
                <div className="mb-8">
                    <h1 className="text-3xl font-bold mb-2">
                        Discover <span className="text-green-400">Portfolios</span>
                    </h1>
                    <p className="text-zinc-400 text-sm">
                        Explore publicly shared portfolios. Every trade is timestamped and verifiable.
                    </p>
                </div>

                <section className="mb-8 rounded-2xl border border-white/10 bg-white/[0.02] p-6">
                    <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                        <div>
                            <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Discover Workspace</p>
                            <h2 className="mt-2 text-2xl font-black text-white">Separate public-market context from the live portfolio feed.</h2>
                            <p className="mt-3 max-w-3xl text-sm leading-7 text-zinc-400">
                                Use overview to understand the public discovery layer, then switch into the feed when you want to inspect and copy specific portfolios.
                            </p>
                        </div>
                        <div className="flex flex-wrap gap-2">
                            {([
                                { key: 'OVERVIEW', label: 'Overview', badge: `${totalElements || portfolios.length} public` },
                                { key: 'FEED', label: 'Feed', badge: `${publicPositionCount} positions` },
                            ] as const).map(({ key, label, badge }) => (
                                <button
                                    key={key}
                                    type="button"
                                    onClick={() => setWorkspaceTab(key)}
                                    className={`inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-semibold transition ${
                                        workspaceTab === key
                                            ? 'border-green-500/35 bg-green-500/15 text-green-300'
                                            : 'border-white/10 bg-white/5 text-zinc-400 hover:text-white'
                                    }`}
                                >
                                    <span>{label}</span>
                                    <span className={`rounded-full px-2 py-0.5 text-[10px] ${
                                        workspaceTab === key ? 'bg-green-500/15 text-green-200' : 'bg-black/30 text-zinc-500'
                                    }`}>
                                        {badge}
                                    </span>
                                </button>
                            ))}
                        </div>
                    </div>
                </section>

                {workspaceTab === 'OVERVIEW' && (
                    <div className="grid gap-4 md:grid-cols-[1.05fr_0.95fr]">
                        <section className="rounded-2xl border border-white/10 bg-white/[0.02] p-6">
                            <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Public Layer</p>
                            <h2 className="mt-3 text-2xl font-black text-white">Discover only shows portfolios that accepted public accountability.</h2>
                            <p className="mt-3 text-sm leading-7 text-zinc-400">
                                Every visible portfolio carries timestamped trades, balance history, and profile ownership. Discovery is not a hype feed; it is the public surface for verifiable paper performance.
                            </p>
                        </section>
                        <section className="rounded-2xl border border-white/10 bg-white/[0.02] p-6">
                            <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">What You Can Do</p>
                            <ul className="mt-4 space-y-3 text-sm leading-6 text-zinc-300">
                                <li>Inspect a public portfolio before opening its full history.</li>
                                <li>Copy a portfolio into your own account when you want a tracked clone.</li>
                                <li>Jump directly to the trader profile behind the published record.</li>
                            </ul>
                        </section>
                        <section className="rounded-2xl border border-white/10 bg-white/[0.02] p-6 md:col-span-2">
                            <div className="flex items-start justify-between gap-4">
                                <div>
                                    <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Suggested Accounts</p>
                                    <h2 className="mt-3 text-2xl font-black text-white">Follow traders with public accountability already on display.</h2>
                                    <p className="mt-3 max-w-3xl text-sm leading-7 text-zinc-400">
                                        This rail prioritizes verified public accounts with follower traction, trust score, and at least one visible portfolio.
                                    </p>
                                </div>
                                <Link href="/dashboard/leaderboard" className="rounded-full border border-white/10 bg-white/5 px-3 py-2 text-xs font-semibold text-zinc-300 hover:text-white">
                                    Open Leaderboard
                                </Link>
                            </div>
                            {suggestionsLoading ? (
                                <div className="mt-5 grid gap-3 md:grid-cols-3">
                                    {Array.from({ length: 3 }).map((_, index) => (
                                        <div key={index} className="h-32 animate-pulse rounded-xl bg-white/5" />
                                    ))}
                                </div>
                            ) : suggestedAccounts.length === 0 ? (
                                <div className="mt-5 rounded-xl border border-dashed border-white/10 bg-black/20 px-5 py-6 text-sm text-zinc-400">
                                    No account suggestions are ready yet. Public portfolios and leaderboard winners will populate this rail as the network grows.
                                </div>
                            ) : (
                                <div className="mt-5 grid gap-3 md:grid-cols-3">
                                    {suggestedAccounts.map((account) => (
                                        <div key={account.id} className="rounded-xl border border-white/5 bg-black/25 p-4">
                                            <div className="flex items-start justify-between gap-3">
                                                <div>
                                                    <Link href={`/profile/${account.id}`} className="font-semibold text-white hover:text-green-300">
                                                        {account.displayName}
                                                    </Link>
                                                    <p className="mt-1 text-xs text-zinc-500">@{account.username}</p>
                                                </div>
                                                {account.verified ? (
                                                    <span className="rounded-full border border-green-500/20 bg-green-500/10 px-2 py-1 text-[10px] font-semibold text-green-300">
                                                        Verified
                                                    </span>
                                                ) : null}
                                            </div>
                                            <div className="mt-4 flex flex-wrap gap-2 text-[11px] text-zinc-400">
                                                <span className="rounded-full border border-white/10 px-2 py-1">{account.portfolioCount} public</span>
                                                <span className="rounded-full border border-white/10 px-2 py-1">{account.followerCount} followers</span>
                                                <span className="rounded-full border border-white/10 px-2 py-1">Trust {account.trustScore.toFixed(1)}</span>
                                            </div>
                                            <div className="mt-4 flex gap-2">
                                                <Link href={`/profile/${account.id}`} className="rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-xs font-semibold text-zinc-200">
                                                    View Profile
                                                </Link>
                                                <button
                                                    type="button"
                                                    onClick={() => void handleFollowSuggestion(account)}
                                                    disabled={followLoadingId === account.id || account.following}
                                                    className="rounded-lg border border-green-500/20 bg-green-500/10 px-3 py-2 text-xs font-semibold text-green-300 disabled:cursor-not-allowed disabled:opacity-60"
                                                >
                                                    {followLoadingId === account.id ? 'Following...' : account.following ? 'Following' : 'Follow'}
                                                </button>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </section>
                        <section className="rounded-2xl border border-white/10 bg-white/[0.02] p-6 md:col-span-2">
                            <div className="flex items-start justify-between gap-4">
                                <div>
                                    <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Trending Public Portfolios</p>
                                    <h2 className="mt-3 text-2xl font-black text-white">Open the public records already rising on the one-week board.</h2>
                                    <p className="mt-3 max-w-3xl text-sm leading-7 text-zinc-400">
                                        This rail highlights recent public outperformers so discovery can start from stronger verified momentum instead of a blank portfolio list.
                                    </p>
                                </div>
                                <button
                                    type="button"
                                    onClick={() => setWorkspaceTab('FEED')}
                                    className="rounded-full border border-white/10 bg-white/5 px-3 py-2 text-xs font-semibold text-zinc-300 hover:text-white"
                                >
                                    Browse Feed
                                </button>
                            </div>
                            {highlightsLoading ? (
                                <div className="mt-5 grid gap-3 md:grid-cols-2">
                                    {Array.from({ length: 4 }).map((_, index) => (
                                        <div key={index} className="h-36 animate-pulse rounded-xl bg-white/5" />
                                    ))}
                                </div>
                            ) : highlightedPortfolios.length === 0 ? (
                                <div className="mt-5 rounded-xl border border-dashed border-white/10 bg-black/20 px-5 py-6 text-sm text-zinc-400">
                                    No trending public portfolios are available yet. Once public records accumulate, this rail will highlight the strongest recent movers.
                                </div>
                            ) : (
                                <div className="mt-5 grid gap-3 md:grid-cols-2">
                                    {highlightedPortfolios.map((portfolio) => (
                                        <Link
                                            key={portfolio.id}
                                            href={`/dashboard/portfolio/${portfolio.id}`}
                                            className="rounded-xl border border-white/5 bg-black/25 p-4 transition hover:border-green-500/20 hover:bg-black/35"
                                        >
                                            <div className="flex items-start justify-between gap-3">
                                                <div>
                                                    <h3 className="font-semibold text-white">{portfolio.name}</h3>
                                                    <p className="mt-1 text-xs text-zinc-500">by {portfolio.ownerName}</p>
                                                </div>
                                                <span className="rounded-full border border-green-500/20 bg-green-500/10 px-2 py-1 text-[10px] font-semibold text-green-300">
                                                    1W
                                                </span>
                                            </div>
                                            {portfolio.description ? (
                                                <p className="mt-3 line-clamp-2 text-sm leading-6 text-zinc-400">{portfolio.description}</p>
                                            ) : null}
                                            <div className="mt-4 flex flex-wrap gap-2 text-[11px] text-zinc-400">
                                                <span className="rounded-full border border-white/10 px-2 py-1">
                                                    {portfolio.returnPercentage1W.toFixed(1)}% 1W
                                                </span>
                                                <span className="rounded-full border border-white/10 px-2 py-1">
                                                    ${portfolio.totalEquity.toLocaleString('en-US', { maximumFractionDigits: 0 })} equity
                                                </span>
                                                <span className="rounded-full border border-white/10 px-2 py-1">
                                                    ${portfolio.profitLoss1W.toLocaleString('en-US', { maximumFractionDigits: 0 })} PnL
                                                </span>
                                            </div>
                                            <div className="mt-4 flex items-center justify-between text-xs text-zinc-500">
                                                <span>Published {new Date(portfolio.createdAt).toLocaleDateString()}</span>
                                                <span className="text-green-300">Open Portfolio →</span>
                                            </div>
                                        </Link>
                                    ))}
                                </div>
                            )}
                        </section>
                        <section className="rounded-2xl border border-white/10 bg-white/[0.02] p-6 md:col-span-2">
                            <div className="grid gap-4 md:grid-cols-3">
                                <div className="rounded-xl border border-white/5 bg-black/30 p-4">
                                    <p className="text-xs uppercase tracking-wide text-zinc-500">Public Portfolios</p>
                                    <p className="mt-2 text-2xl font-bold text-white">{loading ? '...' : totalElements || portfolios.length}</p>
                                    <p className="mt-1 text-xs text-zinc-500">Visible records available for inspection.</p>
                                </div>
                                <div className="rounded-xl border border-white/5 bg-black/30 p-4">
                                    <p className="text-xs uppercase tracking-wide text-zinc-500">Public Positions</p>
                                    <p className="mt-2 text-2xl font-bold text-green-300">{loading ? '...' : publicPositionCount}</p>
                                    <p className="mt-1 text-xs text-zinc-500">Open positions currently exposed in discovery.</p>
                                </div>
                                <div className="rounded-xl border border-white/5 bg-black/30 p-4">
                                    <p className="text-xs uppercase tracking-wide text-zinc-500">Next Step</p>
                                    <button
                                        type="button"
                                        onClick={() => setWorkspaceTab('FEED')}
                                        className="mt-2 rounded-lg border border-green-500/20 bg-green-500/10 px-3 py-2 text-xs font-bold uppercase tracking-[0.2em] text-green-300"
                                    >
                                        Open Feed
                                    </button>
                                    <p className="mt-2 text-xs text-zinc-500">Move into the live public portfolio list when you want actual candidates.</p>
                                </div>
                            </div>
                        </section>
                    </div>
                )}

                {workspaceTab === 'FEED' && (
                    loading ? (
                        <div className="flex justify-center py-20">
                            <div className="animate-spin w-8 h-8 border-2 border-green-500 border-t-transparent rounded-full"></div>
                        </div>
                    ) : (
                        <div className="space-y-6">
                            <section className="rounded-2xl border border-white/10 bg-white/[0.02] p-5">
                                <div className="flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between">
                                    <div>
                                        <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Feed Controls</p>
                                        <h3 className="mt-2 text-xl font-black text-white">Page through public portfolios with quick sort presets and backend-backed search.</h3>
                                    </div>
                                    <div className="flex flex-col gap-3 xl:items-end">
                                        <div className="flex flex-wrap gap-2">
                                            {([
                                                { key: 'LATEST', label: 'Latest' },
                                                { key: 'OLDEST', label: 'Oldest' },
                                                { key: 'BALANCE_DESC', label: 'Top Balance' },
                                                { key: 'BALANCE_ASC', label: 'Low Balance' },
                                            ] as const).map(({ key, label }) => (
                                                <button
                                                    key={key}
                                                    type="button"
                                                    onClick={() => {
                                                        setDiscoverSort(key);
                                                        setPageIndex(0);
                                                    }}
                                                    className={`rounded-full border px-3 py-1.5 text-xs font-semibold transition ${
                                                        discoverSort === key
                                                            ? 'border-green-500/35 bg-green-500/15 text-green-300'
                                                            : 'border-white/10 bg-white/5 text-zinc-400 hover:text-white'
                                                    }`}
                                                >
                                                    {label}
                                                </button>
                                            ))}
                                        </div>
                                        <input
                                            value={discoverSearchInput}
                                            onChange={(event) => setDiscoverSearchInput(event.target.value)}
                                            placeholder="Search public portfolios by name, description, or symbol"
                                            className="w-full rounded-xl border border-white/10 bg-black/30 px-4 py-2.5 text-sm text-white outline-none transition placeholder:text-zinc-600 focus:border-green-500/35 xl:w-96"
                                        />
                                    </div>
                                </div>
                                <div className="mt-4 grid gap-3 md:grid-cols-4">
                                    <div className="rounded-xl border border-white/5 bg-black/30 p-4">
                                        <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Page</p>
                                        <p className="mt-2 text-lg font-bold text-white">{totalPages === 0 ? 0 : pageIndex + 1} / {Math.max(totalPages, 1)}</p>
                                    </div>
                                    <div className="rounded-xl border border-white/5 bg-black/30 p-4">
                                        <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Visible Slice</p>
                                        <p className="mt-2 text-lg font-bold text-green-300">{portfolios.length}</p>
                                    </div>
                                    <div className="rounded-xl border border-white/5 bg-black/30 p-4">
                                        <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Slice Positions</p>
                                        <p className="mt-2 text-lg font-bold text-white">{publicPositionCount}</p>
                                    </div>
                                    <div className="rounded-xl border border-white/5 bg-black/30 p-4">
                                        <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Avg Balance</p>
                                        <p className="mt-2 text-lg font-bold text-white">
                                            ${averageVisibleBalance.toLocaleString('en-US', { maximumFractionDigits: 0 })}
                                        </p>
                                    </div>
                                </div>
                            </section>

                            {portfolios.length === 0 ? (
                                <div className="text-center py-20 border border-dashed border-white/10 rounded-xl">
                                    <p className="text-zinc-500 text-lg mb-2">No public portfolios yet</p>
                                    <p className="text-zinc-600 text-sm">Be the first to share your portfolio!</p>
                                </div>
                            ) : portfolios.length === 0 ? (
                                <div className="text-center py-16 border border-dashed border-white/10 rounded-xl">
                                    <p className="text-zinc-400 text-lg">No public portfolio matches this search.</p>
                                    <p className="mt-2 text-sm text-zinc-600">Clear the query or try a different sort/page combination.</p>
                                </div>
                            ) : (
                                <div className="grid gap-4 md:grid-cols-2">
                                    {portfolios.map(p => (
                                        <Link
                                            key={p.id}
                                            href={`/dashboard/portfolio/${p.id}`}
                                            className="border border-white/10 rounded-xl p-6 hover:border-green-500/30 hover:bg-white/[0.02] transition-all group"
                                        >
                                            <div className="flex items-center justify-between mb-3">
                                                <h3 className="font-semibold text-lg group-hover:text-green-400 transition-colors">
                                                    {p.name}
                                                </h3>
                                                <span className="text-xs bg-green-500/10 text-green-400 px-2 py-0.5 rounded-full border border-green-500/20">
                                                    Public
                                                </span>
                                            </div>
                                            {p.description && (
                                                <p className="text-sm text-zinc-400 mb-3 line-clamp-2">{p.description}</p>
                                            )}
                                            <div className="flex items-center justify-between">
                                                <div className="flex gap-4 text-xs text-zinc-500">
                                                    <span className="font-mono">${p.balance?.toLocaleString('en-US', { minimumFractionDigits: 2 })}</span>
                                                    <span>•</span>
                                                    <span>{p.items?.length || 0} positions</span>
                                                </div>
                                                <div className="flex gap-4">
                                                    <button
                                                        onClick={async (e) => {
                                                            e.preventDefault();
                                                            e.stopPropagation();
                                                            try {
                                                                const userId = localStorage.getItem('userId');
                                                                if (!userId) {
                                                                    alert('Please sign in first.');
                                                                    return;
                                                                }
                                                                const res = await apiFetch(`/api/v1/portfolios/${p.id}/join`, {
                                                                    method: 'POST',
                                                                    headers: {
                                                                        'Content-Type': 'application/json'
                                                                    }
                                                                });
                                                                if (res.ok) {
                                                                    const data = await res.json();
                                                                    alert(`Subscribed! Created copy portfolio ${data.clonedPortfolioId}`);
                                                                } else {
                                                                    const txt = await res.text();
                                                                    alert(`Failed to subscribe: ${txt}`);
                                                                }
                                                            } catch (e) {
                                                                console.error(e);
                                                            }
                                                        }}
                                                        className="text-xs bg-emerald-500/10 text-emerald-400 hover:bg-emerald-500/20 px-3 py-1 rounded-full font-bold transition-all border border-emerald-500/20 shadow-[0_0_10px_rgba(16,185,129,0.1)] hover:shadow-[0_0_15px_rgba(16,185,129,0.2)]"
                                                    >
                                                        COPY
                                                    </button>
                                                    <Link
                                                        href={`/profile/${p.ownerId}`}
                                                        onClick={(e) => e.stopPropagation()}
                                                        className="text-xs text-zinc-500 hover:text-green-400 transition-colors flex items-center"
                                                    >
                                                        View Trader →
                                                    </Link>
                                                </div>
                                            </div>
                                            <div className="mt-3 pt-3 border-t border-white/5 text-xs text-zinc-600">
                                                Created {new Date(p.createdAt).toLocaleDateString()}
                                            </div>
                                        </Link>
                                    ))}
                                </div>
                            )}

                            <div className="flex flex-col gap-3 rounded-2xl border border-white/10 bg-white/[0.02] p-5 md:flex-row md:items-center md:justify-between">
                                <div className="text-sm text-zinc-400">
                                    Showing page <span className="font-semibold text-white">{totalPages === 0 ? 0 : pageIndex + 1}</span>
                                    {' '}of <span className="font-semibold text-white">{Math.max(totalPages, 1)}</span>
                                    {' '}from <span className="font-semibold text-white">{totalElements}</span> public portfolios.
                                    {discoverQuery && (
                                        <>
                                            {' '}Filtered by <span className="font-semibold text-green-300">&quot;{discoverQuery}&quot;</span>.
                                        </>
                                    )}
                                </div>
                                <div className="flex gap-2">
                                    <button
                                        type="button"
                                        onClick={() => setPageIndex((current) => Math.max(0, current - 1))}
                                        disabled={pageIndex === 0}
                                        className="rounded-full border border-white/10 bg-white/5 px-4 py-2 text-xs font-semibold text-zinc-300 transition hover:text-white disabled:cursor-not-allowed disabled:opacity-40"
                                    >
                                        Prev
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => setPageIndex((current) => (current + 1 < totalPages ? current + 1 : current))}
                                        disabled={totalPages === 0 || pageIndex + 1 >= totalPages}
                                        className="rounded-full border border-white/10 bg-white/5 px-4 py-2 text-xs font-semibold text-zinc-300 transition hover:text-white disabled:cursor-not-allowed disabled:opacity-40"
                                    >
                                        Next
                                    </button>
                                </div>
                            </div>
                        </div>
                    )
                )}
            </div>
        </div>
    );
}
