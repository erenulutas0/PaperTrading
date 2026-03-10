'use client';

import { useState, useEffect, useCallback } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import SockJS from 'sockjs-client';
import { Client, IFrame, IMessage } from '@stomp/stompjs';
import { wsBrokerUrl, wsHttpUrl } from '../../../../lib/network';
import { apiFetch, userIdHeaders } from '../../../../lib/api-client';

interface Tournament {
    id: string;
    name: string;
    description: string;
    status: 'UPCOMING' | 'ACTIVE' | 'COMPLETED';
    startsAt: string;
    endsAt: string;
    startingBalance: number;
}

interface LeaderboardEntry {
    rank: number;
    username: string;
    userId: string;
    portfolioId: string;
    equity: number;
    returnPercent: number;
}

interface Trade {
    symbol: string;
    type: string;
    side: string;
    price: number;
    quantity: number;
    timestamp: string;
    portfolioId: string;
}

interface TradeBroadcastPayload {
    type: string;
    data: Trade;
}

const TRADE_SYMBOLS = ['BTCUSDT', 'ETHUSDT', 'SOLUSDT', 'BNBUSDT', 'DOGEUSDT'];

function formatUsd(value: number): string {
    return `$${value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export default function TournamentHubPage() {
    const params = useParams();
    const rawId = params.id;
    const tournamentId = Array.isArray(rawId) ? rawId[0] : rawId;

    const [currentUserId, setCurrentUserId] = useState<string | null>(null);
    const [tournament, setTournament] = useState<Tournament | null>(null);
    const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>([]);
    const [trades, setTrades] = useState<Trade[]>([]);
    const [loading, setLoading] = useState(true);
    const [timeLeft, setTimeLeft] = useState('');
    const [userPortfolioId, setUserPortfolioId] = useState<string | null>(null);

    const [selectedSymbol, setSelectedSymbol] = useState(TRADE_SYMBOLS[0]);
    const [tradeQty, setTradeQty] = useState('0.1');
    const [tradeLeverage, setTradeLeverage] = useState('10');
    const [isTrading, setIsTrading] = useState(false);

    useEffect(() => {
        if (typeof window !== 'undefined') {
            setCurrentUserId(localStorage.getItem('userId'));
        }
    }, []);

    const refreshLeaderboard = useCallback(async () => {
        if (!tournamentId) return;
        try {
            const res = await apiFetch(`/api/v1/tournaments/${tournamentId}/leaderboard`);
            if (res.ok) {
                setLeaderboard(await res.json());
            }
        } catch (err) {
            console.error('Failed to refresh leaderboard:', err);
        }
    }, [tournamentId]);

    const fetchData = useCallback(async () => {
        if (!tournamentId) return;

        try {
            const participantHeaders = userIdHeaders(currentUserId);
            const [tRes, lRes, trRes, pRes] = await Promise.all([
                apiFetch(`/api/v1/tournaments/${tournamentId}`),
                apiFetch(`/api/v1/tournaments/${tournamentId}/leaderboard`),
                apiFetch(`/api/v1/tournaments/${tournamentId}/trades?limit=20`),
                apiFetch(`/api/v1/tournaments/${tournamentId}/participant`, {
                    headers: participantHeaders,
                }),
            ]);

            if (tRes.ok) setTournament(await tRes.json());
            if (lRes.ok) setLeaderboard(await lRes.json());
            if (trRes.ok) setTrades(await trRes.json());
            if (pRes.ok) {
                const participant = await pRes.json();
                setUserPortfolioId(participant.portfolioId);
            }
        } catch (err) {
            console.error('Failed to fetch tournament data:', err);
        } finally {
            setLoading(false);
        }
    }, [currentUserId, tournamentId]);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    useEffect(() => {
        if (!tournamentId) return;
        const accessToken = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
        if (!accessToken) return;
        const connectHeaders: Record<string, string> = {
            Authorization: `Bearer ${accessToken}`,
        };

        const client = new Client({
            brokerURL: wsBrokerUrl('/ws'),
            webSocketFactory: () => new SockJS(wsHttpUrl('/ws')) as WebSocket,
            connectHeaders,
            debug: () => {},
            reconnectDelay: 5000,
            heartbeatIncoming: 4000,
            heartbeatOutgoing: 4000,
        });

        client.onConnect = () => {
            client.subscribe(`/topic/tournament/${tournamentId}`, (message: IMessage) => {
                const payload = JSON.parse(message.body) as TradeBroadcastPayload;
                if (payload.type === 'TRADE') {
                    setTrades((prev) => [payload.data, ...prev].slice(0, 20));
                    refreshLeaderboard();
                }
            });
        };

        client.onStompError = (frame: IFrame) => {
            console.error('Broker reported error: ' + frame.headers['message']);
            console.error('Additional details: ' + frame.body);
        };

        client.activate();

        return () => {
            if (client.active) client.deactivate();
        };
    }, [refreshLeaderboard, tournamentId]);

    useEffect(() => {
        if (!tournamentId) return;

        const interval = setInterval(() => {
            refreshLeaderboard();
        }, 30000);

        return () => clearInterval(interval);
    }, [refreshLeaderboard, tournamentId]);

    useEffect(() => {
        if (!tournament) return;

        const timer = setInterval(() => {
            const end = new Date(tournament.endsAt).getTime();
            const now = Date.now();
            const diff = end - now;

            if (diff < 0) {
                setTimeLeft('CLOSED');
                clearInterval(timer);
                return;
            }

            const days = Math.floor(diff / (1000 * 60 * 60 * 24));
            const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
            const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
            const seconds = Math.floor((diff % (1000 * 60)) / 1000);

            setTimeLeft(`${days}d ${hours}h ${minutes}m ${seconds}s`);
        }, 1000);

        return () => clearInterval(timer);
    }, [tournament]);

    const handleQuickTrade = async (orderSide: 'LONG' | 'SHORT') => {
        if (!tournamentId) return;
        if (!userPortfolioId) {
            alert('Join the tournament first to trade.');
            return;
        }

        const parsedQty = parseFloat(tradeQty);
        const parsedLeverage = parseInt(tradeLeverage, 10);
        if (!Number.isFinite(parsedQty) || parsedQty <= 0) {
            alert('Invalid quantity.');
            return;
        }

        setIsTrading(true);
        try {
            const res = await apiFetch('/api/v1/trade/buy', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    portfolioId: userPortfolioId,
                    symbol: selectedSymbol,
                    quantity: parsedQty,
                    leverage: parsedLeverage,
                    side: orderSide,
                }),
            });

            if (!res.ok) {
                const err = await res.text();
                alert(`Trade failed: ${err}`);
            }
        } catch (err) {
            console.error('Trade error:', err);
            alert('Network error executing trade.');
        } finally {
            setIsTrading(false);
        }
    };

    if (loading && !tournament) {
        return (
            <div className="min-h-screen bg-background text-foreground flex items-center justify-center">
                <div className="animate-spin rounded-full h-10 w-10 border-2 border-primary border-t-transparent" />
            </div>
        );
    }

    if (!tournament) {
        return (
            <div className="min-h-screen bg-background text-foreground flex items-center justify-center">
                Tournament not found.
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-background text-foreground">
            <div className="noise" />
            <div className="relative z-10 max-w-7xl mx-auto px-4 py-8 space-y-6">
                <header className="flex items-center justify-between">
                    <Link href="/tournaments" className="text-sm text-muted-foreground hover:text-foreground transition-colors">
                        ← Back to Tournaments
                    </Link>
                    <span className="rounded-full border border-primary/30 bg-primary/10 px-3 py-1 text-xs font-semibold text-primary">
                        {tournament.status}
                    </span>
                </header>

                <section className="glass-panel border border-border/80 rounded-2xl p-6 space-y-4">
                    <h1 className="text-3xl md:text-4xl font-bold tracking-tight">{tournament.name}</h1>
                    <p className="text-muted-foreground max-w-3xl">{tournament.description}</p>
                    <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-4">
                        <article className="rounded-xl border border-border bg-background/60 p-4">
                            <p className="text-xs uppercase tracking-wide text-muted-foreground">Time Remaining</p>
                            <p className="mt-2 font-semibold">{timeLeft || 'Calculating...'}</p>
                        </article>
                        <article className="rounded-xl border border-border bg-background/60 p-4">
                            <p className="text-xs uppercase tracking-wide text-muted-foreground">Starting Capital</p>
                            <p className="mt-2 font-semibold">{formatUsd(tournament.startingBalance)}</p>
                        </article>
                        <article className="rounded-xl border border-border bg-background/60 p-4">
                            <p className="text-xs uppercase tracking-wide text-muted-foreground">Starts</p>
                            <p className="mt-2 font-semibold">{new Date(tournament.startsAt).toLocaleString()}</p>
                        </article>
                        <article className="rounded-xl border border-border bg-background/60 p-4">
                            <p className="text-xs uppercase tracking-wide text-muted-foreground">Ends</p>
                            <p className="mt-2 font-semibold">{new Date(tournament.endsAt).toLocaleString()}</p>
                        </article>
                    </div>
                </section>

                <div className="grid gap-6 lg:grid-cols-3">
                    <section className="lg:col-span-2 glass-panel border border-border/80 rounded-2xl p-4">
                        <div className="mb-3 flex items-center justify-between px-2">
                            <h2 className="text-lg font-semibold">Live Leaderboard</h2>
                            <button
                                onClick={refreshLeaderboard}
                                className="rounded-lg border border-border bg-accent px-3 py-1.5 text-xs font-medium text-muted-foreground hover:text-foreground"
                            >
                                Refresh
                            </button>
                        </div>
                        <div className="overflow-x-auto">
                            <table className="w-full min-w-[720px] text-sm">
                                <thead>
                                    <tr className="border-b border-border text-muted-foreground">
                                        <th className="p-3 text-left">Rank</th>
                                        <th className="p-3 text-left">Trader</th>
                                        <th className="p-3 text-right">Equity</th>
                                        <th className="p-3 text-right">Return</th>
                                        <th className="p-3 text-right">Action</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {leaderboard.length === 0 ? (
                                        <tr>
                                            <td colSpan={5} className="p-6 text-center text-muted-foreground">
                                                No leaderboard entries yet.
                                            </td>
                                        </tr>
                                    ) : (
                                        leaderboard.map((entry) => {
                                            const isCurrentUser = currentUserId != null && entry.userId === currentUserId;
                                            return (
                                                <tr
                                                    key={entry.userId}
                                                    className={`border-b border-border/60 ${isCurrentUser ? 'bg-primary/5' : 'hover:bg-accent/40'} transition-colors`}
                                                >
                                                    <td className="p-3 font-semibold">#{entry.rank}</td>
                                                    <td className="p-3 font-medium">
                                                        {entry.username}
                                                        {isCurrentUser && <span className="ml-2 text-xs text-primary">(You)</span>}
                                                    </td>
                                                    <td className="p-3 text-right">{formatUsd(entry.equity)}</td>
                                                    <td className={`p-3 text-right font-semibold ${entry.returnPercent >= 0 ? 'text-success' : 'text-destructive'}`}>
                                                        {entry.returnPercent >= 0 ? '+' : ''}{entry.returnPercent.toFixed(2)}%
                                                    </td>
                                                    <td className="p-3 text-right">
                                                        <Link href={`/analytics/${entry.portfolioId}`} className="text-primary hover:text-primary/80 text-xs">
                                                            Audit
                                                        </Link>
                                                    </td>
                                                </tr>
                                            );
                                        })
                                    )}
                                </tbody>
                            </table>
                        </div>
                    </section>

                    <div className="space-y-6">
                        <section className="glass-panel border border-border/80 rounded-2xl p-5 space-y-4">
                            <h2 className="text-lg font-semibold">Quick Trade</h2>

                            <div className="grid grid-cols-5 gap-2">
                                {TRADE_SYMBOLS.map((symbol) => (
                                    <button
                                        key={symbol}
                                        onClick={() => setSelectedSymbol(symbol)}
                                        className={`rounded-lg border px-2 py-2 text-xs font-semibold transition ${selectedSymbol === symbol
                                            ? 'border-primary/40 bg-primary/15 text-primary'
                                            : 'border-border bg-background/60 text-muted-foreground hover:text-foreground'
                                            }`}
                                    >
                                        {symbol.replace('USDT', '')}
                                    </button>
                                ))}
                            </div>

                            <div className="grid gap-3 sm:grid-cols-2">
                                <div className="space-y-1">
                                    <label className="text-xs text-muted-foreground">Quantity</label>
                                    <input
                                        type="number"
                                        value={tradeQty}
                                        onChange={(e) => setTradeQty(e.target.value)}
                                        className="w-full rounded-lg border border-border bg-input-background p-2.5 text-sm outline-none focus:border-primary/50"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs text-muted-foreground">Leverage</label>
                                    <select
                                        value={tradeLeverage}
                                        onChange={(e) => setTradeLeverage(e.target.value)}
                                        className="w-full rounded-lg border border-border bg-input-background p-2.5 text-sm outline-none focus:border-primary/50"
                                    >
                                        <option value="1">1x</option>
                                        <option value="5">5x</option>
                                        <option value="10">10x</option>
                                        <option value="20">20x</option>
                                        <option value="50">50x</option>
                                    </select>
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-2">
                                <button
                                    onClick={() => handleQuickTrade('LONG')}
                                    disabled={isTrading || tournament.status !== 'ACTIVE'}
                                    className="rounded-lg bg-success/15 border border-success/35 px-3 py-2 text-sm font-semibold text-success transition hover:bg-success/25 disabled:opacity-50"
                                >
                                    {isTrading ? 'Sending...' : 'Long'}
                                </button>
                                <button
                                    onClick={() => handleQuickTrade('SHORT')}
                                    disabled={isTrading || tournament.status !== 'ACTIVE'}
                                    className="rounded-lg bg-destructive/15 border border-destructive/35 px-3 py-2 text-sm font-semibold text-destructive transition hover:bg-destructive/25 disabled:opacity-50"
                                >
                                    {isTrading ? 'Sending...' : 'Short'}
                                </button>
                            </div>
                        </section>

                        <section className="glass-panel border border-border/80 rounded-2xl p-5">
                            <h2 className="mb-4 text-lg font-semibold">Live Trade Feed</h2>
                            <div className="max-h-[360px] space-y-2 overflow-y-auto pr-1">
                                {trades.length === 0 ? (
                                    <p className="text-sm text-muted-foreground">Waiting for live trades...</p>
                                ) : (
                                    trades.map((trade, index) => (
                                        <article key={`${trade.timestamp}-${trade.portfolioId}-${index}`} className="rounded-lg border border-border bg-background/60 p-3">
                                            <div className="flex items-center justify-between gap-2">
                                                <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${trade.side === 'LONG'
                                                    ? 'bg-success/10 text-success'
                                                    : 'bg-destructive/10 text-destructive'
                                                    }`}>
                                                    {trade.side} {trade.type}
                                                </span>
                                                <span className="text-[11px] text-muted-foreground">{new Date(trade.timestamp).toLocaleTimeString()}</span>
                                            </div>
                                            <div className="mt-2 flex items-center justify-between">
                                                <p className="font-semibold">{trade.symbol}</p>
                                                <p className="text-sm text-muted-foreground">Qty {trade.quantity}</p>
                                            </div>
                                            <p className="mt-1 text-sm text-muted-foreground">Price {formatUsd(trade.price)}</p>
                                        </article>
                                    ))
                                )}
                            </div>
                        </section>
                    </div>
                </div>
            </div>
        </div>
    );
}
