'use client';

import { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react';
import type { IMessage } from '@stomp/stompjs';
import { wsHttpUrl } from '../lib/network';
import { apiFetch } from '../lib/api-client';

interface NotificationPayload {
    id: string;
    type: string;
    actorUsername: string;
    referenceLabel: string;
    referenceId: string;
    read: boolean;
    createdAt: string;
}

interface Toast {
    id: string;
    message: string;
    type: string;
    timestamp: number;
}

interface LiveNotificationContextType {
    notifications: NotificationPayload[];
    toasts: Toast[];
    unreadCount: number;
    dismissToast: (id: string) => void;
    markRead: (notificationId: string) => Promise<void>;
    markAllRead: () => void;
    connected: boolean;
}

const LiveNotificationContext = createContext<LiveNotificationContextType>({
    notifications: [],
    toasts: [],
    unreadCount: 0,
    dismissToast: () => { },
    markRead: async () => { },
    markAllRead: () => { },
    connected: false,
});

export const useLiveNotifications = () => useContext(LiveNotificationContext);

function getNotifMessage(n: NotificationPayload): string {
    switch (n.type) {
        case 'FOLLOW': return `${n.actorUsername} started following you`;
        case 'PORTFOLIO_LIKE': return `${n.actorUsername} liked "${n.referenceLabel}"`;
        case 'POST_LIKE': return `${n.actorUsername} liked your analysis "${n.referenceLabel}"`;
        case 'PORTFOLIO_COMMENT': return `${n.actorUsername} commented on "${n.referenceLabel}"`;
        case 'POST_COMMENT': return `${n.actorUsername} commented on "${n.referenceLabel}"`;
        case 'PORTFOLIO_JOINED': return `${n.actorUsername} joined "${n.referenceLabel}"`;
        case 'PRICE_ALERT': return `${n.referenceLabel}`;
        default: return `${n.actorUsername || 'System'} sent a notification`;
    }
}

function getNotifIcon(type: string): string {
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
}

export function LiveNotificationProvider({ children }: { children: ReactNode }) {
    const [notifications, setNotifications] = useState<NotificationPayload[]>([]);
    const [toasts, setToasts] = useState<Toast[]>([]);
    const [unreadCount, setUnreadCount] = useState(0);
    const [connected, setConnected] = useState(false);

    // Auto-dismiss toasts after 5 seconds
    useEffect(() => {
        const timer = setInterval(() => {
            setToasts(prev => prev.filter(t => Date.now() - t.timestamp < 5000));
        }, 1000);
        return () => clearInterval(timer);
    }, []);

    const addToast = useCallback((notif: NotificationPayload) => {
        const toast: Toast = {
            id: notif.id || Math.random().toString(36),
            message: `${getNotifIcon(notif.type)} ${getNotifMessage(notif)}`,
            type: notif.type,
            timestamp: Date.now(),
        };
        setToasts(prev => [toast, ...prev].slice(0, 5)); // max 5 toasts

        // Play notification sound
        try {
            const audio = new Audio('data:audio/wav;base64,UklGRnoGAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQoGAACBhYqFbX19fX+Fk5WHf3V0e4COmJWJe3N0f4ucn5WGdnF1gJCfoJmKdnFzgJCmo56OgHVxdoWZqKOZg3RvcIWbq6Wch3dxcIOVqKWXhXhzc4KQo6KWhXp5d4KOnamhlYF6e3mBjaWnnJSAfn57fYudp6KXgH9/fHyJm6eil4OChH5+iZekopaDhYZ/foaUoqKWhIeIgH+Fk6Cil4WIiYGBhZCcn5WFiYqCg4aNl5qSg4aHgoiJjpWXjYGEhIOHi46Tk42DhYWFh4mNkJCMhIWGhoaIi42OjISFhoeHiIqLjIyGhoaHh4iJiouLiIaHh4eHiImKioiIh4eHh4iIiYmJiIeHh4eHiIiIiIiHh4eHh4eIiIiIh4eHh4eHiIiIh4eHh4eHh4eIh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHhw==');
            audio.volume = 0.3;
            audio.play().catch(() => { });
        } catch { }
    }, []);

    const dismissToast = useCallback((id: string) => {
        setToasts(prev => prev.filter(t => t.id !== id));
    }, []);

    const markAllRead = useCallback(async () => {
        const userId = localStorage.getItem('userId');
        if (!userId) return;
        try {
            await apiFetch('/api/v1/notifications/mark-read', {
                method: 'POST'
            });
            setUnreadCount(0);
            setNotifications(prev => prev.map(n => ({ ...n, read: true })));
        } catch { }
    }, []);

    const markRead = useCallback(async (notificationId: string) => {
        const userId = localStorage.getItem('userId');
        if (!userId || !notificationId) return;

        try {
            const res = await apiFetch(`/api/v1/notifications/${notificationId}/mark-read`, {
                method: 'POST'
            });

            if (!res.ok) return;

            setNotifications(prev => {
                const next = prev.map(n => (n.id === notificationId ? { ...n, read: true } : n));
                setUnreadCount(next.filter(n => !n.read).length);
                return next;
            });
        } catch { }
    }, []);

    useEffect(() => {
        const userId = localStorage.getItem('userId');
        const accessToken = localStorage.getItem('accessToken');
        if (!userId) return;

        // Fetch initial notifications
        apiFetch('/api/v1/notifications').then(res => res.json()).then(data => {
            if (data.content) {
                setNotifications(data.content);
                setUnreadCount(data.content.filter((n: NotificationPayload) => !n.read).length);
            }
        }).catch(() => { });

        // 1. Try WebSocket/STOMP connection
        let stompCleanup: (() => void) | null = null;

        const connectStomp = async () => {
            try {
                const { Client } = await import('@stomp/stompjs');
                const SockJS = (await import('sockjs-client')).default;
                const connectHeaders: Record<string, string> = {};
                if (accessToken) {
                    connectHeaders.Authorization = `Bearer ${accessToken}`;
                } else {
                    connectHeaders['X-User-Id'] = userId;
                }

                const client = new Client({
                    webSocketFactory: () => new SockJS(wsHttpUrl('/ws')) as WebSocket,
                    connectHeaders,
                    reconnectDelay: 5000,
                    heartbeatIncoming: 10000,
                    heartbeatOutgoing: 10000,
                    onConnect: () => {
                        setConnected(true);
                        console.log('[WS] Connected to notification stream');

                        const onNotification = (message: IMessage) => {
                            try {
                                const notif = JSON.parse(message.body) as NotificationPayload;
                                setNotifications(prev => [notif, ...prev]);
                                setUnreadCount(prev => prev + 1);
                                addToast(notif);
                            } catch (e) {
                                console.error('[WS] Failed to parse notification:', e);
                            }
                        };

                        // Standard Spring user destination subscription.
                        client.subscribe('/user/queue/notifications', onNotification);

                        // Subscribe to global market alerts (broadcast)
                        client.subscribe('/topic/market', (message) => {
                            try {
                                const data = JSON.parse(message.body);
                                addToast({
                                    id: Math.random().toString(36),
                                    type: 'MARKET',
                                    actorUsername: 'Market',
                                    referenceLabel: data.message || 'Market update',
                                    referenceId: '',
                                    read: false,
                                    createdAt: new Date().toISOString(),
                                });
                            } catch { }
                        });
                    },
                    onDisconnect: () => {
                        setConnected(false);
                        console.log('[WS] Disconnected');
                    },
                    onStompError: (frame) => {
                        console.error('[WS] STOMP error:', frame.headers['message']);
                        setConnected(false);
                    }
                });

                client.activate();

                stompCleanup = () => {
                    client.deactivate();
                };
            } catch (e) {
                console.warn('[WS] WebSocket connection failed, falling back to SSE', e);

                // 2. Fallback to SSE
                const eventSource = new EventSource(`/api/v1/notifications/stream?userId=${userId}`);
                eventSource.addEventListener('notification', (e) => {
                    try {
                        const notif = JSON.parse(e.data);
                        setNotifications(prev => [notif, ...prev]);
                        setUnreadCount(prev => prev + 1);
                        addToast(notif);
                    } catch { }
                });
                setConnected(true);

                stompCleanup = () => {
                    eventSource.close();
                };
            }
        };

        connectStomp();

        return () => {
            stompCleanup?.();
        };
    }, [addToast]);

    return (
        <LiveNotificationContext.Provider value={{ notifications, toasts, unreadCount, dismissToast, markRead, markAllRead, connected }}>
            {children}

            {/* Toast Container — fixed bottom-right */}
            <div className="fixed bottom-6 right-6 z-[9999] flex flex-col gap-2 pointer-events-none">
                {toasts.map(toast => (
                    <div
                        key={toast.id}
                        className="pointer-events-auto animate-in slide-in-from-right fade-in duration-300 bg-zinc-900/95 backdrop-blur-xl border border-white/10 rounded-xl shadow-2xl px-5 py-3 min-w-[320px] max-w-[420px] flex items-start gap-3"
                    >
                        <div className="flex-1">
                            <p className="text-sm text-zinc-200 leading-snug">{toast.message}</p>
                            <p className="text-[10px] text-zinc-600 mt-1">{new Date(toast.timestamp).toLocaleTimeString()}</p>
                        </div>
                        <button
                            onClick={() => dismissToast(toast.id)}
                            className="text-zinc-600 hover:text-white text-xs shrink-0 mt-0.5"
                        >
                            ✕
                        </button>
                    </div>
                ))}
            </div>
        </LiveNotificationContext.Provider>
    );
}
