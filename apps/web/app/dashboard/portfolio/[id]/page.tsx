'use client';

import { useState, useEffect, use, useCallback } from 'react';
import Link from 'next/link';
import PortfolioChart from '../../../../components/PortfolioChart';
import LikeCommentWidget from '../../../../components/LikeCommentWidget';
import { apiFetch } from '../../../../lib/api-client';

interface PortfolioItem {
    id: string;
    symbol: string;
    quantity: number;
    averagePrice: number;
    leverage: number;
    side: 'LONG' | 'SHORT';
    lastUpdated: string;
}

interface TradeActivity {
    id: string;
    symbol: string;
    type: string;
    side: string;
    quantity: number;
    price: number;
    realizedPnl: number | null;
    timestamp: string;
}

interface SnapshotPoint {
    timestamp: string;
    totalEquity: number;
    cashBalance: number;
    assetsEquity: number;
}

interface Portfolio {
    id: string;
    name: string;
    ownerId: string;
    balance: number;
    visibility: 'PUBLIC' | 'PRIVATE';
    items: PortfolioItem[];
    returnPercentage1D?: number;
    returnPercentage1W?: number;
    returnPercentage1M?: number;
    returnPercentageALL?: number;
    maxDrawdown?: number;
    sharpeRatio?: number;
}

const PERIODS = ['1D', '1W', '1M', 'ALL'] as const;
type Period = (typeof PERIODS)[number];

function formatUsd(value: number): string {
    return `$${value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export default function PortfolioDetailPage({ params }: { params: Promise<{ id: string }> }) {
    const { id } = use(params);

    const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
    const [history, setHistory] = useState<TradeActivity[]>([]);
    const [snapshots, setSnapshots] = useState<SnapshotPoint[]>([]);
    const [prices, setPrices] = useState<Record<string, number>>({});
    const [loading, setLoading] = useState(true);
    const [depositAmount, setDepositAmount] = useState('');
    const [showDeposit, setShowDeposit] = useState(false);
    const [selectedPeriod, setSelectedPeriod] = useState<Period>('ALL');

    const fetchSnapshots = useCallback(async () => {
        try {
            const res = await apiFetch(`/api/v1/portfolios/${id}/snapshots`);
            if (res.ok) {
                setSnapshots(await res.json());
            }
        } catch {
            console.debug('Snapshots fetch failed (expected during startup/restart)');
        }
    }, [id]);

    const fetchPrices = useCallback(async () => {
        try {
            const res = await apiFetch('/api/v1/market/prices');
            if (res.ok) {
                setPrices(await res.json());
            }
        } catch {
            console.debug('Prices fetch failed');
        }
    }, []);

    const fetchPortfolio = useCallback(async () => {
        try {
            const res = await apiFetch(`/api/v1/portfolios/${id}?t=${Date.now()}`, {
                cache: 'no-store',
            });
            if (!res.ok) throw new Error('Failed to fetch portfolio');
            setPortfolio(await res.json());
        } catch (error) {
            console.error(error);
        } finally {
            setLoading(false);
        }
    }, [id]);

    const fetchHistory = useCallback(async () => {
        try {
            const res = await apiFetch(`/api/v1/portfolios/${id}/history`);
            if (res.ok) {
                setHistory(await res.json());
            }
        } catch (error) {
            console.error(error);
        }
    }, [id]);

    useEffect(() => {
        fetchPortfolio();
        fetchHistory();
        fetchSnapshots();
        fetchPrices();

        const interval = setInterval(() => {
            fetchPrices();
            fetchSnapshots();
        }, 5000);

        return () => clearInterval(interval);
    }, [fetchPortfolio, fetchHistory, fetchSnapshots, fetchPrices]);

    const handleDeposit = async (e: React.FormEvent) => {
        e.preventDefault();
        const parsedAmount = parseFloat(depositAmount);
        if (!Number.isFinite(parsedAmount) || parsedAmount <= 0) {
            alert('Please enter a valid amount.');
            return;
        }

        try {
            const res = await apiFetch(`/api/v1/portfolios/${id}/deposit`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ amount: parsedAmount }),
            });
            if (res.ok) {
                setDepositAmount('');
                setShowDeposit(false);
                fetchPortfolio();
            }
        } catch (error) {
            console.error(error);
        }
    };

    const handleToggleVisibility = async () => {
        if (!portfolio) return;
        const newVisibility = portfolio.visibility === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC';

        try {
            const res = await apiFetch(`/api/v1/portfolios/${id}/visibility`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ visibility: newVisibility }),
            });
            if (res.ok) {
                const updated = await res.json();
                setPortfolio((prev) => (prev ? { ...prev, visibility: updated.visibility ?? newVisibility } : prev));
                fetchPortfolio();
            } else {
                const message = await res.text();
                alert(`Could not update visibility: ${message || res.status}`);
            }
        } catch (error) {
            console.error(error);
            alert('Could not update visibility. Please try again.');
        }
    };

    if (loading) {
        return <div className="min-h-screen bg-background text-foreground p-8 text-center">Loading portfolio...</div>;
    }

    if (!portfolio) {
        return <div className="min-h-screen bg-background text-foreground p-8 text-center">Portfolio not found</div>;
    }

    const assetsEquity =
        portfolio.items?.reduce((acc, item) => {
            const currentPrice = prices[item.symbol] ?? item.averagePrice;
            const margin = (item.quantity * item.averagePrice) / (item.leverage || 1);
            const unrealizedPL =
                item.side === 'SHORT'
                    ? (item.averagePrice - currentPrice) * item.quantity
                    : (currentPrice - item.averagePrice) * item.quantity;
            return acc + (margin + unrealizedPL);
        }, 0) || 0;

    const totalValue = (portfolio.balance || 0) + assetsEquity;

    const currentReturn =
        selectedPeriod === '1D'
            ? portfolio.returnPercentage1D
            : selectedPeriod === '1W'
                ? portfolio.returnPercentage1W
                : selectedPeriod === '1M'
                    ? portfolio.returnPercentage1M
                    : portfolio.returnPercentageALL;

    const liveEquity = snapshots.length > 0 ? snapshots[snapshots.length - 1].totalEquity : totalValue;

    return (
        <div className="min-h-screen bg-background text-foreground">
            <div className="noise" />
            <div className="relative z-10 max-w-7xl mx-auto px-4 py-8 space-y-6">
                <header className="flex items-center justify-between">
                    <Link href="/dashboard" className="text-sm text-muted-foreground hover:text-foreground transition-colors">
                        ← Back to Dashboard
                    </Link>
                    <Link href={`/analytics/${id}`} className="text-sm text-primary hover:text-primary/80 transition-colors">
                        View Full Analytics
                    </Link>
                </header>

                <section className="glass-panel border border-border/80 rounded-2xl p-6 space-y-5">
                    <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                        <div className="space-y-2">
                            <div className="flex flex-wrap items-center gap-2">
                                <h1 className="text-3xl font-bold tracking-tight">{portfolio.name}</h1>
                                <button
                                    onClick={handleToggleVisibility}
                                    className={`rounded-full border px-2.5 py-1 text-xs font-semibold transition ${portfolio.visibility === 'PUBLIC'
                                        ? 'border-primary/30 bg-primary/10 text-primary'
                                        : 'border-border bg-accent text-muted-foreground'
                                        }`}
                                >
                                    {portfolio.visibility}
                                </button>
                            </div>
                            <div className="flex items-center gap-3">
                                <p className="text-3xl font-bold">{formatUsd(totalValue)}</p>
                                {currentReturn !== undefined && (
                                    <p className={`text-lg font-semibold ${currentReturn >= 0 ? 'text-success' : 'text-destructive'}`}>
                                        {currentReturn >= 0 ? '+' : ''}{currentReturn.toFixed(2)}%
                                    </p>
                                )}
                            </div>
                            <div className="flex flex-wrap gap-2">
                                {PERIODS.map((period) => (
                                    <button
                                        key={period}
                                        onClick={() => setSelectedPeriod(period)}
                                        className={`rounded-lg px-2.5 py-1 text-xs font-semibold transition ${selectedPeriod === period
                                            ? 'bg-primary text-primary-foreground'
                                            : 'bg-accent text-muted-foreground hover:text-foreground'
                                            }`}
                                    >
                                        {period}
                                    </button>
                                ))}
                            </div>
                        </div>

                        <div className="space-y-2 min-w-[260px]">
                            <div className="rounded-xl border border-border bg-background/60 p-3">
                                <p className="text-xs uppercase tracking-wide text-muted-foreground">Cash Balance</p>
                                <p className="mt-1 text-xl font-semibold">{formatUsd(portfolio.balance || 0)}</p>
                            </div>
                            <button
                                onClick={() => setShowDeposit((prev) => !prev)}
                                className="w-full rounded-xl border border-primary/30 bg-primary/10 px-3 py-2 text-sm font-semibold text-primary transition hover:bg-primary/20"
                            >
                                {showDeposit ? 'Close Deposit' : 'Deposit Funds'}
                            </button>
                        </div>
                    </div>

                    <div className="grid gap-3 sm:grid-cols-3">
                        <article className="rounded-xl border border-border bg-background/60 p-4">
                            <p className="text-xs uppercase tracking-wide text-muted-foreground">Live Equity</p>
                            <p className="mt-2 text-lg font-semibold">{formatUsd(liveEquity)}</p>
                        </article>
                        <article className="rounded-xl border border-border bg-background/60 p-4">
                            <p className="text-xs uppercase tracking-wide text-muted-foreground">Max Drawdown</p>
                            <p className="mt-2 text-lg font-semibold text-destructive">
                                {portfolio.maxDrawdown !== undefined ? `-${portfolio.maxDrawdown.toFixed(2)}%` : 'N/A'}
                            </p>
                        </article>
                        <article className="rounded-xl border border-border bg-background/60 p-4">
                            <p className="text-xs uppercase tracking-wide text-muted-foreground">Sharpe Ratio</p>
                            <p className="mt-2 text-lg font-semibold text-secondary">
                                {portfolio.sharpeRatio !== undefined ? portfolio.sharpeRatio.toFixed(2) : 'N/A'}
                            </p>
                        </article>
                    </div>

                    {showDeposit && (
                        <form onSubmit={handleDeposit} className="grid gap-2 sm:grid-cols-[1fr_auto]">
                            <input
                                type="number"
                                value={depositAmount}
                                onChange={(e) => setDepositAmount(e.target.value)}
                                placeholder="Amount"
                                className="rounded-xl border border-border bg-input-background p-3 text-foreground outline-none transition focus:border-primary/50"
                                required
                            />
                            <button
                                type="submit"
                                className="rounded-xl bg-primary px-4 py-3 text-sm font-semibold text-primary-foreground transition hover:opacity-90"
                            >
                                Add Deposit
                            </button>
                        </form>
                    )}
                </section>

                <section className="glass-panel border border-border/80 rounded-2xl p-6">
                    <h2 className="mb-4 text-lg font-semibold">Community Discussion</h2>
                    <LikeCommentWidget targetId={portfolio.id} targetType="PORTFOLIO" />
                </section>

                <section className="glass-panel border border-border/80 rounded-2xl p-6">
                    <div className="mb-4 flex items-end justify-between">
                        <div>
                            <h2 className="text-lg font-semibold">Portfolio Performance</h2>
                            <p className="text-xs text-muted-foreground">Real-time equity snapshots</p>
                        </div>
                        <p className="text-sm font-semibold text-success">{formatUsd(liveEquity)}</p>
                    </div>
                    <div className="h-[300px]">
                        {snapshots.length > 1 ? (
                            <PortfolioChart data={snapshots} />
                        ) : (
                            <div className="h-full flex items-center justify-center text-sm text-muted-foreground">
                                Collecting performance data...
                            </div>
                        )}
                    </div>
                </section>

                <section className="glass-panel border border-border/80 rounded-2xl p-4">
                    <h2 className="mb-3 px-2 text-lg font-semibold">Active Positions</h2>
                    <div className="overflow-x-auto">
                        <table className="w-full min-w-[860px] text-sm">
                            <thead>
                                <tr className="border-b border-border text-muted-foreground">
                                    <th className="p-3 text-left">Asset</th>
                                    <th className="p-3 text-right">Side</th>
                                    <th className="p-3 text-right">Lev</th>
                                    <th className="p-3 text-right">Qty</th>
                                    <th className="p-3 text-right">Avg</th>
                                    <th className="p-3 text-right">Current</th>
                                    <th className="p-3 text-right">Equity</th>
                                    <th className="p-3 text-right">P/L</th>
                                </tr>
                            </thead>
                            <tbody>
                                {portfolio.items?.length === 0 ? (
                                    <tr>
                                        <td colSpan={8} className="p-6 text-center text-muted-foreground">
                                            No open positions
                                        </td>
                                    </tr>
                                ) : (
                                    portfolio.items.map((item) => {
                                        const currentPrice = prices[item.symbol] ?? item.averagePrice;
                                        const margin = (item.quantity * item.averagePrice) / (item.leverage || 1);
                                        const pl =
                                            item.side === 'SHORT'
                                                ? (item.averagePrice - currentPrice) * item.quantity
                                                : (currentPrice - item.averagePrice) * item.quantity;
                                        const plPercent = margin > 0 ? (pl / margin) * 100 : 0;

                                        return (
                                            <tr key={item.id} className="border-b border-border/60 hover:bg-accent/40 transition-colors">
                                                <td className="p-3 font-semibold">{item.symbol}</td>
                                                <td className="p-3 text-right">
                                                    <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${item.side === 'SHORT'
                                                        ? 'bg-destructive/10 text-destructive'
                                                        : 'bg-success/10 text-success'
                                                        }`}>
                                                        {item.side}
                                                    </span>
                                                </td>
                                                <td className="p-3 text-right">{item.leverage || 1}x</td>
                                                <td className="p-3 text-right">{item.quantity}</td>
                                                <td className="p-3 text-right">{formatUsd(item.averagePrice)}</td>
                                                <td className="p-3 text-right">{formatUsd(currentPrice)}</td>
                                                <td className="p-3 text-right">{formatUsd(margin + pl)}</td>
                                                <td className={`p-3 text-right font-semibold ${pl >= 0 ? 'text-success' : 'text-destructive'}`}>
                                                    {pl >= 0 ? '+' : '-'}{formatUsd(Math.abs(pl)).slice(1)}
                                                    {' '}
                                                    ({plPercent.toFixed(2)}%)
                                                </td>
                                            </tr>
                                        );
                                    })
                                )}
                            </tbody>
                        </table>
                    </div>
                </section>

                <section className="glass-panel border border-border/80 rounded-2xl p-4">
                    <h2 className="mb-3 px-2 text-lg font-semibold">Trade History</h2>
                    <div className="overflow-x-auto">
                        <table className="w-full min-w-[860px] text-sm">
                            <thead>
                                <tr className="border-b border-border text-muted-foreground">
                                    <th className="p-3 text-left">Time</th>
                                    <th className="p-3 text-left">Symbol</th>
                                    <th className="p-3 text-right">Action</th>
                                    <th className="p-3 text-right">Side</th>
                                    <th className="p-3 text-right">Qty</th>
                                    <th className="p-3 text-right">Price</th>
                                    <th className="p-3 text-right">Realized P/L</th>
                                </tr>
                            </thead>
                            <tbody>
                                {history.length === 0 ? (
                                    <tr>
                                        <td colSpan={7} className="p-6 text-center text-muted-foreground">
                                            No trade history yet
                                        </td>
                                    </tr>
                                ) : (
                                    history.map((entry) => (
                                        <tr key={entry.id} className="border-b border-border/60 hover:bg-accent/40 transition-colors">
                                            <td className="p-3 text-muted-foreground">{new Date(entry.timestamp).toLocaleString()}</td>
                                            <td className="p-3 font-semibold">{entry.symbol}</td>
                                            <td className="p-3 text-right">{entry.type}</td>
                                            <td className="p-3 text-right">{entry.side}</td>
                                            <td className="p-3 text-right">{entry.quantity}</td>
                                            <td className="p-3 text-right">{formatUsd(entry.price)}</td>
                                            <td className={`p-3 text-right font-semibold ${((entry.realizedPnl ?? 0) >= 0) ? 'text-success' : 'text-destructive'}`}>
                                                {entry.realizedPnl == null
                                                    ? (entry.type === 'BUY' ? '+$0.00' : '-')
                                                    : `${entry.realizedPnl >= 0 ? '+' : '-'}${formatUsd(Math.abs(entry.realizedPnl)).slice(1)}`}
                                            </td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>
                </section>
            </div>
        </div>
    );
}
