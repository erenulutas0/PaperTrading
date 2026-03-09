'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { useLiveNotifications } from '../../components/LiveNotificationProvider';
import { apiFetch } from '../../lib/api-client';

type FilterType = 'ALL' | 'FOLLOW' | 'PORTFOLIO' | 'POST' | 'PRICE_ALERT';

interface NotificationItem {
    id: string;
    type: string;
    actorUsername: string;
    referenceLabel: string;
    referenceId: string;
    read: boolean;
    createdAt: string;
}

function formatRelativeTime(createdAt: string): string {
    const ts = new Date(createdAt).getTime();
    if (!Number.isFinite(ts)) return '';
    const diffMs = Date.now() - ts;
    const minutes = Math.floor(diffMs / 60000);
    if (minutes < 1) return 'just now';
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    return `${days}d ago`;
}

export default function NotificationsPage() {
    const { notifications, unreadCount, markAllRead, markRead, connected } = useLiveNotifications();
    const [filter, setFilter] = useState<FilterType>('ALL');
    const [followBackLoadingId, setFollowBackLoadingId] = useState<string | null>(null);
    const [followedActorIds, setFollowedActorIds] = useState<Set<string>>(new Set());
    const currentUserId = typeof window !== 'undefined' ? localStorage.getItem('userId') : null;

    const filtered = useMemo(
        () =>
            filter === 'ALL'
                ? notifications
                : notifications.filter((n) => {
                    if (filter === 'PORTFOLIO') return n.type.startsWith('PORTFOLIO');
                    if (filter === 'POST') return n.type.startsWith('POST');
                    return n.type === filter;
                }),
        [filter, notifications]
    );

    const getMessage = (n: NotificationItem) => {
        switch (n.type) {
            case 'FOLLOW':
                return 'started following you';
            case 'PORTFOLIO_LIKE':
                return `liked your portfolio "${n.referenceLabel}"`;
            case 'POST_LIKE':
                return `liked your analysis "${n.referenceLabel}"`;
            case 'PORTFOLIO_COMMENT':
                return `commented on "${n.referenceLabel}"`;
            case 'POST_COMMENT':
                return `commented on "${n.referenceLabel}"`;
            case 'PORTFOLIO_JOINED':
                return `joined your portfolio "${n.referenceLabel}"`;
            case 'PRICE_ALERT':
                return n.referenceLabel;
            default:
                return 'sent a notification';
        }
    };

    const getIcon = (type: string) => {
        switch (type) {
            case 'FOLLOW':
                return '👤';
            case 'PORTFOLIO_LIKE':
                return '❤️';
            case 'POST_LIKE':
                return '💙';
            case 'PORTFOLIO_COMMENT':
            case 'POST_COMMENT':
                return '💬';
            case 'PORTFOLIO_JOINED':
                return '🤝';
            case 'PRICE_ALERT':
                return '🔔';
            default:
                return '📢';
        }
    };

    const getLink = (n: NotificationItem) => {
        if (n.type === 'FOLLOW' && n.referenceId) return `/profile/${n.referenceId}`;
        if (n.type === 'PRICE_ALERT') return '/watchlist';
        if (n.type.startsWith('PORTFOLIO')) return `/dashboard/portfolio/${n.referenceId}`;
        if (n.type.startsWith('POST')) return `/dashboard/analysis/${n.referenceId}`;
        return '/dashboard';
    };

    const getAccentColor = (type: string) => {
        switch (type) {
            case 'FOLLOW':
                return 'border-primary/35';
            case 'PORTFOLIO_LIKE':
                return 'border-secondary/35';
            case 'POST_LIKE':
                return 'border-warning/35';
            case 'PORTFOLIO_COMMENT':
            case 'POST_COMMENT':
                return 'border-chart-3/35';
            case 'PORTFOLIO_JOINED':
                return 'border-success/35';
            case 'PRICE_ALERT':
                return 'border-destructive/35';
            default:
                return 'border-border';
        }
    };

    const filters: { label: string; value: FilterType; icon: string }[] = [
        { label: 'All', value: 'ALL', icon: '📋' },
        { label: 'Follows', value: 'FOLLOW', icon: '👤' },
        { label: 'Portfolios', value: 'PORTFOLIO', icon: '📊' },
        { label: 'Posts', value: 'POST', icon: '📝' },
        { label: 'Price Alerts', value: 'PRICE_ALERT', icon: '🔔' },
    ];

    const canFollowBack = (notification: NotificationItem) =>
        notification.type === 'FOLLOW' &&
        Boolean(notification.referenceId) &&
        notification.referenceId !== currentUserId &&
        !followedActorIds.has(notification.referenceId);

    const handleFollowBack = async (notification: NotificationItem) => {
        if (!notification.referenceId || !canFollowBack(notification)) {
            return;
        }

        setFollowBackLoadingId(notification.id);
        try {
            const response = await apiFetch(`/api/v1/users/${notification.referenceId}/follow`, {
                method: 'POST',
            });

            if (response.ok) {
                setFollowedActorIds(prev => new Set(prev).add(notification.referenceId));
                return;
            }

            const message = await response.text();
            if (message.toLowerCase().includes('already following')) {
                setFollowedActorIds(prev => new Set(prev).add(notification.referenceId));
                return;
            }

            throw new Error(message || `Failed to follow user (${response.status})`);
        } catch (error) {
            console.error(error);
            alert(error instanceof Error ? error.message : 'Could not follow user');
        } finally {
            setFollowBackLoadingId(null);
        }
    };

    return (
        <div className="min-h-screen bg-background text-foreground">
            <div className="noise" />
            <div className="relative z-10 max-w-4xl mx-auto px-4 py-8 space-y-6">
                <header className="glass-panel rounded-2xl p-6 border border-border/80">
                    <div className="flex items-start justify-between gap-3">
                        <div>
                            <h1 className="text-3xl font-bold tracking-tight">Notifications</h1>
                            <p className="text-sm text-muted-foreground mt-1">
                                {unreadCount > 0
                                    ? `${unreadCount} unread notification${unreadCount > 1 ? 's' : ''}`
                                    : 'All caught up'}
                            </p>
                        </div>
                        <div className="flex items-center gap-3">
                            <span
                                className={`inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-mono uppercase tracking-wide ${connected
                                    ? 'border-success/40 bg-success/10 text-success'
                                    : 'border-border bg-accent text-muted-foreground'
                                    }`}
                            >
                                <span className={`h-2 w-2 rounded-full ${connected ? 'bg-success animate-pulse' : 'bg-muted-foreground'}`} />
                                {connected ? 'Live' : 'Offline'}
                            </span>
                            <button
                                onClick={markAllRead}
                                disabled={unreadCount === 0}
                                className="rounded-lg border border-primary/30 bg-primary/10 px-3 py-2 text-xs font-semibold uppercase tracking-wide text-primary transition hover:bg-primary/20 disabled:opacity-40 disabled:cursor-not-allowed"
                            >
                                Mark all read
                            </button>
                        </div>
                    </div>
                </header>

                <section className="glass-panel rounded-2xl p-4 border border-border/80">
                    <div className="flex flex-wrap gap-2">
                        {filters.map((item) => (
                            <button
                                key={item.value}
                                onClick={() => setFilter(item.value)}
                                className={`rounded-full border px-3 py-1.5 text-xs font-medium transition ${filter === item.value
                                    ? 'border-primary/40 bg-primary/15 text-primary'
                                    : 'border-border bg-background/50 text-muted-foreground hover:border-primary/25 hover:text-foreground'
                                    }`}
                            >
                                {item.icon} {item.label}
                            </button>
                        ))}
                    </div>
                </section>

                {filtered.length === 0 ? (
                    <div className="glass-panel rounded-2xl border border-dashed border-border/90 p-10 text-center">
                        <p className="text-lg font-semibold">No notifications</p>
                        <p className="mt-1 text-sm text-muted-foreground">
                            {filter === 'ALL' ? 'You will see updates here in real time.' : 'Try a different filter.'}
                        </p>
                    </div>
                ) : (
                    <section className="space-y-3">
                        {filtered.map((n) => (
                            <article
                                key={n.id}
                                className={`glass-panel rounded-xl border ${getAccentColor(n.type)} p-4 transition hover:border-primary/35`}
                            >
                                <div className="flex items-start gap-3">
                                    <div className="h-10 w-10 shrink-0 rounded-lg border border-border bg-accent flex items-center justify-center text-lg">
                                        {getIcon(n.type)}
                                    </div>
                                    <div className="min-w-0 flex-1">
                                        <Link
                                            href={getLink(n)}
                                            onClick={() => {
                                                if (!n.read) {
                                                    void markRead(n.id);
                                                }
                                            }}
                                            className="block hover:text-primary transition-colors"
                                        >
                                            <p className="text-sm leading-relaxed">
                                                <span className="font-semibold text-foreground">
                                                    {n.actorUsername || 'System'}
                                                </span>{' '}
                                                {getMessage(n)}
                                            </p>
                                        </Link>
                                        <div className="mt-2 flex flex-wrap items-center gap-2 text-[11px] text-muted-foreground">
                                            <span>{formatRelativeTime(n.createdAt)}</span>
                                            <span className="inline-flex rounded-full border border-border px-2 py-0.5 uppercase tracking-wide">
                                                {n.type.replace(/_/g, ' ')}
                                            </span>
                                        </div>
                                    </div>
                                    <div className="flex shrink-0 flex-col items-end gap-2">
                                        {canFollowBack(n) && (
                                            <button
                                                type="button"
                                                onClick={() => void handleFollowBack(n)}
                                                disabled={followBackLoadingId === n.id}
                                                className="rounded-md border border-secondary/35 bg-secondary/10 px-2 py-1 text-[11px] font-medium text-secondary hover:bg-secondary/20 disabled:cursor-not-allowed disabled:opacity-60"
                                            >
                                                {followBackLoadingId === n.id ? 'Following...' : 'Follow back'}
                                            </button>
                                        )}
                                        {!n.read ? (
                                            <button
                                                onClick={() => void markRead(n.id)}
                                                className="rounded-md border border-primary/35 bg-primary/10 px-2 py-1 text-[11px] font-medium text-primary hover:bg-primary/20"
                                            >
                                                Mark read
                                            </button>
                                        ) : (
                                            <span className="rounded-md border border-border px-2 py-1 text-[11px] text-muted-foreground">
                                                Read
                                            </span>
                                        )}
                                        <Link
                                            href={getLink(n)}
                                            onClick={() => {
                                                if (!n.read) {
                                                    void markRead(n.id);
                                                }
                                            }}
                                            className="text-[11px] text-primary hover:text-primary/80"
                                        >
                                            Open
                                        </Link>
                                    </div>
                                </div>
                            </article>
                        ))}
                    </section>
                )}
            </div>
        </div>
    );
}
