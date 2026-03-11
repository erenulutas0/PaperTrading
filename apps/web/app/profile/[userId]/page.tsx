'use client';

import { useState, useEffect, useCallback } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import Image, { type ImageLoader } from 'next/image';
import { extractContent } from '../../../lib/page';
import { apiFetch, userIdHeaders } from '../../../lib/api-client';

interface UserProfile {
    id: string;
    username: string;
    displayName: string;
    bio: string | null;
    avatarUrl: string | null;
    verified: boolean;
    followerCount: number;
    followingCount: number;
    portfolioCount: number;
    following: boolean;
    trustScore?: number;
    winRate?: number;
    trustBreakdown?: {
        blendedWinRate: number;
        predictionWinRate: number;
        resolvedPredictionCount: number;
        tradeWinRate: number;
        resolvedTradeCount: number;
        profitablePortfolioCount: number;
        totalPortfolioCount: number;
        portfolioWinRate: number;
        averagePortfolioReturn: number;
        aggregateRealizedPnl: number;
        predictionComponent: number;
        tradeComponent: number;
        portfolioComponent: number;
        returnComponent: number;
        experienceComponent: number;
    };
    memberSince: string;
}

interface PublicPortfolio {
    id: string;
    name: string;
    description: string | null;
    balance: number;
    visibility: 'PUBLIC' | 'PRIVATE';
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

type ProfileTab = 'portfolios' | 'followers' | 'following';

interface PageMeta {
    number: number;
    totalPages: number;
}

const passthroughImageLoader: ImageLoader = ({ src }) => src;

export default function ProfilePage() {
    const params = useParams();
    const userId = params.userId as string;
    const [profile, setProfile] = useState<UserProfile | null>(null);
    const [portfolios, setPortfolios] = useState<PublicPortfolio[]>([]);
    const [loading, setLoading] = useState(true);
    const [activeTab, setActiveTab] = useState<ProfileTab>('portfolios');
    const [followLoading, setFollowLoading] = useState(false);
    const [followers, setFollowers] = useState<UserProfile[]>([]);
    const [followingUsers, setFollowingUsers] = useState<UserProfile[]>([]);
    const [followersPage, setFollowersPage] = useState<PageMeta>({ number: 0, totalPages: 1 });
    const [followingPage, setFollowingPage] = useState<PageMeta>({ number: 0, totalPages: 1 });
    const [followersLoaded, setFollowersLoaded] = useState(false);
    const [followingLoaded, setFollowingLoaded] = useState(false);
    const [followersLoading, setFollowersLoading] = useState(false);
    const [followingLoading, setFollowingLoading] = useState(false);
    const [connectionActionUserId, setConnectionActionUserId] = useState<string | null>(null);
    const currentUserId = typeof window !== 'undefined' ? localStorage.getItem('userId') : null;

    const fetchProfile = useCallback(async () => {
        try {
            const res = await apiFetch(`/api/v1/users/${userId}/profile`, {
                headers: userIdHeaders(currentUserId)
            });
            if (res.ok) {
                setProfile(await res.json());
            }
        } catch (err) {
            console.error(err);
        } finally {
            setLoading(false);
        }
    }, [currentUserId, userId]);

    const fetchPortfolios = useCallback(async () => {
        try {
            const res = await apiFetch(`/api/v1/portfolios?ownerId=${userId}`);
            if (res.ok) {
                const data = await res.json();
                const all = extractContent<PublicPortfolio>(data);
                // Show only public portfolios for other users, or all if viewing own profile
                if (currentUserId === userId) {
                    setPortfolios(all);
                } else {
                    setPortfolios(all.filter(p => p.visibility === 'PUBLIC'));
                }
            }
        } catch (err) {
            console.error(err);
        }
    }, [currentUserId, userId]);

    const fetchConnections = useCallback(async (
        tab: 'followers' | 'following',
        page: number = 0,
        append: boolean = false
    ) => {
        const setLoadingState = tab === 'followers' ? setFollowersLoading : setFollowingLoading;
        setLoadingState(true);

        try {
            const res = await apiFetch(`/api/v1/users/${userId}/${tab}?page=${page}&size=20`, {
                headers: userIdHeaders(currentUserId)
            });
            if (!res.ok) return;

            const data = await res.json();
            const users = extractContent<UserProfile>(data);
            const nextPage: PageMeta = {
                number: typeof data?.page?.number === 'number' ? data.page.number : page,
                totalPages: typeof data?.page?.totalPages === 'number' ? data.page.totalPages : 1,
            };

            if (tab === 'followers') {
                setFollowers((prev) => (append ? [...prev, ...users] : users));
                setFollowersPage(nextPage);
                setFollowersLoaded(true);
            } else {
                setFollowingUsers((prev) => (append ? [...prev, ...users] : users));
                setFollowingPage(nextPage);
                setFollowingLoaded(true);
            }
        } catch (err) {
            console.error(err);
        } finally {
            setLoadingState(false);
        }
    }, [currentUserId, userId]);

    useEffect(() => {
        setFollowers([]);
        setFollowingUsers([]);
        setFollowersPage({ number: 0, totalPages: 1 });
        setFollowingPage({ number: 0, totalPages: 1 });
        setFollowersLoaded(false);
        setFollowingLoaded(false);
        fetchProfile();
        fetchPortfolios();
    }, [fetchPortfolios, fetchProfile]);

    useEffect(() => {
        if (activeTab === 'followers' && !followersLoaded && !followersLoading) {
            fetchConnections('followers');
            return;
        }
        if (activeTab === 'following' && !followingLoaded && !followingLoading) {
            fetchConnections('following');
        }
    }, [activeTab, fetchConnections, followersLoaded, followersLoading, followingLoaded, followingLoading]);

    const handleFollow = async () => {
        if (!currentUserId || !profile) return;
        setFollowLoading(true);
        try {
            const method = profile.following ? 'DELETE' : 'POST';
            await apiFetch(`/api/v1/users/${userId}/follow`, {
                method,
                headers: userIdHeaders(currentUserId),
            });
            fetchProfile();
        } catch (err) {
            console.error(err);
        } finally {
            setFollowLoading(false);
        }
    };

    const handleConnectionFollowToggle = async (target: UserProfile) => {
        if (!currentUserId || currentUserId === target.id) return;
        setConnectionActionUserId(target.id);
        try {
            const method = target.following ? 'DELETE' : 'POST';
            const res = await apiFetch(`/api/v1/users/${target.id}/follow`, {
                method,
                headers: userIdHeaders(currentUserId),
            });
            if (!res.ok) return;

            await fetchProfile();
            if (activeTab === 'followers') {
                await fetchConnections('followers', 0, false);
            } else if (activeTab === 'following') {
                await fetchConnections('following', 0, false);
            }
        } catch (err) {
            console.error(err);
        } finally {
            setConnectionActionUserId(null);
        }
    };

    if (loading) {
        return (
            <div className="min-h-screen bg-black text-white flex items-center justify-center">
                <div className="animate-spin w-8 h-8 border-2 border-green-500 border-t-transparent rounded-full"></div>
            </div>
        );
    }

    if (!profile) {
        return (
            <div className="min-h-screen bg-black text-white flex items-center justify-center">
                <p className="text-zinc-400">User not found</p>
            </div>
        );
    }

    const isOwnProfile = currentUserId === userId;
    const trustScoreColor =
        profile.trustScore !== undefined && profile.trustScore >= 70
            ? 'text-success'
            : profile.trustScore !== undefined && profile.trustScore >= 40
                ? 'text-warning'
                : 'text-destructive';
    const isFollowersTab = activeTab === 'followers';
    const isFollowingTab = activeTab === 'following';
    const connectionUsers = isFollowersTab ? followers : followingUsers;
    const connectionLoading = isFollowersTab ? followersLoading : isFollowingTab ? followingLoading : false;
    const connectionPage = isFollowersTab ? followersPage : followingPage;
    const hasMoreConnections = connectionPage.number + 1 < connectionPage.totalPages;

    return (
        <div className="min-h-screen bg-background text-foreground">
            <div className="noise" />
            <div className="relative z-10 max-w-6xl mx-auto px-4 py-8 space-y-6">
                <header className="flex items-center justify-between">
                    <Link href="/dashboard" className="text-sm text-muted-foreground hover:text-foreground transition-colors">
                        ← Back to Dashboard
                    </Link>
                    <Link href="/dashboard/leaderboard" className="text-sm text-primary hover:text-primary/80 transition-colors">
                        Leaderboard
                    </Link>
                </header>

                <section className="glass-panel rounded-2xl border border-border/80 p-6">
                    <div className="flex flex-col gap-6 md:flex-row md:items-start md:justify-between">
                        <div className="flex gap-4">
                            <div className="h-20 w-20 shrink-0 rounded-full bg-gradient-to-br from-primary to-secondary flex items-center justify-center text-2xl font-bold text-primary-foreground">
                                {profile.avatarUrl ? (
                                    <Image
                                        src={profile.avatarUrl}
                                        alt={`${profile.username} avatar`}
                                        width={80}
                                        height={80}
                                        loader={passthroughImageLoader}
                                        unoptimized
                                        className="h-full w-full rounded-full object-cover"
                                    />
                                ) : (
                                    profile.displayName?.charAt(0)?.toUpperCase() || profile.username.charAt(0).toUpperCase()
                                )}
                            </div>
                            <div className="space-y-2">
                                <div className="flex flex-wrap items-center gap-2">
                                    <h1 className="text-2xl font-bold">{profile.displayName || profile.username}</h1>
                                    {profile.verified && (
                                        <span className="rounded-full border border-primary/30 bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
                                            Verified
                                        </span>
                                    )}
                                </div>
                                <p className="text-sm text-muted-foreground">@{profile.username}</p>
                                {profile.bio && <p className="max-w-2xl text-sm text-foreground/85">{profile.bio}</p>}
                                <div className="flex flex-wrap gap-4 text-sm">
                                    <button
                                        onClick={() => setActiveTab('portfolios')}
                                        className={`transition-colors ${activeTab === 'portfolios' ? 'text-foreground' : 'text-muted-foreground hover:text-foreground'}`}
                                    >
                                        <span className="font-semibold">{profile.portfolioCount}</span> Portfolios
                                    </button>
                                    <button
                                        onClick={() => setActiveTab('followers')}
                                        className={`transition-colors ${activeTab === 'followers' ? 'text-foreground' : 'text-muted-foreground hover:text-foreground'}`}
                                    >
                                        <span className="font-semibold">{profile.followerCount}</span> Followers
                                    </button>
                                    <button
                                        onClick={() => setActiveTab('following')}
                                        className={`transition-colors ${activeTab === 'following' ? 'text-foreground' : 'text-muted-foreground hover:text-foreground'}`}
                                    >
                                        <span className="font-semibold">{profile.followingCount}</span> Following
                                    </button>
                                </div>
                            </div>
                        </div>

                        <div className="shrink-0">
                            {isOwnProfile ? (
                                <button className="rounded-full border border-border bg-accent px-4 py-2 text-sm font-medium text-foreground hover:border-primary/30">
                                    Edit Profile
                                </button>
                            ) : currentUserId ? (
                                <button
                                    onClick={handleFollow}
                                    disabled={followLoading}
                                    className={`rounded-full px-4 py-2 text-sm font-semibold transition disabled:opacity-60 ${profile.following
                                        ? 'border border-border bg-accent text-foreground hover:border-destructive/40 hover:text-destructive'
                                        : 'border border-primary/30 bg-primary/15 text-primary hover:bg-primary/25'
                                        }`}
                                >
                                    {followLoading ? 'Updating...' : profile.following ? 'Following' : 'Follow'}
                                </button>
                            ) : null}
                        </div>
                    </div>
                </section>

                <section className="grid grid-cols-2 gap-3 md:grid-cols-4">
                    <article className="glass-panel rounded-xl border border-border/80 p-4">
                        <p className="text-xs uppercase tracking-wide text-muted-foreground">Trust Score</p>
                        <p className={`mt-2 text-2xl font-bold ${trustScoreColor}`}>
                            {profile.trustScore !== undefined ? profile.trustScore.toFixed(1) : 'N/A'}
                        </p>
                        <Link href="/trust-score" className="mt-3 inline-block text-xs text-primary hover:text-primary/80 transition-colors">
                            How it works
                        </Link>
                    </article>
                    <article className="glass-panel rounded-xl border border-border/80 p-4">
                        <p className="text-xs uppercase tracking-wide text-muted-foreground">Platform Win Rate</p>
                        <p className="mt-2 text-2xl font-bold text-secondary">
                            {profile.winRate !== undefined && profile.winRate > 0 ? `${profile.winRate.toFixed(1)}%` : 'N/A'}
                        </p>
                        {profile.trustBreakdown && (
                            <p className="mt-1 text-xs text-muted-foreground">
                                Blend of prediction, trade, and portfolio signals
                            </p>
                        )}
                    </article>
                    <article className="glass-panel rounded-xl border border-border/80 p-4">
                        <p className="text-xs uppercase tracking-wide text-muted-foreground">Portfolios</p>
                        <p className="mt-2 text-2xl font-bold">{profile.portfolioCount}</p>
                    </article>
                    <article className="glass-panel rounded-xl border border-border/80 p-4">
                        <p className="text-xs uppercase tracking-wide text-muted-foreground">Member Since</p>
                        <p className="mt-2 text-sm font-medium">
                            {new Date(profile.memberSince).toLocaleDateString('en-US', { year: 'numeric', month: 'short' })}
                        </p>
                    </article>
                </section>

                {profile.trustBreakdown && (
                    <section className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-5">
                        <article className="glass-panel rounded-xl border border-border/80 p-4">
                            <p className="text-xs uppercase tracking-wide text-muted-foreground">Resolved Calls</p>
                            <p className="mt-2 text-2xl font-bold">{profile.trustBreakdown.resolvedPredictionCount}</p>
                            <p className="mt-1 text-xs text-muted-foreground">Target/stop/expiry resolved analysis posts</p>
                        </article>
                        <article className="glass-panel rounded-xl border border-border/80 p-4">
                            <p className="text-xs uppercase tracking-wide text-muted-foreground">Trade Win Rate</p>
                            <p className="mt-2 text-2xl font-bold text-secondary">
                                {profile.trustBreakdown.resolvedTradeCount > 0
                                    ? `${profile.trustBreakdown.tradeWinRate.toFixed(1)}%`
                                    : 'N/A'}
                            </p>
                            <p className="mt-1 text-xs text-muted-foreground">{profile.trustBreakdown.resolvedTradeCount} resolved closing trades</p>
                        </article>
                        <article className="glass-panel rounded-xl border border-border/80 p-4">
                            <p className="text-xs uppercase tracking-wide text-muted-foreground">Profitable Portfolios</p>
                            <p className="mt-2 text-2xl font-bold">
                                {profile.trustBreakdown.profitablePortfolioCount}/{profile.trustBreakdown.totalPortfolioCount}
                            </p>
                            <p className="mt-1 text-xs text-muted-foreground">
                                {profile.trustBreakdown.totalPortfolioCount > 0
                                    ? `${profile.trustBreakdown.portfolioWinRate.toFixed(1)}% currently positive`
                                    : 'No portfolios yet'}
                            </p>
                        </article>
                        <article className="glass-panel rounded-xl border border-border/80 p-4">
                            <p className="text-xs uppercase tracking-wide text-muted-foreground">Avg Portfolio Return</p>
                            <p className={`mt-2 text-2xl font-bold ${profile.trustBreakdown.averagePortfolioReturn >= 0 ? 'text-success' : 'text-destructive'}`}>
                                {profile.trustBreakdown.averagePortfolioReturn >= 0 ? '+' : ''}
                                {profile.trustBreakdown.averagePortfolioReturn.toFixed(2)}%
                            </p>
                            <p className="mt-1 text-xs text-muted-foreground">Current average all-time return across portfolios</p>
                        </article>
                        <article className="glass-panel rounded-xl border border-border/80 p-4">
                            <p className="text-xs uppercase tracking-wide text-muted-foreground">Realized P/L</p>
                            <p className={`mt-2 text-2xl font-bold ${profile.trustBreakdown.aggregateRealizedPnl >= 0 ? 'text-success' : 'text-destructive'}`}>
                                {profile.trustBreakdown.aggregateRealizedPnl >= 0 ? '+' : ''}
                                ${Math.abs(profile.trustBreakdown.aggregateRealizedPnl).toLocaleString('en-US', { maximumFractionDigits: 2 })}
                            </p>
                            <p className="mt-1 text-xs text-muted-foreground">Closed-trade realized profit/loss</p>
                        </article>
                    </section>
                )}

                <section className="glass-panel rounded-2xl border border-border/80 p-5">
                    <div className="mb-5 flex flex-wrap gap-2">
                        <button
                            onClick={() => setActiveTab('portfolios')}
                            className={`rounded-full px-3 py-1.5 text-xs font-medium transition ${activeTab === 'portfolios'
                                ? 'bg-primary/15 text-primary border border-primary/35'
                                : 'bg-accent text-muted-foreground border border-border hover:text-foreground'
                                }`}
                        >
                            Portfolios
                        </button>
                        <button
                            onClick={() => setActiveTab('followers')}
                            className={`rounded-full px-3 py-1.5 text-xs font-medium transition ${activeTab === 'followers'
                                ? 'bg-primary/15 text-primary border border-primary/35'
                                : 'bg-accent text-muted-foreground border border-border hover:text-foreground'
                                }`}
                        >
                            Followers
                        </button>
                        <button
                            onClick={() => setActiveTab('following')}
                            className={`rounded-full px-3 py-1.5 text-xs font-medium transition ${activeTab === 'following'
                                ? 'bg-primary/15 text-primary border border-primary/35'
                                : 'bg-accent text-muted-foreground border border-border hover:text-foreground'
                                }`}
                        >
                            Following
                        </button>
                    </div>

                    {activeTab === 'portfolios' && (
                        <div className="space-y-3">
                            {portfolios.length === 0 ? (
                                <div className="rounded-xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
                                    {isOwnProfile ? 'No portfolios yet.' : 'No public portfolios yet.'}
                                </div>
                            ) : (
                                portfolios.map((portfolio) => (
                                    <Link
                                        key={portfolio.id}
                                        href={`/dashboard/portfolio/${portfolio.id}`}
                                        className="block rounded-xl border border-border bg-background/60 p-4 transition hover:border-primary/35"
                                    >
                                        <div className="flex flex-wrap items-start justify-between gap-3">
                                            <div>
                                                <div className="flex items-center gap-2">
                                                    <h3 className="text-lg font-semibold">{portfolio.name}</h3>
                                                    <span
                                                        className={`rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${portfolio.visibility === 'PUBLIC'
                                                            ? 'border border-primary/30 bg-primary/10 text-primary'
                                                            : 'border border-border bg-accent text-muted-foreground'
                                                            }`}
                                                    >
                                                        {portfolio.visibility}
                                                    </span>
                                                </div>
                                                {portfolio.description && (
                                                    <p className="mt-1 text-sm text-muted-foreground">{portfolio.description}</p>
                                                )}
                                            </div>
                                            <div className="text-right">
                                                <p className="text-xl font-bold text-success">
                                                    ${portfolio.balance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                                </p>
                                                <p className="text-xs text-muted-foreground">
                                                    {portfolio.items?.length || 0} open position{(portfolio.items?.length || 0) === 1 ? '' : 's'}
                                                </p>
                                            </div>
                                        </div>
                                    </Link>
                                ))
                            )}
                        </div>
                    )}

                    {(isFollowersTab || isFollowingTab) && (
                        <div className="space-y-3">
                            {connectionLoading && connectionUsers.length === 0 ? (
                                <div className="rounded-xl border border-border p-8 text-center text-sm text-muted-foreground">
                                    Loading {isFollowersTab ? 'followers' : 'following'}...
                                </div>
                            ) : connectionUsers.length === 0 ? (
                                <div className="rounded-xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
                                    {isFollowersTab ? 'No followers yet.' : 'Not following anyone yet.'}
                                </div>
                            ) : (
                                connectionUsers.map((user) => {
                                    const showFollowAction = !!currentUserId && currentUserId !== user.id;
                                    const isActionLoading = connectionActionUserId === user.id;

                                    return (
                                        <article
                                            key={user.id}
                                            className="rounded-xl border border-border bg-background/60 p-4 transition hover:border-primary/35"
                                        >
                                            <div className="flex items-center justify-between gap-4">
                                                <Link href={`/profile/${user.id}`} className="flex min-w-0 items-center gap-3">
                                                    <div className="h-10 w-10 shrink-0 rounded-full border border-border bg-accent flex items-center justify-center font-semibold">
                                                        {(user.displayName || user.username).charAt(0).toUpperCase()}
                                                    </div>
                                                    <div className="min-w-0">
                                                        <p className="truncate text-sm font-semibold text-foreground">
                                                            {user.displayName || user.username}
                                                        </p>
                                                        <p className="truncate text-xs text-muted-foreground">@{user.username}</p>
                                                    </div>
                                                </Link>

                                                <div className="flex items-center gap-3">
                                                    <div className="text-right text-xs text-muted-foreground">
                                                        <p>{user.followerCount} followers</p>
                                                        <p>{user.followingCount} following</p>
                                                    </div>
                                                    {showFollowAction && (
                                                        <button
                                                            onClick={() => handleConnectionFollowToggle(user)}
                                                            disabled={isActionLoading}
                                                            className={`rounded-full px-3 py-1.5 text-xs font-semibold transition disabled:opacity-60 ${user.following
                                                                ? 'border border-border bg-accent text-foreground hover:border-destructive/40 hover:text-destructive'
                                                                : 'border border-primary/30 bg-primary/15 text-primary hover:bg-primary/25'
                                                                }`}
                                                        >
                                                            {isActionLoading ? 'Updating...' : user.following ? 'Following' : 'Follow'}
                                                        </button>
                                                    )}
                                                </div>
                                            </div>
                                        </article>
                                    );
                                })
                            )}

                            {hasMoreConnections && (
                                <div className="pt-2">
                                    <button
                                        onClick={() => {
                                            const nextPage = connectionPage.number + 1;
                                            fetchConnections(isFollowersTab ? 'followers' : 'following', nextPage, true);
                                        }}
                                        disabled={connectionLoading}
                                        className="rounded-lg border border-primary/30 bg-primary/10 px-4 py-2 text-xs font-semibold text-primary transition hover:bg-primary/20 disabled:opacity-60"
                                    >
                                        {connectionLoading ? 'Loading...' : 'Load More'}
                                    </button>
                                </div>
                            )}
                        </div>
                    )}
                </section>
            </div>
        </div>
    );
}
