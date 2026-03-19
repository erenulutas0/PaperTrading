'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { useLiveNotifications } from './LiveNotificationProvider';
import { apiFetch } from '../lib/api-client';
import { getNotificationIcon, getNotificationLink, getNotificationMessage } from '../lib/notification-presentation';

interface NotificationItem {
    id: string;
    type: string;
    actorUsername: string;
    referenceLabel: string;
    referenceId: string;
    read: boolean;
    createdAt: string;
}

type BellTab = 'RECENT' | 'ALERTS' | 'SOCIAL';

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
    const [activeTab, setActiveTab] = useState<BellTab>('RECENT');
    const [followBackLoadingId, setFollowBackLoadingId] = useState<string | null>(null);
    const [followedActorIds, setFollowedActorIds] = useState<Set<string>>(new Set());
    const currentUserId = typeof window !== 'undefined' ? localStorage.getItem('userId') : null;

    const visibleNotifications = useMemo(() => notifications.slice(0, 30), [notifications]);
    const priceAlertCount = useMemo(() => notifications.filter((notification) => notification.type === 'PRICE_ALERT').length, [notifications]);
    const socialCount = useMemo(() => notifications.filter((notification) => notification.type !== 'PRICE_ALERT').length, [notifications]);
    const tabNotifications = useMemo(() => {
        switch (activeTab) {
            case 'ALERTS':
                return visibleNotifications.filter((notification) => notification.type === 'PRICE_ALERT');
            case 'SOCIAL':
                return visibleNotifications.filter((notification) => notification.type !== 'PRICE_ALERT');
            default:
                return visibleNotifications;
        }
    }, [activeTab, visibleNotifications]);

    const toggleOpen = () => {
        setIsOpen(!isOpen);
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
                        <div className="grid grid-cols-3 gap-px border-b border-zinc-800/80 bg-zinc-800/80">
                            <div className="bg-zinc-950/70 px-4 py-3">
                                <p className="text-[9px] uppercase tracking-[0.24em] text-zinc-500">Unread</p>
                                <p className="mt-2 text-lg font-black text-white">{unreadCount}</p>
                            </div>
                            <div className="bg-zinc-950/70 px-4 py-3">
                                <p className="text-[9px] uppercase tracking-[0.24em] text-zinc-500">Loaded</p>
                                <p className="mt-2 text-lg font-black text-white">{notifications.length}</p>
                            </div>
                            <div className="bg-zinc-950/70 px-4 py-3">
                                <p className="text-[9px] uppercase tracking-[0.24em] text-zinc-500">Alerts</p>
                                <p className="mt-2 text-lg font-black text-white">{priceAlertCount}</p>
                            </div>
                        </div>
                        <div className="border-b border-zinc-800/80 bg-zinc-950/70 px-3 py-2">
                            <div className="flex flex-wrap gap-2">
                                {([
                                    { key: 'RECENT', label: 'Recent', badge: `${visibleNotifications.length}` },
                                    { key: 'ALERTS', label: 'Alerts', badge: `${priceAlertCount}` },
                                    { key: 'SOCIAL', label: 'Social', badge: `${socialCount}` },
                                ] as const).map(({ key, label, badge }) => (
                                    <button
                                        key={key}
                                        type="button"
                                        onClick={() => setActiveTab(key)}
                                        className={`inline-flex items-center gap-2 rounded-full border px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.18em] transition-colors ${
                                            activeTab === key
                                                ? 'border-green-500/30 bg-green-500/10 text-green-300'
                                                : 'border-zinc-800 bg-black/40 text-zinc-500 hover:text-white'
                                        }`}
                                    >
                                        <span>{label}</span>
                                        <span className={`rounded-full px-1.5 py-0.5 text-[9px] ${
                                            activeTab === key ? 'bg-green-500/15 text-green-200' : 'bg-zinc-900 text-zinc-500'
                                        }`}>
                                            {badge}
                                        </span>
                                    </button>
                                ))}
                            </div>
                        </div>
                        <div className="border-b border-zinc-800/80 bg-zinc-950/60 px-4 py-2 text-[10px] uppercase tracking-[0.24em] text-zinc-500">
                            {activeTab === 'RECENT' && 'Scroll recent activity'}
                            {activeTab === 'ALERTS' && 'Price alert slice'}
                            {activeTab === 'SOCIAL' && 'Social interaction slice'}
                        </div>
                        <div className="max-h-[32rem] overflow-y-auto pr-1 custom-scrollbar">
                            {tabNotifications.length === 0 ? (
                                <div className="p-8 text-center">
                                    <p className="text-[10px] uppercase tracking-[0.28em] text-zinc-600">Inbox Empty</p>
                                    <p className="mt-3 text-sm font-semibold text-zinc-200">No notifications in this slice.</p>
                                    <p className="mt-2 text-xs leading-6 text-zinc-500">
                                        {activeTab === 'RECENT' && 'New follows, alerts, and discussion events will start appearing here.'}
                                        {activeTab === 'ALERTS' && 'Price-triggered events will appear here once watchlist alerts fire.'}
                                        {activeTab === 'SOCIAL' && 'Follows, likes, comments, and replies will collect here.'}
                                    </p>
                                </div>
                            ) : (
                                tabNotifications.map(n => (
                                    <div
                                        key={n.id}
                                        className={`p-4 border-b border-zinc-800/50 hover:bg-white/5 transition-colors flex gap-3 ${!n.read ? 'bg-green-500/5' : ''}`}
                                    >
                                        <Link
                                            href={getNotificationLink(n)}
                                            className="contents"
                                            onClick={() => {
                                                if (!n.read) {
                                                    void markRead(n.id);
                                                }
                                                setIsOpen(false);
                                            }}
                                        >
                                            <div className="w-8 h-8 rounded-full bg-zinc-800 flex items-center justify-center text-sm shrink-0">
                                                {getNotificationIcon(n.type)}
                                            </div>
                                            <div className="flex-1 min-w-0">
                                                <p className="text-xs text-zinc-300 leading-tight">
                                                    <span className="font-bold text-white">{n.actorUsername || 'System'}</span> {getNotificationMessage(n)}
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
                                        <div className="flex shrink-0 flex-col items-end gap-2">
                                            {!n.read && (
                                                <button
                                                    type="button"
                                                    onClick={(event) => {
                                                        event.preventDefault();
                                                        event.stopPropagation();
                                                        void markRead(n.id);
                                                    }}
                                                    className="self-start rounded-md border border-emerald-500/30 bg-emerald-500/10 px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-emerald-300 hover:bg-emerald-500/20"
                                                >
                                                    Mark read
                                                </button>
                                            )}
                                            {canFollowBack(n) && (
                                                <button
                                                    type="button"
                                                    onClick={(event) => {
                                                        event.preventDefault();
                                                        event.stopPropagation();
                                                        void handleFollowBack(n);
                                                    }}
                                                    disabled={followBackLoadingId === n.id}
                                                    className="self-start rounded-md border border-blue-500/30 bg-blue-500/10 px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-blue-300 hover:bg-blue-500/20 disabled:cursor-not-allowed disabled:opacity-60"
                                                >
                                                    {followBackLoadingId === n.id ? 'Following...' : 'Follow back'}
                                                </button>
                                            )}
                                        </div>
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
