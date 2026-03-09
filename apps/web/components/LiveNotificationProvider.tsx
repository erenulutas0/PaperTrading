'use client';

import { createContext, useContext, useState, useEffect, useCallback, useRef, ReactNode } from 'react';
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
    const WS_CONNECT_WATCHDOG_MS = 7000;
    const [notifications, setNotifications] = useState<NotificationPayload[]>([]);
    const [toasts, setToasts] = useState<Toast[]>([]);
    const [unreadCount, setUnreadCount] = useState(0);
    const [connected, setConnected] = useState(false);
    const knownNotificationIdsRef = useRef<Set<string>>(new Set());

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

    const mergeNotification = useCallback((notif: NotificationPayload) => {
        const alreadyKnown = notif.id ? knownNotificationIdsRef.current.has(notif.id) : false;
        setNotifications(prev => {
            const exists = prev.some(existing => existing.id === notif.id);
            if (exists) {
                return prev.map(existing => existing.id === notif.id ? { ...existing, ...notif } : existing);
            }
            if (notif.id) {
                knownNotificationIdsRef.current.add(notif.id);
            }
            return [notif, ...prev];
        });
        setUnreadCount(prev => (!alreadyKnown && !notif.read ? prev + 1 : prev));
    }, []);

    const handleIncomingNotification = useCallback((notif: NotificationPayload) => {
        const alreadyKnown = notif.id ? knownNotificationIdsRef.current.has(notif.id) : false;
        mergeNotification(notif);
        if (!alreadyKnown) {
            addToast(notif);
        }
    }, [addToast, mergeNotification]);

    // Auto-dismiss toasts after 5 seconds
    useEffect(() => {
        const timer = setInterval(() => {
            setToasts(prev => prev.filter(t => Date.now() - t.timestamp < 5000));
        }, 1000);
        return () => clearInterval(timer);
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
                knownNotificationIdsRef.current = new Set(
                    (data.content as NotificationPayload[])
                        .map(notification => notification.id)
                        .filter((id): id is string => Boolean(id))
                );
                setNotifications(data.content);
                setUnreadCount(data.content.filter((n: NotificationPayload) => !n.read).length);
            }
        }).catch(() => { });

        let transportCleanup: (() => void) | null = null;
        let connectWatchdog: ReturnType<typeof setTimeout> | null = null;
        let fallbackStarted = false;
        let wsConnected = false;
        let disposed = false;

        const clearWatchdog = () => {
            if (connectWatchdog) {
                clearTimeout(connectWatchdog);
                connectWatchdog = null;
            }
        };

        const startSseFallback = async (reason: string) => {
            if (disposed || fallbackStarted) {
                return;
            }
            fallbackStarted = true;
            clearWatchdog();
            setConnected(false);
            transportCleanup?.();
            console.warn(`[WS] Falling back to SSE: ${reason}`);

            const tokenResponse = await apiFetch('/api/v1/notifications/stream-token');
            if (!tokenResponse.ok) {
                throw new Error(`stream-token-request-failed:${tokenResponse.status}`);
            }
            const tokenPayload = await tokenResponse.json();
            const streamToken = tokenPayload.streamToken as string | undefined;
            if (!streamToken) {
                throw new Error('stream-token-missing');
            }

            const eventSource = new EventSource(`/api/v1/notifications/stream?streamToken=${encodeURIComponent(streamToken)}`);
            eventSource.addEventListener('notification', (e) => {
                try {
                    const notif = JSON.parse(e.data);
                    handleIncomingNotification(notif);
                } catch { }
            });
            eventSource.addEventListener('connected', () => {
                setConnected(true);
            });
            eventSource.onerror = () => {
                setConnected(false);
            };

            transportCleanup = () => {
                eventSource.close();
            };
        };

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
                        wsConnected = true;
                        clearWatchdog();
                        setConnected(true);
                        console.log('[WS] Connected to notification stream');

                        const onNotification = (message: IMessage) => {
                            try {
                                const notif = JSON.parse(message.body) as NotificationPayload;
                                handleIncomingNotification(notif);
                            } catch (e) {
                                console.error('[WS] Failed to parse notification:', e);
                            }
                        };

                        client.subscribe('/user/queue/notifications', onNotification);
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
                    },
                    onWebSocketClose: () => {
                        if (!wsConnected && !fallbackStarted && !disposed) {
                            void startSseFallback('websocket-closed-before-connect');
                        }
                    },
                    onWebSocketError: () => {
                        if (!wsConnected && !fallbackStarted && !disposed) {
                            void startSseFallback('websocket-error-before-connect');
                        }
                    }
                });

                client.activate();
                connectWatchdog = setTimeout(() => {
                    if (!wsConnected && !fallbackStarted && !disposed) {
                        void startSseFallback('connect-timeout');
                    }
                }, WS_CONNECT_WATCHDOG_MS);

                transportCleanup = () => {
                    clearWatchdog();
                    client.deactivate();
                };
            } catch (e) {
                console.warn('[WS] WebSocket setup failed, falling back to SSE', e);
                await startSseFallback('stomp-setup-failed');
            }
        };

        void connectStomp();

        return () => {
            disposed = true;
            clearWatchdog();
            transportCleanup?.();
        };
    }, [addToast, handleIncomingNotification]);

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
