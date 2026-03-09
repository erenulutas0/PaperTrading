'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { useLiveNotifications } from './LiveNotificationProvider';
import { apiFetch } from '../lib/api-client';

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

export default function NotificationBell() {
    const { notifications, unreadCount, markAllRead, markRead, connected } = useLiveNotifications();
    const [isOpen, setIsOpen] = useState(false);
    const [followBackLoadingId, setFollowBackLoadingId] = useState<string | null>(null);
    const [followedActorIds, setFollowedActorIds] = useState<Set<string>>(new Set());
    const currentUserId = typeof window !== 'undefined' ? localStorage.getItem('userId') : null;

    const visibleNotifications = useMemo(() => notifications.slice(0, 30), [notifications]);

    const toggleOpen = () => {
        setIsOpen(!isOpen);
    };

    const getMessage = (n: NotificationItem) => {
        switch (n.type) {
            case 'FOLLOW': return `started following you`;
            case 'PORTFOLIO_LIKE': return `liked your portfolio "${n.referenceLabel}"`;
            case 'POST_LIKE': return `liked your analysis "${n.referenceLabel}"`;
            case 'PORTFOLIO_COMMENT': return `commented on "${n.referenceLabel}"`;
            case 'POST_COMMENT': return `commented on "${n.referenceLabel}"`;
            case 'PORTFOLIO_JOINED': return `joined your portfolio "${n.referenceLabel}"`;
            case 'PRICE_ALERT': return `${n.referenceLabel}`;
            default: return `sent a notification`;
        }
    };

    const getLink = (n: NotificationItem) => {
        if (n.type === 'FOLLOW' && n.referenceId) return `/profile/${n.referenceId}`;
        if (n.type === 'PRICE_ALERT') return '/watchlist';
        if (n.type?.startsWith('PORTFOLIO')) return `/dashboard/portfolio/${n.referenceId}`;
        if (n.type?.startsWith('POST')) return `/dashboard/analysis/${n.referenceId}`;
        return '/dashboard';
    };

    const getIcon = (type: string) => {
        switch (type) {
            case 'FOLLOW': return '👤';
            case 'PORTFOLIO_LIKE': return '❤️';
            case 'POST_LIKE': return '💙';
            case 'PORTFOLIO_COMMENT': return '💬';
            case 'POST_COMMENT': return '💬';
            case 'PORTFOLIO_JOINED': return '🤝';
            case 'PRICE_ALERT': return '🔔';
            default: return '📢';
        }
    };

    const canFollowBack = (n: NotificationItem) =>
        n.type === 'FOLLOW' &&
        Boolean(n.referenceId) &&
        n.referenceId !== currentUserId &&
        !followedActorIds.has(n.referenceId);

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
        <div className="relative z-[120]">
            <button
                onClick={toggleOpen}
                className="relative p-2 text-zinc-400 hover:text-white transition-colors focus:outline-none group"
            >
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
                </svg>
                {unreadCount > 0 && (
                    <span className="absolute top-0 right-0 inline-flex items-center justify-center px-2 py-1 text-[9px] font-bold leading-none text-black transform translate-x-1/4 -translate-y-1/4 bg-red-500 rounded-full animate-pulse shadow-[0_0_10px_rgba(239,68,68,0.5)]">
                        {unreadCount}
                    </span>
                )}
                {/* Connection indicator */}
                <span className={`absolute bottom-0 right-0 w-2 h-2 rounded-full ${connected ? 'bg-green-500' : 'bg-zinc-600'}`}></span>
            </button>

            {isOpen && (
                <>
                    {/* Backdrop */}
                    <div className="fixed inset-0 z-[110]" onClick={() => setIsOpen(false)}></div>

                    <div className="absolute right-0 mt-2 w-[28rem] bg-zinc-900 border border-zinc-800 rounded-2xl shadow-2xl z-[120] overflow-hidden backdrop-blur-xl">
                        <div className="p-4 border-b border-zinc-800 flex justify-between items-center bg-black/40">
                            <div className="flex items-center gap-2">
                                <h3 className="text-sm font-bold text-white uppercase tracking-widest">Notifications</h3>
                                <span className={`w-2 h-2 rounded-full ${connected ? 'bg-green-500 animate-pulse' : 'bg-zinc-600'}`}></span>
                                <span className="text-[9px] text-zinc-600 uppercase">{connected ? 'Live' : 'Offline'}</span>
                            </div>
                            <div className="flex items-center gap-3">
                                {unreadCount > 0 && (
                                    <button
                                        onClick={markAllRead}
                                        className="text-[10px] text-zinc-400 hover:text-white uppercase tracking-wider font-bold"
                                    >
                                        Mark all
                                    </button>
                                )}
                                <Link
                                    href="/notifications"
                                    onClick={() => setIsOpen(false)}
                                    className="text-[10px] text-blue-400 hover:text-blue-300 uppercase tracking-wider font-bold"
                                >
                                    View All →
                                </Link>
                            </div>
                        </div>
                        <div className="border-b border-zinc-800/80 bg-zinc-950/60 px-4 py-2 text-[10px] uppercase tracking-[0.24em] text-zinc-500">
                            Scroll recent activity
                        </div>
                        <div className="max-h-[32rem] overflow-y-auto pr-1 custom-scrollbar">
                            {visibleNotifications.length === 0 ? (
                                <div className="p-8 text-center text-zinc-600 text-xs italic">
                                    No notifications yet.
                                </div>
                            ) : (
                                visibleNotifications.map(n => (
                                    <div
                                        key={n.id}
                                        className={`p-4 border-b border-zinc-800/50 hover:bg-white/5 transition-colors flex gap-3 ${!n.read ? 'bg-green-500/5' : ''}`}
                                    >
                                        <Link
                                            href={getLink(n)}
                                            className="contents"
                                            onClick={() => {
                                                if (!n.read) {
                                                    void markRead(n.id);
                                                }
                                                setIsOpen(false);
                                            }}
                                        >
                                            <div className="w-8 h-8 rounded-full bg-zinc-800 flex items-center justify-center text-sm shrink-0">
                                                {getIcon(n.type)}
                                            </div>
                                            <div className="flex-1 min-w-0">
                                                <p className="text-xs text-zinc-300 leading-tight">
                                                    <span className="font-bold text-white">{n.actorUsername || 'System'}</span> {getMessage(n)}
                                                </p>
                                                <div className="mt-1 flex items-center gap-2 text-[10px] text-zinc-600">
                                                    <span>{formatRelativeTime(n.createdAt)}</span>
                                                    <span className="rounded-full border border-zinc-800 px-1.5 py-0.5 uppercase tracking-wide text-[9px]">
                                                        {n.type.replace(/_/g, ' ')}
                                                    </span>
                                                </div>
                                            </div>
                                            {!n.read && <div className="w-2 h-2 rounded-full bg-green-500 ml-auto shrink-0 mt-1"></div>}
                                        </Link>
                                        {canFollowBack(n) && (
                                            <button
                                                type="button"
                                                onClick={(event) => {
                                                    event.preventDefault();
                                                    event.stopPropagation();
                                                    void handleFollowBack(n);
                                                }}
                                                disabled={followBackLoadingId === n.id}
                                                className="self-start shrink-0 rounded-md border border-blue-500/30 bg-blue-500/10 px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-blue-300 hover:bg-blue-500/20 disabled:cursor-not-allowed disabled:opacity-60"
                                            >
                                                {followBackLoadingId === n.id ? 'Following...' : 'Follow back'}
                                            </button>
                                        )}
                                    </div>
                                ))
                            )}
                        </div>
                        {notifications.length > 0 && (
                            <div className="flex items-center justify-between bg-black/40 px-4 py-3">
                                <p className="text-[11px] text-zinc-500">
                                    {notifications.length} notification{notifications.length === 1 ? '' : 's'} loaded
                                </p>
                                <Link
                                    href="/notifications"
                                    onClick={() => setIsOpen(false)}
                                    className="text-[11px] font-semibold text-blue-400 hover:text-blue-300"
                                >
                                    Open full inbox
                                </Link>
                            </div>
                        )}
                    </div>
                </>
            )}
        </div>
    );
}
