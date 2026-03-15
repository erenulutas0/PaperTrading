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
    trustScoreChange7d?: number;
    winRateChange7d?: number;
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
    trustHistory?: {
        capturedAt: string;
        trustScore: number;
        winRate: number;
    }[];
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

function formatDelta(value?: number, suffix = '') {
    if (value === undefined || Number.isNaN(value)) {
        return 'N/A';
    }
    const prefix = value > 0 ? '+' : '';
    return `${prefix}${value.toFixed(1)}${suffix}`;
}

function buildSeriesPath(values: number[], width: number, height: number) {
    if (values.length === 0) {
        return '';
    }
    const max = Math.max(...values, 1);
    const min = Math.min(...values, 0);
    const range = Math.max(max - min, 1);
    return values
        .map((value, index) => {
            const x = values.length === 1 ? width / 2 : (index / (values.length - 1)) * width;
            const y = height - ((value - min) / range) * height;
            return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`;
        })
        .join(' ');
}

function ProfileEmptyPanel({
    title,
    body,
}: {
    title: string;
    body: string;
}) {
    return (
        <div className="rounded-xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
            <p className="font-medium text-foreground">{title}</p>
            <p className="mt-2">{body}</p>
        </div>
    );
}

function ProfileLoadingShell() {
    return (
        <div className="min-h-screen bg-background text-foreground">
            <div className="noise" />
            <div className="relative z-10 max-w-6xl mx-auto px-4 py-8 space-y-6">
                <div className="flex items-center justify-between">
                    <div className="h-4 w-32 animate-pulse rounded bg-white/10" />
                    <div className="h-4 w-24 animate-pulse rounded bg-white/10" />
                </div>
                <div className="glass-panel rounded-2xl border border-border/80 p-6">
                    <div className="flex flex-col gap-6 md:flex-row md:items-start md:justify-between">
                        <div className="flex gap-4">
                            <div className="h-20 w-20 rounded-full animate-pulse bg-white/10" />
                            <div className="space-y-3">
                                <div className="h-8 w-56 animate-pulse rounded bg-white/10" />
                                <div className="h-4 w-32 animate-pulse rounded bg-white/5" />
                                <div className="h-4 w-72 max-w-full animate-pulse rounded bg-white/5" />
                            </div>
                        </div>
                        <div className="h-10 w-28 animate-pulse rounded-full bg-white/10" />
                    </div>
                </div>
                <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
                    {Array.from({ length: 4 }).map((_, index) => (
                        <div key={index} className="glass-panel rounded-xl border border-border/80 p-4">
                            <div className="h-3 w-24 animate-pulse rounded bg-white/10" />
                            <div className="mt-3 h-8 w-20 animate-pulse rounded bg-white/10" />
                            <div className="mt-2 h-3 w-24 animate-pulse rounded bg-white/5" />
                        </div>
                    ))}
                </div>
                <div className="glass-panel rounded-2xl border border-border/80 p-5">
                    <div className="h-4 w-40 animate-pulse rounded bg-white/10" />
                    <div className="mt-4 h-48 animate-pulse rounded-xl bg-white/5" />
                </div>
            </div>
        </div>
    );
}

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
        return <ProfileLoadingShell />;
    }

    if (!profile) {
        return (
            <div className="min-h-screen bg-background text-foreground">
                <div className="relative z-10 max-w-3xl mx-auto px-4 py-12">
                    <ProfileEmptyPanel
                        title="User not found"
                        body="This profile may have been removed, or the link is invalid."
                    />
                </div>
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
    const trustHistory = profile.trustHistory ?? [];
    const hasTrustEvidence = !!profile.trustBreakdown && (
        profile.trustBreakdown.resolvedPredictionCount > 0 ||
        profile.trustBreakdown.resolvedTradeCount > 0 ||
        profile.trustBreakdown.totalPortfolioCount > 0
    );
    const profileSummaryCards = [
        {
            label: 'Trust Score',
            value: profile.trustScore !== undefined ? profile.trustScore.toFixed(1) : 'N/A',
            detail: `7d ${formatDelta(profile.trustScoreChange7d)}`,
            tone: trustScoreColor,
        },
        {
            label: 'Platform Win Rate',
            value: profile.winRate !== undefined && hasTrustEvidence ? `${profile.winRate.toFixed(1)}%` : 'N/A',
            detail: `7d ${formatDelta(profile.winRateChange7d, '%')}`,
            tone: 'text-secondary',
        },
        {
            label: 'Public Signal Set',
            value: profile.trustBreakdown
                ? `${profile.trustBreakdown.resolvedPredictionCount + profile.trustBreakdown.resolvedTradeCount}`
                : '0',
            detail: 'Resolved predictions + closing trades',
            tone: 'text-foreground',
        },
        {
            label: 'Member Since',
            value: new Date(profile.memberSince).toLocaleDateString('en-US', { year: 'numeric', month: 'short' }),
            detail: `${profile.portfolioCount} portfolios`,
            tone: 'text-foreground',
        },
    ];
    const trustPath = buildSeriesPath(trustHistory.map((point) => point.trustScore), 100, 48);
    const winRatePath = buildSeriesPath(trustHistory.map((point) => point.winRate), 100, 48);

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
                                <Link href="/profile/edit" className="rounded-full border border-border bg-accent px-4 py-2 text-sm font-medium text-foreground hover:border-primary/30">
                                    Edit Profile
                                </Link>
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
                    {profileSummaryCards.map((card) => (
                        <article key={card.label} className="glass-panel rounded-xl border border-border/80 p-4">
                            <p className="text-xs uppercase tracking-wide text-muted-foreground">{card.label}</p>
                            <p className={`mt-2 text-2xl font-bold ${card.tone}`}>{card.value}</p>
                            <p className="mt-1 text-xs text-muted-foreground">{card.detail}</p>
                            {card.label === 'Trust Score' ? (
                                <Link href="/trust-score" className="mt-3 inline-block text-xs text-primary hover:text-primary/80 transition-colors">
                                    How it works
                                </Link>
                            ) : null}
                        </article>
                    ))}
                </section>

                {profile.trustBreakdown && (
                    <section className="space-y-3">
                        <article className="glass-panel rounded-xl border border-border/80 p-4">
                            <div className="flex flex-wrap items-start justify-between gap-4">
                                <div>
                                    <p className="text-xs uppercase tracking-wide text-muted-foreground">Trust Trend</p>
                                    <h2 className="mt-2 text-xl font-semibold">Last 30 profile evaluations</h2>
                                    <p className="mt-1 text-xs text-muted-foreground">
                                        Trust score snapshots are persisted by the backend job. Current profile reads also inject the latest point so the trend never starts empty.
                                    </p>
                                </div>
                                <div className="text-xs text-muted-foreground">
                                    {trustHistory.length} point{trustHistory.length === 1 ? '' : 's'}
                                </div>
                            </div>
                            <div className="mt-3 flex flex-wrap gap-2 text-[11px]">
                                <span className="rounded-full border border-primary/20 bg-primary/10 px-3 py-1 text-primary">
                                    Predictions {profile.trustBreakdown.resolvedPredictionCount}
                                </span>
                                <span className="rounded-full border border-secondary/20 bg-secondary/10 px-3 py-1 text-secondary">
                                    Trades {profile.trustBreakdown.resolvedTradeCount}
                                </span>
                                <span className="rounded-full border border-border bg-accent px-3 py-1 text-muted-foreground">
                                    Portfolios {profile.trustBreakdown.totalPortfolioCount}
                                </span>
                            </div>
                            <div className="mt-4 grid gap-4 lg:grid-cols-[1.4fr_1fr]">
                                <div className="rounded-2xl border border-border bg-background/60 p-4">
                                    <svg viewBox="0 0 100 48" className="h-36 w-full">
                                        <path d="M 0 24 L 100 24" stroke="rgba(255,255,255,0.08)" strokeWidth="1" />
                                        {trustPath && (
                                            <path
                                                d={trustPath}
                                                fill="none"
                                                stroke="rgb(34 197 94)"
                                                strokeWidth="2.5"
                                                strokeLinecap="round"
                                                strokeLinejoin="round"
                                            />
                                        )}
                                        {winRatePath && (
                                            <path
                                                d={winRatePath}
                                                fill="none"
                                                stroke="rgb(59 130 246)"
                                                strokeWidth="2"
                                                strokeLinecap="round"
                                                strokeLinejoin="round"
                                                strokeDasharray="4 3"
                                            />
                                        )}
                                    </svg>
                                    <div className="mt-3 flex flex-wrap gap-4 text-xs text-muted-foreground">
                                        <span className="flex items-center gap-2">
                                            <span className="h-2 w-2 rounded-full bg-green-500" />
                                            Trust score
                                        </span>
                                        <span className="flex items-center gap-2">
                                            <span className="h-2 w-2 rounded-full bg-blue-500" />
                                            Platform win rate
                                        </span>
                                    </div>
                                </div>
                                <div className="rounded-2xl border border-border bg-background/60 p-4">
                                    <p className="text-xs uppercase tracking-wide text-muted-foreground">Latest Evidence</p>
                                    <dl className="mt-4 space-y-3 text-sm">
                                        <div className="flex items-center justify-between">
                                            <dt className="text-muted-foreground">Resolved predictions</dt>
                                            <dd className="font-semibold">{profile.trustBreakdown.resolvedPredictionCount}</dd>
                                        </div>
                                        <div className="flex items-center justify-between">
                                            <dt className="text-muted-foreground">Resolved trades</dt>
                                            <dd className="font-semibold">{profile.trustBreakdown.resolvedTradeCount}</dd>
                                        </div>
                                        <div className="flex items-center justify-between">
                                            <dt className="text-muted-foreground">Portfolio win rate</dt>
                                            <dd className="font-semibold">{profile.trustBreakdown.portfolioWinRate.toFixed(1)}%</dd>
                                        </div>
                                        <div className="flex items-center justify-between">
                                            <dt className="text-muted-foreground">Average return</dt>
                                            <dd className="font-semibold">{profile.trustBreakdown.averagePortfolioReturn.toFixed(2)}%</dd>
                                        </div>
                                    </dl>
                                </div>
                            </div>
                        </article>

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
                                <ProfileEmptyPanel
                                    title={isOwnProfile ? 'No portfolios yet' : 'No public portfolios yet'}
                                    body={isOwnProfile
                                        ? 'Create one from the dashboard to start building a public performance record.'
                                        : 'This user has not exposed any public portfolios yet.'}
                                />
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
                                <div className="space-y-3">
                                    {Array.from({ length: 3 }).map((_, index) => (
                                        <div key={index} className="rounded-xl border border-border bg-background/60 p-4">
                                            <div className="flex items-center justify-between gap-4">
                                                <div className="flex items-center gap-3">
                                                    <div className="h-10 w-10 rounded-full animate-pulse bg-white/10" />
                                                    <div className="space-y-2">
                                                        <div className="h-3 w-24 animate-pulse rounded bg-white/10" />
                                                        <div className="h-3 w-16 animate-pulse rounded bg-white/5" />
                                                    </div>
                                                </div>
                                                <div className="h-8 w-20 animate-pulse rounded-full bg-white/10" />
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            ) : connectionUsers.length === 0 ? (
                                <ProfileEmptyPanel
                                    title={isFollowersTab ? 'No followers yet' : 'Not following anyone yet'}
                                    body={isFollowersTab
                                        ? 'This profile has not attracted followers yet.'
                                        : isOwnProfile
                                            ? 'Use discover, leaderboards, and profiles to build your network.'
                                            : 'This user is not following anyone yet.'}
                                />
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
