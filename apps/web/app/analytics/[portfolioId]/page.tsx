'use client';

import { useState, useEffect, useRef, use, useCallback } from 'react';
import Link from 'next/link';
import { apiFetch, userIdHeaders } from '../../../lib/api-client';

interface AnalyticsData {
    summary: {
        portfolioId: string;
        portfolioName: string;
        visibility: string;
        startingEquity: number;
        currentEquity: number;
        absoluteReturn: number;
        returnPercentage: number;
        peakEquity: number;
        troughEquity: number;
        snapshotCount: number;
        firstSnapshotAt: string | null;
        latestSnapshotAt: string | null;
    };
    riskMetrics: {
        maxDrawdown: number;
        sharpeRatio: number;
        sortinoRatio: number;
        volatility: number;
        profitFactor: number;
    };
    predictionWinRate: number;
    tradeStats: {
        totalTrades: number;
        buyCount: number;
        sellCount: number;
        longCount: number;
        shortCount: number;
        profitableTrades: number;
        losingTrades: number;
        tradeWinRate: number;
        totalPnl: number;
        bestTrade: number;
        worstTrade: number;
        avgWin: number;
        avgLoss: number;
        mostTradedSymbol: string;
        symbolBreakdown: Record<string, number>;
    };
    equityCurve: { timestamp: string; equity: number; drawdown: number; peak: number }[];
}

export default function AnalyticsPage({ params }: { params: Promise<{ portfolioId: string }> }) {
    const resolvedParams = use(params);
    const portfolioId = resolvedParams.portfolioId;
    const [data, setData] = useState<AnalyticsData | null>(null);
    const [loading, setLoading] = useState(true);
    const canvasRef = useRef<HTMLCanvasElement>(null);

    const fetchAnalytics = useCallback(async () => {
        try {
            const userId = localStorage.getItem('userId') || '';
            const res = await apiFetch(`/api/v1/analytics/${portfolioId}`, {
                headers: userIdHeaders(userId)
            });
            if (res.ok) setData(await res.json());
        } catch (err) { console.error(err); }
        finally { setLoading(false); }
    }, [portfolioId]);

    const drawEquityCurve = useCallback(() => {
        const canvas = canvasRef.current;
        if (!canvas || !data?.equityCurve.length) return;

        const ctx = canvas.getContext('2d');
        if (!ctx) return;

        const dpr = window.devicePixelRatio || 1;
        const rect = canvas.getBoundingClientRect();
        canvas.width = rect.width * dpr;
        canvas.height = rect.height * dpr;
        ctx.scale(dpr, dpr);
        const W = rect.width;
        const H = rect.height;

        const curve = data.equityCurve;
        const equities = curve.map(p => p.equity);
        const drawdowns = curve.map(p => p.drawdown);
        const peaks = curve.map(p => p.peak);
        const minEq = Math.min(...equities) * 0.998;
        const maxEq = Math.max(...peaks) * 1.002;

        const padL = 60, padR = 20, padT = 20, padB = 40;
        const plotW = W - padL - padR;
        const plotH = H - padT - padB;

        const xPos = (i: number) => {
            if (curve.length === 1) {
                return padL + plotW / 2;
            }
            return padL + (i / (curve.length - 1)) * plotW;
        };
        const yPos = (v: number) => padT + (1 - (v - minEq) / (maxEq - minEq)) * plotH;

        // Clear
        ctx.clearRect(0, 0, W, H);

        // Grid
        ctx.strokeStyle = 'rgba(255,255,255,0.05)';
        ctx.lineWidth = 1;
        for (let i = 0; i <= 4; i++) {
            const y = padT + (i / 4) * plotH;
            ctx.beginPath(); ctx.moveTo(padL, y); ctx.lineTo(W - padR, y); ctx.stroke();
            ctx.fillStyle = 'rgba(255,255,255,0.3)';
            ctx.font = '10px monospace';
            ctx.textAlign = 'right';
            const val = maxEq - (i / 4) * (maxEq - minEq);
            ctx.fillText(`$${Math.round(val).toLocaleString()}`, padL - 8, y + 3);
        }

        // Drawdown fill (inverted, below equity)
        if (drawdowns.some(d => d > 0)) {
            ctx.beginPath();
            for (let i = 0; i < curve.length; i++) {
                const x = xPos(i);
                const peakY = yPos(peaks[i]);
                if (i === 0) ctx.moveTo(x, peakY);
                else ctx.lineTo(x, peakY);
            }
            for (let i = curve.length - 1; i >= 0; i--) {
                ctx.lineTo(xPos(i), yPos(equities[i]));
            }
            ctx.closePath();
            ctx.fillStyle = 'rgba(239, 68, 68, 0.08)';
            ctx.fill();
        }

        // Equity fill gradient
        ctx.beginPath();
        ctx.moveTo(xPos(0), yPos(equities[0]));
        for (let i = 1; i < curve.length; i++) ctx.lineTo(xPos(i), yPos(equities[i]));
        ctx.lineTo(xPos(curve.length - 1), padT + plotH);
        ctx.lineTo(xPos(0), padT + plotH);
        ctx.closePath();
        const grad = ctx.createLinearGradient(0, padT, 0, padT + plotH);
        grad.addColorStop(0, 'rgba(34, 197, 94, 0.15)');
        grad.addColorStop(1, 'rgba(34, 197, 94, 0)');
        ctx.fillStyle = grad;
        ctx.fill();

        // Equity line
        ctx.beginPath();
        ctx.moveTo(xPos(0), yPos(equities[0]));
        for (let i = 1; i < curve.length; i++) ctx.lineTo(xPos(i), yPos(equities[i]));
        ctx.strokeStyle = '#22c55e';
        ctx.lineWidth = 2;
        ctx.stroke();

        // Peak line (dashed)
        ctx.beginPath();
        ctx.setLineDash([4, 4]);
        ctx.moveTo(xPos(0), yPos(peaks[0]));
        for (let i = 1; i < curve.length; i++) ctx.lineTo(xPos(i), yPos(peaks[i]));
        ctx.strokeStyle = 'rgba(255,255,255,0.15)';
        ctx.lineWidth = 1;
        ctx.stroke();
        ctx.setLineDash([]);

        // X Axis labels (4 evenly spaced)
        ctx.fillStyle = 'rgba(255,255,255,0.3)';
        ctx.font = '9px monospace';
        ctx.textAlign = 'center';
        for (let i = 0; i <= 3; i++) {
            const idx = Math.floor((i / 3) * (curve.length - 1));
            const ts = curve[idx].timestamp;
            const d = new Date(ts);
            ctx.fillText(d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }), xPos(idx), H - 10);
        }
    }, [data]);

    useEffect(() => {
        fetchAnalytics();
    }, [fetchAnalytics]);

    useEffect(() => {
        if (data?.equityCurve && canvasRef.current) {
            drawEquityCurve();
        }
    }, [data, drawEquityCurve]);

    const ratingColor = (value: number, type: 'sharpe' | 'sortino' | 'drawdown' | 'winrate' | 'pf' | 'vol') => {
        switch (type) {
            case 'sharpe':
            case 'sortino':
                if (value >= 2) return 'text-green-400';
                if (value >= 1) return 'text-yellow-400';
                return value >= 0 ? 'text-orange-400' : 'text-red-400';
            case 'drawdown':
                if (value < 5) return 'text-green-400';
                if (value < 15) return 'text-yellow-400';
                return 'text-red-400';
            case 'winrate':
                if (value >= 60) return 'text-green-400';
                if (value >= 40) return 'text-yellow-400';
                return 'text-red-400';
            case 'pf':
                if (value >= 2) return 'text-green-400';
                if (value >= 1) return 'text-yellow-400';
                return 'text-red-400';
            case 'vol':
                if (value < 20) return 'text-green-400';
                if (value < 50) return 'text-yellow-400';
                return 'text-red-400';
            default: return 'text-zinc-300';
        }
    };

    const ratingLabel = (value: number, type: string) => {
        switch (type) {
            case 'sharpe':
            case 'sortino':
                if (value >= 3) return 'Excellent';
                if (value >= 2) return 'Great';
                if (value >= 1) return 'Good';
                if (value >= 0) return 'Moderate';
                return 'Poor';
            case 'drawdown':
                if (value < 5) return 'Low Risk';
                if (value < 15) return 'Moderate';
                return 'High Risk';
            case 'pf':
                if (value >= 3) return 'Excellent';
                if (value >= 2) return 'Good';
                if (value >= 1) return 'Break Even';
                return 'Losing';
            default: return '';
        }
    };

    const formatCurrency = (value: number) =>
        `${value >= 0 ? '+' : '-'}$${Math.abs(value).toLocaleString(undefined, { maximumFractionDigits: 2 })}`;

    const formatEquity = (value: number) =>
        `$${value.toLocaleString(undefined, { maximumFractionDigits: 2 })}`;

    const formatTimestamp = (value: string | null) =>
        value
            ? new Date(value).toLocaleString([], {
                year: 'numeric',
                month: 'short',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit',
            })
            : 'N/A';

    if (loading) {
        return (
            <div className="min-h-screen bg-black flex items-center justify-center">
                <div className="animate-spin w-10 h-10 border-2 border-blue-500 border-t-transparent rounded-full"></div>
            </div>
        );
    }

    if (!data) {
        return (
            <div className="min-h-screen bg-black flex items-center justify-center text-zinc-500">
                <p>No analytics data available for this portfolio.</p>
            </div>
        );
    }

    const rm = data.riskMetrics;
    const ts = data.tradeStats;
    const summary = data.summary ?? {
        portfolioId,
        portfolioName: 'Portfolio',
        visibility: 'PRIVATE',
        startingEquity: 0,
        currentEquity: 0,
        absoluteReturn: 0,
        returnPercentage: 0,
        peakEquity: 0,
        troughEquity: 0,
        snapshotCount: 0,
        firstSnapshotAt: null,
        latestSnapshotAt: null,
    };
    const performancePositive = summary.absoluteReturn >= 0;

    return (
        <div className="min-h-screen bg-black text-white">
            {/* Nav */}
            <nav className="border-b border-white/10 px-6 py-4 flex items-center justify-between backdrop-blur-md bg-black/50 sticky top-0 z-50">
                <Link href="/dashboard" className="font-bold text-xl tracking-tight flex items-center gap-2">
                    <div className="w-8 h-8 bg-green-500 rounded-lg flex items-center justify-center text-black font-bold">P</div>
                    <span>PaperTrade<span className="text-green-500">Pro</span></span>
                </Link>
                <div className="flex gap-4 text-sm font-medium text-zinc-400 items-center">
                    <Link href="/dashboard" className="hover:text-white transition-colors">Dashboard</Link>
                    <Link href="/discover" className="hover:text-white transition-colors">Discover</Link>
                    <Link href="/watchlist" className="hover:text-white transition-colors">Watchlist</Link>
                    <Link href="/tournaments" className="hover:text-white transition-colors">Tournaments</Link>
                </div>
            </nav>

            <div className="max-w-6xl mx-auto px-6 py-10">
                {/* Header */}
                <div className="mb-8">
                    <Link href="/dashboard" className="text-xs text-zinc-600 hover:text-zinc-400 transition-colors mb-2 inline-block">← Back to Dashboard</Link>
                    <div className="flex flex-col gap-3 xl:flex-row xl:items-end xl:justify-between">
                        <div>
                            <p className="text-[11px] uppercase tracking-[0.35em] text-zinc-500">Portfolio Analytics</p>
                            <h1 className="text-3xl font-bold">
                                <span className="text-transparent bg-clip-text bg-gradient-to-r from-blue-400 via-cyan-400 to-teal-400">{summary.portfolioName}</span>
                            </h1>
                            <p className="text-zinc-500 text-sm mt-1">
                                Current equity, return path, trade quality, and risk posture in one view.
                            </p>
                        </div>
                        <div className="flex flex-wrap gap-2 text-xs">
                            <span className="rounded-full border border-emerald-500/20 bg-emerald-500/10 px-3 py-1 text-emerald-300">
                                {summary.visibility}
                            </span>
                            <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-zinc-300">
                                {summary.snapshotCount} snapshots
                            </span>
                            <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-zinc-300">
                                Updated {formatTimestamp(summary.latestSnapshotAt)}
                            </span>
                        </div>
                    </div>
                </div>

                <div className="mb-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                    <div className="rounded-2xl border border-white/10 bg-zinc-900/60 p-5">
                        <p className="text-[10px] uppercase tracking-[0.28em] text-zinc-500">Current Equity</p>
                        <p className="mt-3 text-3xl font-bold text-white">{formatEquity(summary.currentEquity)}</p>
                        <p className="mt-2 text-xs text-zinc-500">Started at {formatEquity(summary.startingEquity)}</p>
                    </div>
                    <div className="rounded-2xl border border-white/10 bg-zinc-900/60 p-5">
                        <p className="text-[10px] uppercase tracking-[0.28em] text-zinc-500">Net Return</p>
                        <p className={`mt-3 text-3xl font-bold ${performancePositive ? 'text-green-400' : 'text-red-400'}`}>
                            {formatCurrency(summary.absoluteReturn)}
                        </p>
                        <p className={`mt-2 text-xs ${performancePositive ? 'text-green-300' : 'text-red-300'}`}>
                            {summary.returnPercentage >= 0 ? '+' : ''}{summary.returnPercentage.toFixed(2)}%
                        </p>
                    </div>
                    <div className="rounded-2xl border border-white/10 bg-zinc-900/60 p-5">
                        <p className="text-[10px] uppercase tracking-[0.28em] text-zinc-500">Range</p>
                        <div className="mt-3 grid grid-cols-2 gap-3 text-sm">
                            <div>
                                <p className="text-zinc-500">Peak</p>
                                <p className="mt-1 font-mono font-bold text-green-300">{formatEquity(summary.peakEquity)}</p>
                            </div>
                            <div>
                                <p className="text-zinc-500">Trough</p>
                                <p className="mt-1 font-mono font-bold text-red-300">{formatEquity(summary.troughEquity)}</p>
                            </div>
                        </div>
                    </div>
                    <div className="rounded-2xl border border-white/10 bg-zinc-900/60 p-5">
                        <p className="text-[10px] uppercase tracking-[0.28em] text-zinc-500">Signal Quality</p>
                        <p className={`mt-3 text-3xl font-bold ${ratingColor(data.predictionWinRate, 'winrate')}`}>
                            {data.predictionWinRate.toFixed(2)}%
                        </p>
                        <p className="mt-2 text-xs text-zinc-500">Resolved analysis hit rate</p>
                    </div>
                </div>

                {/* Equity Curve */}
                <div className="bg-zinc-900/50 border border-zinc-800 rounded-2xl p-6 mb-6">
                    <div className="flex items-center justify-between mb-4">
                        <div>
                            <h2 className="text-sm font-bold text-zinc-300 uppercase tracking-wider">Equity Curve</h2>
                            <p className="text-[10px] text-zinc-600">
                                {formatTimestamp(summary.firstSnapshotAt)} to {formatTimestamp(summary.latestSnapshotAt)} with drawdown overlay.
                            </p>
                        </div>
                        <div className="flex gap-4 text-[10px]">
                            <span className="flex items-center gap-1.5"><span className="w-3 h-0.5 bg-green-500 rounded"></span> Equity</span>
                            <span className="flex items-center gap-1.5"><span className="w-3 h-0.5 bg-white/20 rounded" style={{ borderBottom: '1px dashed rgba(255,255,255,0.3)' }}></span> Peak</span>
                            <span className="flex items-center gap-1.5"><span className="w-3 h-3 bg-red-500/10 rounded"></span> Drawdown</span>
                        </div>
                    </div>
                    <canvas ref={canvasRef} className="w-full h-64 rounded-xl"></canvas>
                </div>

                {/* Risk Metrics Cards */}
                <div className="grid gap-3 mb-6 sm:grid-cols-2 xl:grid-cols-5">
                    {[
                        { label: 'Max Drawdown', value: rm.maxDrawdown, suffix: '%', type: 'drawdown' as const, icon: '📉' },
                        { label: 'Sharpe Ratio', value: rm.sharpeRatio, suffix: '', type: 'sharpe' as const, icon: '⚡' },
                        { label: 'Sortino Ratio', value: rm.sortinoRatio, suffix: '', type: 'sortino' as const, icon: '🎯' },
                        { label: 'Volatility', value: rm.volatility, suffix: '%', type: 'vol' as const, icon: '🌊' },
                        { label: 'Profit Factor', value: rm.profitFactor, suffix: 'x', type: 'pf' as const, icon: '💰' },
                    ].map(metric => (
                        <div key={metric.label} className="bg-zinc-900/50 border border-zinc-800 rounded-xl p-4 hover:border-zinc-700 transition-all group">
                            <div className="flex items-center gap-2 mb-2">
                                <span className="text-lg">{metric.icon}</span>
                                <span className="text-[10px] text-zinc-500 uppercase tracking-wider font-bold">{metric.label}</span>
                            </div>
                            <p className={`text-2xl font-bold font-mono ${ratingColor(metric.value, metric.type)}`}>
                                {metric.value.toFixed(2)}{metric.suffix}
                            </p>
                            <p className="text-[10px] text-zinc-600 mt-1">{ratingLabel(metric.value, metric.type)}</p>
                        </div>
                    ))}
                </div>

                {/* Trade Stats + Win Rate */}
                <div className="grid gap-6 mb-6 xl:grid-cols-12">
                    {/* Trade Stats */}
                    <div className="bg-zinc-900/50 border border-zinc-800 rounded-2xl p-6 xl:col-span-7">
                        <h2 className="text-sm font-bold text-zinc-300 uppercase tracking-wider mb-4">Trade Statistics</h2>

                        <div className="grid gap-4 mb-6 sm:grid-cols-3">
                            <div className="text-center p-3 bg-black/30 rounded-xl">
                                <p className="text-2xl font-bold text-white">{ts.totalTrades}</p>
                                <p className="text-[10px] text-zinc-500 uppercase">Total Trades</p>
                            </div>
                            <div className="text-center p-3 bg-black/30 rounded-xl">
                                <p className={`text-2xl font-bold ${ts.totalPnl >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                                    {formatCurrency(ts.totalPnl)}
                                </p>
                                <p className="text-[10px] text-zinc-500 uppercase">Total PnL</p>
                            </div>
                            <div className="text-center p-3 bg-black/30 rounded-xl">
                                <p className={`text-2xl font-bold ${ratingColor(ts.tradeWinRate, 'winrate')}`}>{ts.tradeWinRate}%</p>
                                <p className="text-[10px] text-zinc-500 uppercase">Win Rate</p>
                            </div>
                        </div>

                        <div className="grid gap-3 text-sm md:grid-cols-2">
                            {[
                                { label: 'Buy Orders', value: ts.buyCount, color: 'text-green-400' },
                                { label: 'Sell Orders', value: ts.sellCount, color: 'text-red-400' },
                                { label: 'Long Positions', value: ts.longCount, color: 'text-emerald-400' },
                                { label: 'Short Positions', value: ts.shortCount, color: 'text-orange-400' },
                                { label: 'Best Trade', value: `+$${ts.bestTrade.toLocaleString()}`, color: 'text-green-400' },
                                { label: 'Worst Trade', value: `$${ts.worstTrade.toLocaleString()}`, color: 'text-red-400' },
                                { label: 'Avg Win', value: `+$${ts.avgWin.toLocaleString()}`, color: 'text-green-400' },
                                { label: 'Avg Loss', value: `-$${ts.avgLoss.toLocaleString()}`, color: 'text-red-400' },
                            ].map(stat => (
                                <div key={stat.label} className="flex justify-between items-center py-2 px-3 bg-black/20 rounded-lg">
                                    <span className="text-zinc-500 text-xs">{stat.label}</span>
                                    <span className={`font-mono font-bold text-xs ${stat.color}`}>{stat.value}</span>
                                </div>
                            ))}
                        </div>

                        <div className="mt-4 pt-4 border-t border-zinc-800">
                            <p className="text-[10px] text-zinc-500 uppercase tracking-wider mb-2">Most Traded</p>
                            <span className="text-sm font-bold text-amber-400 bg-amber-500/10 px-3 py-1 rounded-full border border-amber-500/20">
                                {ts.mostTradedSymbol}
                            </span>
                        </div>
                    </div>

                    {/* Symbol Breakdown & Win/Loss Donut */}
                    <div className="space-y-4 xl:col-span-5">
                        {/* Win/Loss Visual */}
                        <div className="bg-zinc-900/50 border border-zinc-800 rounded-2xl p-6">
                            <h2 className="text-sm font-bold text-zinc-300 uppercase tracking-wider mb-4">Win/Loss Distribution</h2>
                            <div className="flex items-center gap-6">
                                <div className="relative w-24 h-24">
                                    <svg viewBox="0 0 36 36" className="w-24 h-24 transform -rotate-90">
                                        <circle cx="18" cy="18" r="15.91" fill="none" stroke="#27272a" strokeWidth="3" />
                                        <circle cx="18" cy="18" r="15.91" fill="none" stroke="#22c55e" strokeWidth="3"
                                            strokeDasharray={`${ts.tradeWinRate} ${100 - ts.tradeWinRate}`} strokeLinecap="round" />
                                    </svg>
                                    <div className="absolute inset-0 flex items-center justify-center">
                                        <span className="text-lg font-bold text-white">{ts.tradeWinRate}%</span>
                                    </div>
                                </div>
                                <div className="flex-1 space-y-2">
                                    <div className="flex items-center gap-2">
                                        <div className="w-3 h-3 rounded-full bg-green-500"></div>
                                        <span className="text-xs text-zinc-400">Winners</span>
                                        <span className="text-xs font-bold text-green-400 ml-auto">{ts.profitableTrades}</span>
                                    </div>
                                    <div className="flex items-center gap-2">
                                        <div className="w-3 h-3 rounded-full bg-red-500"></div>
                                        <span className="text-xs text-zinc-400">Losers</span>
                                        <span className="text-xs font-bold text-red-400 ml-auto">{ts.losingTrades}</span>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Symbol breakdown */}
                        <div className="bg-zinc-900/50 border border-zinc-800 rounded-2xl p-6">
                            <h2 className="text-sm font-bold text-zinc-300 uppercase tracking-wider mb-3">Symbol Breakdown</h2>
                            <div className="space-y-2">
                                {Object.entries(ts.symbolBreakdown || {})
                                    .sort(([, a], [, b]) => (b as number) - (a as number))
                                    .slice(0, 6)
                                    .map(([symbol, count]) => {
                                        const pct = ts.totalTrades > 0 ? ((count as number) / ts.totalTrades) * 100 : 0;
                                        return (
                                            <div key={symbol}>
                                                <div className="flex justify-between text-xs mb-1">
                                                    <span className="text-zinc-300 font-bold">{symbol}</span>
                                                    <span className="text-zinc-500">{count as number} trades ({pct.toFixed(0)}%)</span>
                                                </div>
                                                <div className="w-full h-1.5 bg-zinc-800 rounded-full overflow-hidden">
                                                    <div
                                                        className="h-full bg-gradient-to-r from-blue-500 to-cyan-400 rounded-full transition-all"
                                                        style={{ width: `${pct}%` }}
                                                    ></div>
                                                </div>
                                            </div>
                                        );
                                    })}
                                {Object.keys(ts.symbolBreakdown || {}).length === 0 && (
                                    <p className="text-xs text-zinc-600 italic">No trades yet</p>
                                )}
                            </div>
                        </div>

                        {/* Prediction Win Rate */}
                        <div className="bg-zinc-900/50 border border-zinc-800 rounded-2xl p-6">
                            <h2 className="text-sm font-bold text-zinc-300 uppercase tracking-wider mb-2">Prediction Accuracy</h2>
                            <p className={`text-3xl font-bold font-mono ${ratingColor(data.predictionWinRate, 'winrate')}`}>
                                {data.predictionWinRate}%
                            </p>
                            <p className="text-[10px] text-zinc-600 mt-1">From resolved analysis posts</p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
