'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useLiveNotifications } from './LiveNotificationProvider';

interface NotificationItem {
    id: string;
    type: string;
    actorUsername: string;
    referenceLabel: string;
    referenceId: string;
    read: boolean;
    createdAt: string;
}

export default function NotificationBell() {
    const { notifications, unreadCount, markAllRead, markRead, connected } = useLiveNotifications();
    const [isOpen, setIsOpen] = useState(false);

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

    return (
        <div className="relative">
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
                    <div className="fixed inset-0 z-40" onClick={() => setIsOpen(false)}></div>

                    <div className="absolute right-0 mt-2 w-96 bg-zinc-900 border border-zinc-800 rounded-2xl shadow-2xl z-50 overflow-hidden backdrop-blur-xl">
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
                        <div className="max-h-96 overflow-y-auto custom-scrollbar">
                            {notifications.length === 0 ? (
                                <div className="p-8 text-center text-zinc-600 text-xs italic">
                                    No notifications yet.
                                </div>
                            ) : (
                                notifications.slice(0, 15).map(n => (
                                    <Link
                                        key={n.id}
                                        href={getLink(n)}
                                        onClick={() => {
                                            if (!n.read) {
                                                void markRead(n.id);
                                            }
                                            setIsOpen(false);
                                        }}
                                    >
                                        <div className={`p-4 border-b border-zinc-800/50 hover:bg-white/5 transition-colors flex gap-3 ${!n.read ? 'bg-green-500/5' : ''}`}>
                                            <div className="w-8 h-8 rounded-full bg-zinc-800 flex items-center justify-center text-sm shrink-0">
                                                {getIcon(n.type)}
                                            </div>
                                            <div className="flex-1 min-w-0">
                                                <p className="text-xs text-zinc-300 leading-tight">
                                                    <span className="font-bold text-white">{n.actorUsername || 'System'}</span> {getMessage(n)}
                                                </p>
                                                <p className="text-[10px] text-zinc-600 mt-1">{n.createdAt ? new Date(n.createdAt).toLocaleString() : ''}</p>
                                            </div>
                                            {!n.read && <div className="w-2 h-2 rounded-full bg-green-500 ml-auto shrink-0 mt-1"></div>}
                                        </div>
                                    </Link>
                                ))
                            )}
                        </div>
                    </div>
                </>
            )}
        </div>
    );
}
