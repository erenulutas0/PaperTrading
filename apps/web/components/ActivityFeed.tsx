'use client';

import { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { apiFetch } from '../lib/api-client';

interface ActivityEvent {
    id: string;
    actorId: string;
    actorUsername: string;
    eventType:
    | 'FOLLOW'
    | 'POST_CREATED'
    | 'PORTFOLIO_PUBLISHED'
    | 'PORTFOLIO_JOINED'
    | 'PORTFOLIO_LEFT'
    | 'PORTFOLIO_LIKED'
    | 'PORTFOLIO_COMMENTED'
    | 'POST_LIKED'
    | 'POST_COMMENTED'
    | 'TRADE_EXECUTED'
    | 'POST_DELETED';
    targetType: 'USER' | 'PORTFOLIO' | 'POST';
    targetId: string;
    targetLabel: string;
    createdAt: string;
}

export default function ActivityFeed() {
    const [events, setEvents] = useState<ActivityEvent[]>([]);
    const [loading, setLoading] = useState(true);
    const [filter, setFilter] = useState<'GLOBAL' | 'FOLLOWING'>('GLOBAL');

    const fetchFeed = useCallback(async () => {
        try {
            const url = filter === 'GLOBAL'
                ? '/api/v1/feed/global'
                : '/api/v1/feed';

            const res = await apiFetch(url);
            if (res.ok) {
                const data = await res.json();
                setEvents(data.content || []);
            }
        } catch (error) {
            console.error('Error fetching feed:', error);
        } finally {
            setLoading(false);
        }
    }, [filter]);

    useEffect(() => {
        fetchFeed();
        const interval = setInterval(fetchFeed, 10000);
        return () => clearInterval(interval);
    }, [fetchFeed]);

    const getEventText = (event: ActivityEvent) => {
        switch (event.eventType) {
            case 'FOLLOW':
                return `followed ${event.targetLabel}`;
            case 'POST_CREATED':
                return `published an analysis on ${event.targetLabel}`;
            case 'PORTFOLIO_PUBLISHED':
                return `made portfolio "${event.targetLabel}" public`;
            case 'PORTFOLIO_JOINED':
                return `joined the portfolio "${event.targetLabel}"`;
            case 'PORTFOLIO_LIKED':
                return `liked portfolio "${event.targetLabel}"`;
            case 'PORTFOLIO_COMMENTED':
                return `commented on portfolio "${event.targetLabel}"`;
            case 'POST_LIKED':
                return `liked analysis "${event.targetLabel}"`;
            case 'POST_COMMENTED':
                return `commented on analysis "${event.targetLabel}"`;
            case 'TRADE_EXECUTED':
                return `executed a trade in ${event.targetLabel}`;
            default:
                return `performed an action on ${event.targetLabel}`;
        }
    };

    const getTimeAgo = (dateStr: string) => {
        const date = new Date(dateStr);
        const now = new Date();
        const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);

        if (diffInSeconds < 60) return 'just now';
        if (diffInSeconds < 3600) return `${Math.floor(diffInSeconds / 60)}m ago`;
        if (diffInSeconds < 86400) return `${Math.floor(diffInSeconds / 3600)}h ago`;
        return `${Math.floor(diffInSeconds / 86400)}d ago`;
    };

    return (
        <div className="bg-black/40 backdrop-blur-xl border border-white/10 rounded-2xl overflow-hidden flex flex-col h-[600px] shadow-2xl relative">
            <div className="absolute top-0 right-0 w-32 h-32 bg-emerald-500/5 rounded-full blur-3xl pointer-events-none"></div>
            <div className="p-4 border-b border-white/5 flex justify-between items-center relative z-10">
                <h3 className="text-sm font-bold uppercase tracking-widest text-zinc-400">Activity Feed</h3>
                <div className="flex bg-white/5 rounded-lg p-1 border border-white/5">
                    <button
                        onClick={() => setFilter('GLOBAL')}
                        className={`px-3 py-1 text-[10px] font-bold rounded-md transition-all ${filter === 'GLOBAL' ? 'bg-zinc-800 text-white shadow-sm' : 'text-zinc-600 hover:text-zinc-400'}`}
                    >
                        GLOBAL
                    </button>
                    <button
                        onClick={() => setFilter('FOLLOWING')}
                        className={`px-3 py-1 text-[10px] font-bold rounded-md transition-all ${filter === 'FOLLOWING' ? 'bg-zinc-800 text-white shadow-sm' : 'text-zinc-600 hover:text-zinc-400'}`}
                    >
                        FOLLOWING
                    </button>
                </div>
            </div>

            <div className="flex-1 overflow-y-auto custom-scrollbar p-4 space-y-6">
                {loading ? (
                    <div className="flex flex-col items-center justify-center h-full space-y-4">
                        <div className="w-8 h-8 border-2 border-green-500/30 border-t-green-500 rounded-full animate-spin"></div>
                        <p className="text-zinc-600 text-xs font-medium">Synchronizing feed...</p>
                    </div>
                ) : events.length === 0 ? (
                    <div className="flex flex-col items-center justify-center h-full text-center space-y-2">
                        <p className="text-zinc-500 text-sm">No activity found.</p>
                        <p className="text-zinc-700 text-[10px] max-w-[150px]">Follow other traders to see their moves here!</p>
                    </div>
                ) : (
                    events.map((event) => (
                        <div key={event.id} className="relative pl-6 before:absolute before:left-0 before:top-2 before:bottom-[-24px] before:w-[1px] before:bg-zinc-800 last:before:hidden">
                            <div className="absolute left-[-4px] top-1.5 w-2 h-2 rounded-full bg-zinc-700 ring-4 ring-black"></div>
                            <div className="flex flex-col">
                                <div className="flex justify-between items-center mb-1">
                                    <Link href={`/profile/${event.actorId}`} className="text-xs font-bold text-zinc-200 hover:text-green-400 transition-colors">
                                        @{event.actorUsername}
                                    </Link>
                                    <span className="text-[10px] text-zinc-600 font-mono italic">
                                        {getTimeAgo(event.createdAt)}
                                    </span>
                                </div>
                                <p className="text-xs text-zinc-500 leading-relaxed">
                                    {getEventText(event)}
                                </p>
                                {event.targetType === 'POST' && (
                                    <Link
                                        href={`/dashboard/analysis/${event.targetId}`}
                                        className="mt-2 p-2 bg-white/5 border border-white/5 rounded-lg text-[10px] text-zinc-400 hover:bg-white/10 hover:border-white/10 transition-all inline-block w-fit"
                                    >
                                        View Analysis →
                                    </Link>
                                )}
                            </div>
                        </div>
                    ))
                )}
            </div>

            <div className="p-4 bg-zinc-950/40 border-t border-zinc-800">
                <Link href="/discover" className="text-[10px] text-zinc-500 hover:text-zinc-300 transition-colors block text-center uppercase tracking-widest font-bold">
                    View Discover Hub
                </Link>
            </div>
        </div>
    );
}
