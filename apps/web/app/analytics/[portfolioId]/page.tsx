'use client';

import { useState, useEffect, useRef, use, useCallback, useMemo } from 'react';
import Link from 'next/link';
import { apiFetch, userIdHeaders } from '../../../lib/api-client';
import { extractContent } from '../../../lib/page';

interface PortfolioOption {
    id: string;
    name: string;
    visibility?: 'PUBLIC' | 'PRIVATE';
}

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
    positionSummary?: {
        openPositions: number;
        grossExposure: number;
        realizedPnl: number;
        unrealizedPnl: number;
        netPnl: number;
        topPositions: {
            symbol: string;
            side: string;
            leverage: number;
            quantity: number;
            averagePrice: number;
            currentPrice: number;
            exposure: number;
            unrealizedPnl: number;
        }[];
    };
    riskAttribution?: {
        symbol: string;
        side: string;
        leverage: number;
        quantity: number;
        averagePrice: number;
        currentPrice: number;
        exposure: number;
        exposureShare: number;
        unrealizedPnl: number;
        movePercentage: number;
    }[];
    performanceWindows?: {
        '7d': {
            startingEquity: number;
            endingEquity: number;
            absoluteReturn: number;
            returnPercentage: number;
            snapshotCount: number;
        };
        '30d': {
            startingEquity: number;
            endingEquity: number;
            absoluteReturn: number;
            returnPercentage: number;
            snapshotCount: number;
        };
    };
    periodExtremes?: {
        bestMove: {
            absoluteReturn: number;
            returnPercentage: number;
            from: string | null;
            to: string | null;
        };
        worstMove: {
            absoluteReturn: number;
            returnPercentage: number;
            from: string | null;
            to: string | null;
        };
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
    symbolAttribution?: {
        symbol: string;
        realizedPnl: number;
        tradeCount: number;
    }[];
    symbolMiniTimelines?: {
        symbol: string;
        tradeCount: number;
        realizedTradeCount: number;
        finalRealizedPnl: number;
        points: {
            timestamp: string | null;
            cumulativePnl: number;
        }[];
    }[];
    pnlTimeline?: {
        timestamp: string | null;
        equity: number;
        realizedPnl: number;
        unrealizedPnl: number;
        netPnl: number;
    }[];
    equityCurve: { timestamp: string; equity: number; drawdown: number; peak: number }[];
}

export default function AnalyticsPage({ params }: { params: Promise<{ portfolioId: string }> }) {
    const resolvedParams = use(params);
    const portfolioId = resolvedParams.portfolioId;
    const [data, setData] = useState<AnalyticsData | null>(null);
    const [loading, setLoading] = useState(true);
    const [isMounted, setIsMounted] = useState(false);
    const [portfolioOptions, setPortfolioOptions] = useState<PortfolioOption[]>([]);
    const [comparePortfolioId, setComparePortfolioId] = useState('');
    const [compareData, setCompareData] = useState<AnalyticsData | null>(null);
    const [compareLoading, setCompareLoading] = useState(false);
    const [selectedCurveWindow, setSelectedCurveWindow] = useState<'ALL' | '30D' | '7D'>('ALL');
    const [symbolFilter, setSymbolFilter] = useState('');
    const [selectedSymbolDetail, setSelectedSymbolDetail] = useState('');
    const [compareLinkCopied, setCompareLinkCopied] = useState(false);
    const [compareSummaryCopied, setCompareSummaryCopied] = useState(false);
    const [chartRenderVersion, setChartRenderVersion] = useState(0);
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const pnlCanvasRef = useRef<HTMLCanvasElement>(null);
    const compareCanvasRef = useRef<HTMLCanvasElement>(null);

    const initializeCanvas = useCallback((canvas: HTMLCanvasElement) => {
        const ctx = canvas.getContext('2d');
        if (!ctx) {
            return null;
        }

        const dpr = window.devicePixelRatio || 1;
        const rect = canvas.getBoundingClientRect();
        const width = Math.max(1, rect.width);
        const height = Math.max(1, rect.height);
        canvas.width = width * dpr;
        canvas.height = height * dpr;
        ctx.setTransform(1, 0, 0, 1, 0, 0);
        ctx.scale(dpr, dpr);
        ctx.clearRect(0, 0, width, height);
        return { ctx, width, height };
    }, []);

    const clearCanvas = useCallback((canvas: HTMLCanvasElement | null) => {
        if (!canvas) {
            return;
        }

        initializeCanvas(canvas);
    }, [initializeCanvas]);

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

    const fetchPortfolioOptions = useCallback(async () => {
        try {
            const userId = localStorage.getItem('userId') || '';
            if (!userId) {
                setPortfolioOptions([]);
                return;
            }

            const res = await apiFetch(`/api/v1/portfolios?ownerId=${encodeURIComponent(userId)}&t=${Date.now()}`, {
                headers: userIdHeaders(userId),
                cache: 'no-store',
            });
            if (!res.ok) {
                return;
            }

            const payload = await res.json();
            const portfolios = extractContent<PortfolioOption>(payload).filter((portfolio) => portfolio.id !== portfolioId);
            setPortfolioOptions(portfolios);
        } catch (err) {
            console.error(err);
        }
    }, [portfolioId]);

    const fetchCompareAnalytics = useCallback(async (targetPortfolioId: string) => {
        if (!targetPortfolioId || targetPortfolioId === portfolioId) {
            setCompareData(null);
            setCompareLoading(false);
            return;
        }

        try {
            setCompareLoading(true);
            const userId = localStorage.getItem('userId') || '';
            const res = await apiFetch(`/api/v1/analytics/${targetPortfolioId}`, {
                headers: userIdHeaders(userId),
            });
            if (!res.ok) {
                setCompareData(null);
                return;
            }

            setCompareData(await res.json());
        } catch (err) {
            console.error(err);
            setCompareData(null);
        } finally {
            setCompareLoading(false);
        }
    }, [portfolioId]);

    const filteredEquityCurve = useMemo(() => {
        if (!data?.equityCurve?.length) {
            return [];
        }

        if (selectedCurveWindow === 'ALL') {
            return data.equityCurve;
        }

        const days = selectedCurveWindow === '7D' ? 7 : 30;
        const threshold = Date.now() - days * 24 * 60 * 60 * 1000;
        const filtered = data.equityCurve.filter((point) => new Date(point.timestamp).getTime() >= threshold);
        return filtered.length > 0 ? filtered : data.equityCurve;
    }, [data, selectedCurveWindow]);

    const filteredCompareEquityCurve = useMemo(() => {
        if (!compareData?.equityCurve?.length) {
            return [];
        }

        if (selectedCurveWindow === 'ALL') {
            return compareData.equityCurve;
        }

        const days = selectedCurveWindow === '7D' ? 7 : 30;
        const threshold = Date.now() - days * 24 * 60 * 60 * 1000;
        const filtered = compareData.equityCurve.filter((point) => new Date(point.timestamp).getTime() >= threshold);
        return filtered.length > 0 ? filtered : compareData.equityCurve;
    }, [compareData, selectedCurveWindow]);

    const curveWindowStats = useMemo(() => {
        const totalCurve = data?.equityCurve ?? [];
        const selectedCurve = filteredEquityCurve;

        if (!totalCurve.length || !selectedCurve.length) {
            return {
                totalPointCount: 0,
                selectedPointCount: 0,
                availableHours: 0,
                availableDays: 0,
                isFilteredWindowDistinct: false,
                selectedStart: null as string | null,
                selectedEnd: null as string | null,
            };
        }

        const firstTimestamp = totalCurve[0]?.timestamp;
        const lastTimestamp = totalCurve[totalCurve.length - 1]?.timestamp;
        const availableHours = firstTimestamp && lastTimestamp
            ? Math.max(0, (new Date(lastTimestamp).getTime() - new Date(firstTimestamp).getTime()) / (1000 * 60 * 60))
            : 0;

        return {
            totalPointCount: totalCurve.length,
            selectedPointCount: selectedCurve.length,
            availableHours,
            availableDays: availableHours / 24,
            isFilteredWindowDistinct:
                selectedCurveWindow === 'ALL'
                    ? false
                    : selectedCurve.length < totalCurve.length,
            selectedStart: selectedCurve[0]?.timestamp ?? null,
            selectedEnd: selectedCurve[selectedCurve.length - 1]?.timestamp ?? null,
        };
    }, [data, filteredEquityCurve, selectedCurveWindow]);

    const drawEquityCurve = useCallback(() => {
        const canvas = canvasRef.current;
        if (!canvas || !filteredEquityCurve.length) return;

        const prepared = initializeCanvas(canvas);
        if (!prepared) return;

        const { ctx, width: W, height: H } = prepared;

        const curve = filteredEquityCurve;
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
    }, [filteredEquityCurve, initializeCanvas]);

    const filteredPnlTimeline = useMemo(() => {
        if (!data?.pnlTimeline?.length) {
            return [];
        }

        if (selectedCurveWindow === 'ALL') {
            return data.pnlTimeline;
        }

        const days = selectedCurveWindow === '7D' ? 7 : 30;
        const threshold = Date.now() - days * 24 * 60 * 60 * 1000;
        const filtered = data.pnlTimeline.filter((point) => point.timestamp && new Date(point.timestamp).getTime() >= threshold);
        return filtered.length > 0 ? filtered : data.pnlTimeline;
    }, [data, selectedCurveWindow]);

    const symbolDetailCandidates = useMemo(() => {
        const normalizedFilter = symbolFilter.trim().toUpperCase();
        const symbolMiniTimelines = data?.symbolMiniTimelines ?? [];
        const riskAttribution = data?.riskAttribution ?? [];
        const symbolAttribution = data?.symbolAttribution ?? [];

        const filteredSymbolMiniTimelines = normalizedFilter
            ? symbolMiniTimelines.filter((row) => row.symbol.toUpperCase().includes(normalizedFilter))
            : symbolMiniTimelines;
        const filteredRiskAttribution = normalizedFilter
            ? riskAttribution.filter((row) => row.symbol.toUpperCase().includes(normalizedFilter))
            : riskAttribution;
        const filteredSymbolAttribution = normalizedFilter
            ? symbolAttribution.filter((row) => row.symbol.toUpperCase().includes(normalizedFilter))
            : symbolAttribution;

        return Array.from(new Set([
            ...filteredSymbolMiniTimelines.map((row) => row.symbol),
            ...filteredRiskAttribution.map((row) => row.symbol),
            ...filteredSymbolAttribution.map((row) => row.symbol),
        ]));
    }, [data, symbolFilter]);

    const drawPnlTimeline = useCallback(() => {
        const canvas = pnlCanvasRef.current;
        if (!canvas || !filteredPnlTimeline.length) return;

        const prepared = initializeCanvas(canvas);
        if (!prepared) return;

        const { ctx, width: W, height: H } = prepared;
        const padL = 60, padR = 20, padT = 20, padB = 40;
        const plotW = W - padL - padR;
        const plotH = H - padT - padB;
        const values = filteredPnlTimeline.flatMap((point) => [point.realizedPnl, point.unrealizedPnl, point.netPnl]);
        const minValue = Math.min(...values, 0);
        const maxValue = Math.max(...values, 0);
        const span = maxValue - minValue || 1;

        const xPos = (i: number) => filteredPnlTimeline.length === 1
            ? padL + plotW / 2
            : padL + (i / (filteredPnlTimeline.length - 1)) * plotW;
        const yPos = (value: number) => padT + (1 - ((value - minValue) / span)) * plotH;

        ctx.strokeStyle = 'rgba(255,255,255,0.05)';
        ctx.lineWidth = 1;
        for (let i = 0; i <= 4; i++) {
            const y = padT + (i / 4) * plotH;
            ctx.beginPath();
            ctx.moveTo(padL, y);
            ctx.lineTo(W - padR, y);
            ctx.stroke();
        }

        const series = [
            { key: 'realizedPnl' as const, color: '#38bdf8' },
            { key: 'unrealizedPnl' as const, color: '#f59e0b' },
            { key: 'netPnl' as const, color: '#22c55e' },
        ];

        for (const { key, color } of series) {
            ctx.beginPath();
            filteredPnlTimeline.forEach((point, index) => {
                const x = xPos(index);
                const y = yPos(point[key]);
                if (index === 0) {
                    ctx.moveTo(x, y);
                } else {
                    ctx.lineTo(x, y);
                }
            });
            ctx.strokeStyle = color;
            ctx.lineWidth = 2;
            ctx.stroke();
        }

        ctx.fillStyle = 'rgba(255,255,255,0.3)';
        ctx.font = '9px monospace';
        ctx.textAlign = 'center';
        for (let i = 0; i <= 3; i++) {
            const idx = Math.floor((i / 3) * (filteredPnlTimeline.length - 1));
            const ts = filteredPnlTimeline[idx]?.timestamp;
            if (!ts) continue;
            const d = new Date(ts);
            ctx.fillText(d.toLocaleDateString(), xPos(idx), H - 10);
        }
    }, [filteredPnlTimeline, initializeCanvas]);

    const drawCompareOverlay = useCallback(() => {
        const canvas = compareCanvasRef.current;
        if (!canvas || !filteredEquityCurve.length || !filteredCompareEquityCurve.length) {
            return;
        }

        const prepared = initializeCanvas(canvas);
        if (!prepared) return;

        const { ctx, width: W, height: H } = prepared;
        const padL = 60, padR = 20, padT = 20, padB = 40;
        const plotW = W - padL - padR;
        const plotH = H - padT - padB;

        const toIndexed = (curve: { equity: number }[]) => {
            const base = curve[0]?.equity || 1;
            return curve.map((point) => (base > 0 ? (point.equity / base) * 100 : 100));
        };

        const primaryIndexed = toIndexed(filteredEquityCurve);
        const compareIndexed = toIndexed(filteredCompareEquityCurve);
        const allValues = [...primaryIndexed, ...compareIndexed];
        const minValue = Math.min(...allValues) * 0.995;
        const maxValue = Math.max(...allValues) * 1.005;
        const span = maxValue - minValue || 1;

        const xPos = (index: number, length: number) => {
            if (length === 1) return padL + plotW / 2;
            return padL + (index / (length - 1)) * plotW;
        };
        const yPos = (value: number) => padT + (1 - ((value - minValue) / span)) * plotH;

        ctx.strokeStyle = 'rgba(255,255,255,0.05)';
        ctx.lineWidth = 1;
        for (let i = 0; i <= 4; i++) {
            const y = padT + (i / 4) * plotH;
            ctx.beginPath();
            ctx.moveTo(padL, y);
            ctx.lineTo(W - padR, y);
            ctx.stroke();
            ctx.fillStyle = 'rgba(255,255,255,0.3)';
            ctx.font = '10px monospace';
            ctx.textAlign = 'right';
            const val = maxValue - (i / 4) * (maxValue - minValue);
            ctx.fillText(`${val.toFixed(1)}x`, padL - 8, y + 3);
        }

        const drawSeries = (values: number[], color: string, dashed: boolean) => {
            ctx.beginPath();
            values.forEach((value, index) => {
                const x = xPos(index, values.length);
                const y = yPos(value);
                if (index === 0) {
                    ctx.moveTo(x, y);
                } else {
                    ctx.lineTo(x, y);
                }
            });
            ctx.strokeStyle = color;
            ctx.lineWidth = 2;
            ctx.setLineDash(dashed ? [6, 4] : []);
            ctx.stroke();
            ctx.setLineDash([]);
        };

        drawSeries(primaryIndexed, '#22c55e', false);
        drawSeries(compareIndexed, '#38bdf8', true);

        ctx.fillStyle = 'rgba(255,255,255,0.3)';
        ctx.font = '9px monospace';
        ctx.textAlign = 'center';
        for (let i = 0; i <= 3; i++) {
            const idx = Math.floor((i / 3) * (filteredEquityCurve.length - 1));
            const ts = filteredEquityCurve[idx]?.timestamp;
            if (!ts) continue;
            const d = new Date(ts);
            ctx.fillText(d.toLocaleDateString(), xPos(idx, filteredEquityCurve.length), H - 10);
        }
    }, [filteredCompareEquityCurve, filteredEquityCurve, initializeCanvas]);

    useEffect(() => {
        let frameId: number | null = null;
        const requestRedraw = () => {
            if (frameId !== null) {
                cancelAnimationFrame(frameId);
            }
            frameId = window.requestAnimationFrame(() => {
                setChartRenderVersion((current) => current + 1);
            });
        };

        window.addEventListener('resize', requestRedraw);

        return () => {
            window.removeEventListener('resize', requestRedraw);
            if (frameId !== null) {
                cancelAnimationFrame(frameId);
            }
        };
    }, []);

    useEffect(() => {
        setIsMounted(true);
    }, []);

    useEffect(() => {
        const params = new URLSearchParams(window.location.search);
        const compare = params.get('compare');
        const curveWindow = params.get('curveWindow');
        const filter = params.get('symbolFilter');
        const detail = params.get('detail');

        if (compare) {
            setComparePortfolioId(compare);
        }
        if (curveWindow === 'ALL' || curveWindow === '30D' || curveWindow === '7D') {
            setSelectedCurveWindow(curveWindow);
        }
        if (filter) {
            setSymbolFilter(filter);
        }
        if (detail) {
            setSelectedSymbolDetail(detail.toUpperCase());
        }

    }, []);

    useEffect(() => {
        fetchAnalytics();
    }, [fetchAnalytics]);

    useEffect(() => {
        fetchPortfolioOptions();
    }, [fetchPortfolioOptions]);

    useEffect(() => {
        if (filteredEquityCurve.length && canvasRef.current) {
            drawEquityCurve();
            return;
        }

        clearCanvas(canvasRef.current);
    }, [chartRenderVersion, clearCanvas, drawEquityCurve, filteredEquityCurve]);

    useEffect(() => {
        if (filteredPnlTimeline.length && pnlCanvasRef.current) {
            drawPnlTimeline();
            return;
        }

        clearCanvas(pnlCanvasRef.current);
    }, [chartRenderVersion, clearCanvas, drawPnlTimeline, filteredPnlTimeline]);

    useEffect(() => {
        if (filteredEquityCurve.length && filteredCompareEquityCurve.length && compareCanvasRef.current) {
            drawCompareOverlay();
            return;
        }

        clearCanvas(compareCanvasRef.current);
    }, [chartRenderVersion, clearCanvas, drawCompareOverlay, filteredCompareEquityCurve, filteredEquityCurve]);

    useEffect(() => {
        if (symbolDetailCandidates.length === 0) {
            if (selectedSymbolDetail) {
                setSelectedSymbolDetail('');
            }
            return;
        }

        if (!selectedSymbolDetail || !symbolDetailCandidates.includes(selectedSymbolDetail)) {
            setSelectedSymbolDetail(symbolDetailCandidates[0]);
        }
    }, [selectedSymbolDetail, symbolDetailCandidates]);

    useEffect(() => {
        void fetchCompareAnalytics(comparePortfolioId);
    }, [comparePortfolioId, fetchCompareAnalytics]);

    useEffect(() => {
        setCompareLinkCopied(false);
        setCompareSummaryCopied(false);
    }, [comparePortfolioId, selectedCurveWindow, symbolFilter, selectedSymbolDetail]);

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

    const buildMiniSparklinePath = (points: { cumulativePnl: number }[]) => {
        if (points.length === 0) {
            return '';
        }
        if (points.length === 1) {
            return 'M 0 22 L 100 22';
        }

        const values = points.map((point) => point.cumulativePnl);
        const minValue = Math.min(...values);
        const maxValue = Math.max(...values);
        const span = maxValue - minValue || 1;

        return points.map((point, index) => {
            const x = (index / (points.length - 1)) * 100;
            const y = 44 - (((point.cumulativePnl - minValue) / span) * 44);
            return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`;
        }).join(' ');
    };

    const buildDetailTimelineVisuals = (
        points: { cumulativePnl: number }[],
        currentUnrealized: number,
    ) => {
        const chartWidth = 280;
        const chartHeight = 96;
        const baselineValue = points.length ? points[points.length - 1].cumulativePnl : 0;
        const values = [...points.map((point) => point.cumulativePnl), currentUnrealized, baselineValue, 0];
        const minValue = Math.min(...values);
        const maxValue = Math.max(...values);
        const span = maxValue - minValue || 1;
        const yForValue = (value: number) => chartHeight - (((value - minValue) / span) * chartHeight);

        const realizedPath = points.length === 1
            ? `M 0 ${yForValue(points[0].cumulativePnl).toFixed(2)} L ${chartWidth} ${yForValue(points[0].cumulativePnl).toFixed(2)}`
            : points.length === 0
                ? `M 0 ${yForValue(0).toFixed(2)} L ${chartWidth} ${yForValue(0).toFixed(2)}`
            : points.map((point, index) => {
                const x = (index / (points.length - 1)) * chartWidth;
                const y = yForValue(point.cumulativePnl);
                return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`;
            }).join(' ');

        const baselineY = yForValue(baselineValue);
        const unrealizedY = yForValue(currentUnrealized);
        const liveConnectorPath = `M ${chartWidth - 36} ${baselineY.toFixed(2)} L ${chartWidth - 36} ${unrealizedY.toFixed(2)} L ${chartWidth} ${unrealizedY.toFixed(2)}`;
        const unrealizedDelta = currentUnrealized - baselineValue;

        return {
            realizedPath,
            baselineY,
            unrealizedY,
            liveConnectorPath,
            unrealizedDelta,
            hasRealizedPoints: points.length > 0,
        };
    };

    const exposurePalette = ['#22c55e', '#06b6d4', '#f59e0b', '#f97316', '#a855f7', '#ef4444', '#84cc16', '#3b82f6'];

    const downloadBlob = (blob: Blob, filename: string) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        link.click();
        URL.revokeObjectURL(url);
    };

    const copyAnalyticsShareLink = async () => {
        try {
            const params = new URLSearchParams();
            if (comparePortfolioId) {
                params.set('compare', comparePortfolioId);
            }
            if (selectedCurveWindow !== 'ALL') {
                params.set('curveWindow', selectedCurveWindow);
            }
            if (symbolFilter.trim()) {
                params.set('symbolFilter', symbolFilter.trim().toUpperCase());
            }
            if (selectedSymbolDetail) {
                params.set('detail', selectedSymbolDetail);
            }

            const shareUrl = `${window.location.origin}${window.location.pathname}${params.toString() ? `?${params.toString()}` : ''}`;
            await navigator.clipboard.writeText(shareUrl);
            setCompareLinkCopied(true);
            window.setTimeout(() => setCompareLinkCopied(false), 1800);
        } catch (error) {
            console.error(error);
        }
    };

    if (!isMounted || loading) {
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
    const positionSummary = data.positionSummary ?? {
        openPositions: 0,
        grossExposure: 0,
        realizedPnl: 0,
        unrealizedPnl: 0,
        netPnl: 0,
        topPositions: [],
    };
    const performanceWindows = data.performanceWindows ?? {
        '7d': { startingEquity: 0, endingEquity: 0, absoluteReturn: 0, returnPercentage: 0, snapshotCount: 0 },
        '30d': { startingEquity: 0, endingEquity: 0, absoluteReturn: 0, returnPercentage: 0, snapshotCount: 0 },
    };
    const periodExtremes = data.periodExtremes ?? {
        bestMove: { absoluteReturn: 0, returnPercentage: 0, from: null, to: null },
        worstMove: { absoluteReturn: 0, returnPercentage: 0, from: null, to: null },
    };
    const symbolAttribution = data.symbolAttribution ?? [];
    const symbolMiniTimelines = data.symbolMiniTimelines ?? [];
    const riskAttribution = data.riskAttribution ?? [];
    const pnlTimeline = data.pnlTimeline ?? [];
    const performancePositive = summary.absoluteReturn >= 0;
    const normalizedSymbolFilter = symbolFilter.trim().toUpperCase();
    const filteredTopPositions = normalizedSymbolFilter
        ? positionSummary.topPositions.filter((position) => position.symbol.toUpperCase().includes(normalizedSymbolFilter))
        : positionSummary.topPositions;
    const filteredSymbolAttribution = normalizedSymbolFilter
        ? symbolAttribution.filter((row) => row.symbol.toUpperCase().includes(normalizedSymbolFilter))
        : symbolAttribution;
    const filteredSymbolMiniTimelines = normalizedSymbolFilter
        ? symbolMiniTimelines.filter((row) => row.symbol.toUpperCase().includes(normalizedSymbolFilter))
        : symbolMiniTimelines;
    const filteredRiskAttribution = normalizedSymbolFilter
        ? riskAttribution.filter((row) => row.symbol.toUpperCase().includes(normalizedSymbolFilter))
        : riskAttribution;
    const filteredSymbolBreakdownEntries = Object.entries(ts.symbolBreakdown || {})
        .filter(([symbol]) => !normalizedSymbolFilter || symbol.toUpperCase().includes(normalizedSymbolFilter))
        .sort(([, a], [, b]) => (b as number) - (a as number))
        .slice(0, 6);
    const selectedSymbolTimeline = filteredSymbolMiniTimelines.find((row) => row.symbol === selectedSymbolDetail) ?? null;
    const selectedSymbolRisk = filteredRiskAttribution.find((row) => row.symbol === selectedSymbolDetail) ?? null;
    const selectedSymbolAttribution = filteredSymbolAttribution.find((row) => row.symbol === selectedSymbolDetail) ?? null;
    const selectedSymbolVisuals = buildDetailTimelineVisuals(
        selectedSymbolTimeline?.points ?? [],
        selectedSymbolRisk?.unrealizedPnl ?? 0,
    );
    const compareSummary = compareData?.summary ?? null;
    const compareRiskMetrics = compareData?.riskMetrics ?? null;
    const compareTradeStats = compareData?.tradeStats ?? null;
    const comparePerformanceWindows = compareData?.performanceWindows ?? {
        '7d': { startingEquity: 0, endingEquity: 0, absoluteReturn: 0, returnPercentage: 0, snapshotCount: 0 },
        '30d': { startingEquity: 0, endingEquity: 0, absoluteReturn: 0, returnPercentage: 0, snapshotCount: 0 },
    };
    const selectedComparePortfolio = portfolioOptions.find((portfolio) => portfolio.id === comparePortfolioId) ?? null;

    const copyCompareSummary = async () => {
        if (!compareSummary || !compareRiskMetrics || !compareTradeStats) {
            return;
        }

        const compareName = selectedComparePortfolio?.name ?? compareSummary.portfolioName;
        const summaryLines = [
            `Portfolio Compare Summary`,
            `${summary.portfolioName} vs ${compareName}`,
            `Window: ${selectedCurveWindow}`,
            `Return delta: ${(summary.returnPercentage - compareSummary.returnPercentage) >= 0 ? '+' : ''}${(summary.returnPercentage - compareSummary.returnPercentage).toFixed(2)} pts`,
            `Equity delta: ${formatCurrency(summary.currentEquity - compareSummary.currentEquity)}`,
            `Drawdown delta: ${(rm.maxDrawdown - compareRiskMetrics.maxDrawdown) >= 0 ? '+' : ''}${(rm.maxDrawdown - compareRiskMetrics.maxDrawdown).toFixed(2)} pts`,
            `Win-rate delta: ${(ts.tradeWinRate - compareTradeStats.tradeWinRate) >= 0 ? '+' : ''}${(ts.tradeWinRate - compareTradeStats.tradeWinRate).toFixed(2)} pts`,
            `7D return delta: ${(performanceWindows['7d'].returnPercentage - comparePerformanceWindows['7d'].returnPercentage) >= 0 ? '+' : ''}${(performanceWindows['7d'].returnPercentage - comparePerformanceWindows['7d'].returnPercentage).toFixed(2)} pts`,
            `30D return delta: ${(performanceWindows['30d'].returnPercentage - comparePerformanceWindows['30d'].returnPercentage) >= 0 ? '+' : ''}${(performanceWindows['30d'].returnPercentage - comparePerformanceWindows['30d'].returnPercentage).toFixed(2)} pts`,
            `Sharpe: ${rm.sharpeRatio.toFixed(2)} vs ${compareRiskMetrics.sharpeRatio.toFixed(2)}`,
            `Sortino: ${rm.sortinoRatio.toFixed(2)} vs ${compareRiskMetrics.sortinoRatio.toFixed(2)}`,
            `Profit factor: ${rm.profitFactor.toFixed(2)} vs ${compareRiskMetrics.profitFactor.toFixed(2)}`,
            normalizedSymbolFilter ? `Symbol filter: ${normalizedSymbolFilter}` : null,
            selectedSymbolDetail ? `Detail symbol: ${selectedSymbolDetail}` : null,
        ].filter(Boolean);

        try {
            await navigator.clipboard.writeText(summaryLines.join('\n'));
            setCompareSummaryCopied(true);
            window.setTimeout(() => setCompareSummaryCopied(false), 1800);
        } catch (error) {
            console.error(error);
        }
    };

    const exportAnalytics = async (format: 'csv' | 'json') => {
        try {
            const userId = localStorage.getItem('userId') || '';
            const searchParams = new URLSearchParams({
                format,
                curveWindow: selectedCurveWindow,
            });
            if (normalizedSymbolFilter) {
                searchParams.set('symbolFilter', normalizedSymbolFilter);
            }

            const response = await apiFetch(`/api/v1/analytics/${portfolioId}/export?${searchParams.toString()}`, {
                headers: userIdHeaders(userId),
            });
            if (!response.ok) {
                throw new Error(`Analytics export failed with status ${response.status}`);
            }

            const blob = await response.blob();
            downloadBlob(blob, `${summary.portfolioName.replace(/\s+/g, '-').toLowerCase()}-analytics.${format}`);
        } catch (error) {
            console.error(error);
        }
    };

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

                <div className="mb-6 grid gap-6 xl:grid-cols-12">
                    <div className="rounded-2xl border border-white/10 bg-zinc-900/60 p-6 xl:col-span-7">
                        <h2 className="text-sm font-bold uppercase tracking-wider text-zinc-300">Analytics Controls</h2>
                        <p className="mt-1 text-[10px] text-zinc-600">Filter symbol-facing blocks and export the current analytics snapshot.</p>
                        <div className="mt-5 grid gap-4 md:grid-cols-[minmax(0,1fr)_auto_auto]">
                            <input
                                value={symbolFilter}
                                onChange={(event) => setSymbolFilter(event.target.value)}
                                placeholder="Filter symbols: BTC, ETH, THYAO..."
                                className="rounded-xl border border-white/10 bg-black/20 px-4 py-3 text-sm text-white outline-none transition-colors placeholder:text-zinc-600 focus:border-cyan-500/40"
                            />
                            <button
                                type="button"
                                onClick={() => void exportAnalytics('csv')}
                                className="rounded-xl border border-emerald-500/20 bg-emerald-500/10 px-4 py-3 text-sm font-medium text-emerald-300 transition-colors hover:bg-emerald-500/20"
                            >
                                Export CSV
                            </button>
                            <button
                                type="button"
                                onClick={() => void exportAnalytics('json')}
                                className="rounded-xl border border-blue-500/20 bg-blue-500/10 px-4 py-3 text-sm font-medium text-blue-300 transition-colors hover:bg-blue-500/20"
                            >
                                Export JSON
                            </button>
                        </div>
                    </div>
                    <div className="rounded-2xl border border-white/10 bg-zinc-900/60 p-6 xl:col-span-5">
                        <h2 className="text-sm font-bold uppercase tracking-wider text-zinc-300">Curve Window</h2>
                        <p className="mt-1 text-[10px] text-zinc-600">Limit the equity chart to the window you want to inspect.</p>
                        <div className="mt-5 flex flex-wrap gap-2">
                            {(['ALL', '30D', '7D'] as const).map((windowOption) => (
                                <button
                                    key={windowOption}
                                    type="button"
                                    onClick={() => setSelectedCurveWindow(windowOption)}
                                    className={`rounded-full border px-4 py-2 text-xs font-bold tracking-[0.24em] transition-colors ${
                                        selectedCurveWindow === windowOption
                                            ? 'border-cyan-500/40 bg-cyan-500/15 text-cyan-300'
                                            : 'border-white/10 bg-white/5 text-zinc-400 hover:text-white'
                                    }`}
                                >
                                    {windowOption}
                                </button>
                            ))}
                        </div>
                        <div className="mt-4 space-y-1 text-[11px] text-zinc-500">
                            <p>
                                Available history: {curveWindowStats.availableDays >= 2
                                    ? `${curveWindowStats.availableDays.toFixed(1)} days`
                                    : `${Math.round(curveWindowStats.availableHours)} hours`}
                                {' '}across {curveWindowStats.totalPointCount} snapshots.
                            </p>
                            <p>
                                Current view: {curveWindowStats.selectedPointCount}/{curveWindowStats.totalPointCount} plotted points.
                            </p>
                            {selectedCurveWindow !== 'ALL' && !curveWindowStats.isFilteredWindowDistinct && curveWindowStats.totalPointCount > 0 ? (
                                <p className="text-amber-300">
                                    Current account history is shorter than {selectedCurveWindow}, so this view matches full history.
                                </p>
                            ) : null}
                        </div>
                    </div>
                </div>

                <div className="mb-6 rounded-2xl border border-white/10 bg-zinc-900/60 p-6">
                    <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
                        <div>
                            <h2 className="text-sm font-bold uppercase tracking-wider text-zinc-300">Portfolio Compare</h2>
                            <p className="mt-1 text-[10px] text-zinc-600">Load a second portfolio and compare core outcome, risk, and trade-quality metrics side by side.</p>
                            <div className="mt-3 flex flex-wrap gap-2 text-[10px] text-zinc-500">
                                <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1">
                                    Window {selectedCurveWindow}
                                </span>
                                {normalizedSymbolFilter ? (
                                    <span className="rounded-full border border-cyan-500/20 bg-cyan-500/10 px-3 py-1 text-cyan-300">
                                        Filter {normalizedSymbolFilter}
                                    </span>
                                ) : null}
                                {selectedSymbolDetail ? (
                                    <span className="rounded-full border border-emerald-500/20 bg-emerald-500/10 px-3 py-1 text-emerald-300">
                                        Detail {selectedSymbolDetail}
                                    </span>
                                ) : null}
                            </div>
                        </div>
                        <div className="flex w-full flex-col gap-3 sm:flex-row xl:max-w-xl">
                            <select
                                value={comparePortfolioId}
                                onChange={(event) => setComparePortfolioId(event.target.value)}
                                disabled={portfolioOptions.length === 0}
                                className="w-full rounded-xl border border-white/10 bg-black/20 px-4 py-3 text-sm text-white outline-none transition-colors focus:border-cyan-500/40 disabled:cursor-not-allowed disabled:opacity-50"
                            >
                                <option value="">{portfolioOptions.length === 0 ? 'No other portfolios available' : 'No comparison'}</option>
                                {portfolioOptions.map((portfolio) => (
                                    <option key={portfolio.id} value={portfolio.id}>
                                        {portfolio.name} {portfolio.visibility ? `(${portfolio.visibility})` : ''}
                                    </option>
                                ))}
                            </select>
                            {comparePortfolioId ? (
                                <>
                                    <button
                                        type="button"
                                        onClick={() => void copyCompareSummary()}
                                        disabled={compareLoading || !compareSummary || !compareRiskMetrics || !compareTradeStats}
                                        className="rounded-xl border border-emerald-500/20 bg-emerald-500/10 px-4 py-3 text-sm font-medium text-emerald-300 transition-colors hover:bg-emerald-500/20 disabled:cursor-not-allowed disabled:opacity-50"
                                    >
                                        {compareSummaryCopied ? 'Summary Copied' : 'Copy Summary'}
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => void copyAnalyticsShareLink()}
                                        disabled={compareLoading}
                                        className="rounded-xl border border-cyan-500/20 bg-cyan-500/10 px-4 py-3 text-sm font-medium text-cyan-300 transition-colors hover:bg-cyan-500/20 disabled:cursor-not-allowed disabled:opacity-50"
                                    >
                                        {compareLinkCopied ? 'Link Copied' : 'Copy Compare Link'}
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => {
                                            setComparePortfolioId('');
                                            setCompareData(null);
                                        }}
                                        className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-sm font-medium text-zinc-300 transition-colors hover:text-white"
                                    >
                                        Clear
                                    </button>
                                </>
                            ) : null}
                        </div>
                    </div>
                    {comparePortfolioId ? (
                        compareLoading ? (
                            <div className="mt-5 space-y-4">
                                <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                                    {Array.from({ length: 4 }).map((_, index) => (
                                        <div key={index} className="rounded-xl border border-white/5 bg-black/20 p-4">
                                            <div className="h-3 w-24 animate-pulse rounded bg-white/10"></div>
                                            <div className="mt-4 h-8 w-28 animate-pulse rounded bg-white/10"></div>
                                            <div className="mt-3 h-3 w-32 animate-pulse rounded bg-white/5"></div>
                                        </div>
                                    ))}
                                </div>
                                <div className="rounded-xl border border-white/5 bg-black/20 p-4">
                                    <div className="h-3 w-32 animate-pulse rounded bg-white/10"></div>
                                    <div className="mt-3 h-3 w-56 animate-pulse rounded bg-white/5"></div>
                                    <div className="mt-4 h-64 animate-pulse rounded-xl bg-white/5"></div>
                                </div>
                            </div>
                        ) : compareSummary && compareRiskMetrics && compareTradeStats ? (
                            <div className="mt-5 space-y-4">
                                <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                                    {[
                                        {
                                            label: 'Return Delta',
                                            value: summary.returnPercentage - compareSummary.returnPercentage,
                                            suffix: '%',
                                            baseline: `${summary.portfolioName} vs ${selectedComparePortfolio?.name ?? compareSummary.portfolioName}`,
                                        },
                                        {
                                            label: 'Equity Delta',
                                            value: summary.currentEquity - compareSummary.currentEquity,
                                            prefix: '$',
                                            baseline: `${formatEquity(summary.currentEquity)} vs ${formatEquity(compareSummary.currentEquity)}`,
                                        },
                                        {
                                            label: 'Drawdown Delta',
                                            value: rm.maxDrawdown - compareRiskMetrics.maxDrawdown,
                                            suffix: ' pts',
                                            baseline: `${rm.maxDrawdown.toFixed(2)} vs ${compareRiskMetrics.maxDrawdown.toFixed(2)}`,
                                        },
                                        {
                                            label: 'Trade Win Rate Delta',
                                            value: ts.tradeWinRate - compareTradeStats.tradeWinRate,
                                            suffix: ' pts',
                                            baseline: `${ts.tradeWinRate.toFixed(2)} vs ${compareTradeStats.tradeWinRate.toFixed(2)}`,
                                        },
                                    ].map((metric) => {
                                        const positive = metric.value >= 0;
                                        return (
                                            <div key={metric.label} className="rounded-xl border border-white/5 bg-black/20 p-4">
                                                <p className="text-[10px] uppercase tracking-[0.25em] text-zinc-500">{metric.label}</p>
                                                <p className={`mt-2 text-2xl font-bold font-mono ${positive ? 'text-green-400' : 'text-red-400'}`}>
                                                    {metric.prefix ?? ''}{metric.value >= 0 ? '+' : ''}{metric.value.toFixed(2)}{metric.suffix ?? ''}
                                                </p>
                                                <p className="mt-2 text-xs text-zinc-500">{metric.baseline}</p>
                                            </div>
                                        );
                                    })}
                                </div>
                                <div className="rounded-xl border border-white/5 bg-black/20 p-4">
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <p className="text-[10px] uppercase tracking-[0.25em] text-zinc-500">Indexed Equity Overlay</p>
                                            <p className="mt-1 text-xs text-zinc-500">Both curves are rebased to 100 for fair relative comparison under the selected window.</p>
                                        </div>
                                        <div className="flex gap-4 text-[10px] text-zinc-400">
                                            <span className="flex items-center gap-1.5">
                                                <span className="h-0.5 w-3 rounded bg-green-500"></span>
                                                {summary.portfolioName}
                                            </span>
                                            <span className="flex items-center gap-1.5">
                                                <span className="h-0.5 w-3 rounded bg-sky-400" style={{ borderBottom: '1px dashed rgba(56,189,248,0.8)' }}></span>
                                                {selectedComparePortfolio?.name ?? compareSummary.portfolioName}
                                            </span>
                                        </div>
                                    </div>
                                    <canvas ref={compareCanvasRef} className="mt-4 h-64 w-full rounded-xl" />
                                </div>
                                <div className="rounded-xl border border-white/5 bg-black/20 p-4">
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <p className="text-[10px] uppercase tracking-[0.25em] text-zinc-500">Rolling Delta Strip</p>
                                            <p className="mt-1 text-xs text-zinc-500">Recent-window momentum gap between the two portfolios.</p>
                                        </div>
                                    </div>
                                    <div className="mt-4 grid gap-4 md:grid-cols-2">
                                        {([
                                            {
                                                label: '7D Momentum Delta',
                                                primary: performanceWindows['7d'],
                                                compare: comparePerformanceWindows['7d'],
                                            },
                                            {
                                                label: '30D Momentum Delta',
                                                primary: performanceWindows['30d'],
                                                compare: comparePerformanceWindows['30d'],
                                            },
                                        ] as const).map((windowMetric) => {
                                            const delta = windowMetric.primary.returnPercentage - windowMetric.compare.returnPercentage;
                                            const positive = delta >= 0;
                                            return (
                                                <div key={windowMetric.label} className="rounded-xl border border-white/5 bg-zinc-950/70 p-4">
                                                    <div className="flex items-center justify-between">
                                                        <p className="text-[10px] uppercase tracking-[0.25em] text-zinc-500">{windowMetric.label}</p>
                                                        <span className="text-[10px] text-zinc-600">
                                                            {windowMetric.primary.snapshotCount} vs {windowMetric.compare.snapshotCount} snapshots
                                                        </span>
                                                    </div>
                                                    <p className={`mt-3 text-2xl font-bold font-mono ${positive ? 'text-green-400' : 'text-red-400'}`}>
                                                        {delta >= 0 ? '+' : ''}{delta.toFixed(2)} pts
                                                    </p>
                                                    <div className="mt-3 grid grid-cols-2 gap-3 text-xs text-zinc-500">
                                                        <div>
                                                            <p className="text-zinc-600">{summary.portfolioName}</p>
                                                            <p className="mt-1 font-mono text-zinc-200">
                                                                {windowMetric.primary.returnPercentage >= 0 ? '+' : ''}{windowMetric.primary.returnPercentage.toFixed(2)}%
                                                            </p>
                                                            <p className="mt-1 font-mono text-zinc-500">{formatCurrency(windowMetric.primary.absoluteReturn)}</p>
                                                        </div>
                                                        <div>
                                                            <p className="text-zinc-600">{selectedComparePortfolio?.name ?? compareSummary.portfolioName}</p>
                                                            <p className="mt-1 font-mono text-zinc-200">
                                                                {windowMetric.compare.returnPercentage >= 0 ? '+' : ''}{windowMetric.compare.returnPercentage.toFixed(2)}%
                                                            </p>
                                                            <p className="mt-1 font-mono text-zinc-500">{formatCurrency(windowMetric.compare.absoluteReturn)}</p>
                                                        </div>
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                </div>
                                <div className="rounded-xl border border-white/5 bg-black/20 p-4">
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <p className="text-[10px] uppercase tracking-[0.25em] text-zinc-500">Risk And Quality Compare</p>
                                            <p className="mt-1 text-xs text-zinc-500">Direct metric table for path quality, downside control, and trading efficiency.</p>
                                        </div>
                                    </div>
                                    <div className="mt-4 overflow-x-auto">
                                        <table className="min-w-full text-left text-sm">
                                            <thead>
                                                <tr className="border-b border-white/5 text-[10px] uppercase tracking-[0.22em] text-zinc-500">
                                                    <th className="pb-3 pr-4 font-medium">Metric</th>
                                                    <th className="pb-3 pr-4 font-medium text-zinc-300">{summary.portfolioName}</th>
                                                    <th className="pb-3 pr-4 font-medium text-zinc-300">{selectedComparePortfolio?.name ?? compareSummary.portfolioName}</th>
                                                    <th className="pb-3 font-medium">Delta</th>
                                                </tr>
                                            </thead>
                                            <tbody className="divide-y divide-white/5">
                                                {[
                                                    {
                                                        label: 'Sharpe',
                                                        primary: rm.sharpeRatio,
                                                        compare: compareRiskMetrics.sharpeRatio,
                                                        type: 'sharpe' as const,
                                                        format: (value: number) => value.toFixed(2),
                                                    },
                                                    {
                                                        label: 'Sortino',
                                                        primary: rm.sortinoRatio,
                                                        compare: compareRiskMetrics.sortinoRatio,
                                                        type: 'sortino' as const,
                                                        format: (value: number) => value.toFixed(2),
                                                    },
                                                    {
                                                        label: 'Max Drawdown',
                                                        primary: rm.maxDrawdown,
                                                        compare: compareRiskMetrics.maxDrawdown,
                                                        type: 'drawdown' as const,
                                                        format: (value: number) => `${value.toFixed(2)}%`,
                                                    },
                                                    {
                                                        label: 'Volatility',
                                                        primary: rm.volatility,
                                                        compare: compareRiskMetrics.volatility,
                                                        type: 'vol' as const,
                                                        format: (value: number) => `${value.toFixed(2)}%`,
                                                    },
                                                    {
                                                        label: 'Profit Factor',
                                                        primary: rm.profitFactor,
                                                        compare: compareRiskMetrics.profitFactor,
                                                        type: 'pf' as const,
                                                        format: (value: number) => value.toFixed(2),
                                                    },
                                                    {
                                                        label: 'Trade Win Rate',
                                                        primary: ts.tradeWinRate,
                                                        compare: compareTradeStats.tradeWinRate,
                                                        type: 'winrate' as const,
                                                        format: (value: number) => `${value.toFixed(2)}%`,
                                                    },
                                                ].map((metric) => {
                                                    const delta = metric.primary - metric.compare;
                                                    const positive = metric.type === 'drawdown' || metric.type === 'vol'
                                                        ? delta <= 0
                                                        : delta >= 0;
                                                    return (
                                                        <tr key={metric.label}>
                                                            <td className="py-3 pr-4 text-zinc-400">{metric.label}</td>
                                                            <td className={`py-3 pr-4 font-mono ${ratingColor(metric.primary, metric.type)}`}>
                                                                {metric.format(metric.primary)}
                                                            </td>
                                                            <td className={`py-3 pr-4 font-mono ${ratingColor(metric.compare, metric.type)}`}>
                                                                {metric.format(metric.compare)}
                                                            </td>
                                                            <td className={`py-3 font-mono ${positive ? 'text-green-400' : 'text-red-400'}`}>
                                                                {delta >= 0 ? '+' : ''}{metric.type === 'drawdown' || metric.type === 'vol' || metric.type === 'winrate' ? `${delta.toFixed(2)} pts` : delta.toFixed(2)}
                                                            </td>
                                                        </tr>
                                                    );
                                                })}
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            </div>
                        ) : (
                            <p className="mt-5 rounded-xl border border-dashed border-white/10 bg-black/20 px-4 py-6 text-sm text-zinc-500">
                                Comparison analytics could not be loaded for the selected portfolio.
                            </p>
                        )
                    ) : portfolioOptions.length === 0 ? (
                        <p className="mt-5 rounded-xl border border-dashed border-white/10 bg-black/20 px-4 py-6 text-sm text-zinc-500">
                            Create another portfolio first. Compare mode needs at least two portfolios owned by the same account.
                        </p>
                    ) : (
                        <p className="mt-5 rounded-xl border border-dashed border-white/10 bg-black/20 px-4 py-6 text-sm text-zinc-500">
                            Pick one of your other portfolios to compare against this analytics surface.
                        </p>
                    )}
                </div>

                <div className="mb-6 grid gap-6 xl:grid-cols-12">
                    <div className="rounded-2xl border border-white/10 bg-zinc-900/60 p-6 xl:col-span-5">
                        <div className="flex items-center justify-between">
                            <div>
                                <h2 className="text-sm font-bold uppercase tracking-wider text-zinc-300">Position Summary</h2>
                                <p className="mt-1 text-[10px] text-zinc-600">Live open-risk footprint built from current holdings.</p>
                            </div>
                            <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs text-zinc-300">
                                {positionSummary.openPositions} open
                            </span>
                        </div>
                        <div className="mt-5 grid gap-3 sm:grid-cols-2">
                            {[
                                { label: 'Realized PnL', value: positionSummary.realizedPnl },
                                { label: 'Unrealized PnL', value: positionSummary.unrealizedPnl },
                                { label: 'Net PnL', value: positionSummary.netPnl },
                                { label: 'Gross Exposure', value: positionSummary.grossExposure, neutral: true },
                            ].map((metric) => (
                                <div key={metric.label} className="rounded-xl border border-white/5 bg-black/20 p-4">
                                    <p className="text-[10px] uppercase tracking-[0.25em] text-zinc-500">{metric.label}</p>
                                    <p className={`mt-2 text-xl font-bold font-mono ${metric.neutral ? 'text-white' : metric.value >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                                        {formatEquity(metric.value)}
                                    </p>
                                </div>
                            ))}
                        </div>
                    </div>

                    <div className="rounded-2xl border border-white/10 bg-zinc-900/60 p-6 xl:col-span-7">
                        <div className="flex items-center justify-between">
                            <div>
                                <h2 className="text-sm font-bold uppercase tracking-wider text-zinc-300">Top Open Positions</h2>
                                <p className="mt-1 text-[10px] text-zinc-600">Largest live contributors by unrealized PnL magnitude.</p>
                            </div>
                        </div>
                        <div className="mt-5 space-y-3">
                            {filteredTopPositions.length === 0 ? (
                                <p className="rounded-xl border border-dashed border-white/10 bg-black/20 px-4 py-6 text-sm text-zinc-500">
                                    {normalizedSymbolFilter
                                        ? 'No open positions match the current symbol filter.'
                                        : 'No open positions. Analytics is currently driven by historical trade and snapshot data.'}
                                </p>
                            ) : (
                                filteredTopPositions.map((position) => (
                                    <div key={`${position.symbol}-${position.side}`} className="rounded-xl border border-white/5 bg-black/20 px-4 py-4">
                                        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                                            <div>
                                                <div className="flex items-center gap-2">
                                                    <span className="text-sm font-bold text-white">{position.symbol}</span>
                                                    <span className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${position.side === 'SHORT' ? 'bg-red-500/15 text-red-300' : 'bg-emerald-500/15 text-emerald-300'}`}>
                                                        {position.side} {position.leverage > 1 ? `${position.leverage}x` : ''}
                                                    </span>
                                                </div>
                                                <p className="mt-1 text-xs text-zinc-500">
                                                    Qty {position.quantity} | Avg {formatEquity(position.averagePrice)} | Now {formatEquity(position.currentPrice)}
                                                </p>
                                            </div>
                                            <div className="text-left md:text-right">
                                                <p className={`text-lg font-bold font-mono ${position.unrealizedPnl >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                                                    {formatCurrency(position.unrealizedPnl)}
                                                </p>
                                                <p className="text-xs text-zinc-500">Exposure {formatEquity(position.exposure)}</p>
                                            </div>
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                </div>

                <div className="mb-6 grid gap-6 xl:grid-cols-12">
                    <div className="rounded-2xl border border-white/10 bg-zinc-900/60 p-6 xl:col-span-5">
                        <div>
                            <h2 className="text-sm font-bold uppercase tracking-wider text-zinc-300">Rolling Performance</h2>
                            <p className="mt-1 text-[10px] text-zinc-600">Short and medium window momentum from stored equity snapshots.</p>
                        </div>
                        <div className="mt-5 space-y-4">
                            {([
                                ['7D', performanceWindows['7d']],
                                ['30D', performanceWindows['30d']],
                            ] as const).map(([label, window]) => (
                                <div key={label} className="rounded-xl border border-white/5 bg-black/20 p-4">
                                    <div className="flex items-center justify-between">
                                        <span className="text-sm font-bold text-white">{label}</span>
                                        <span className="text-xs text-zinc-500">{window.snapshotCount} snapshots</span>
                                    </div>
                                    <div className="mt-3 grid grid-cols-2 gap-3 text-sm">
                                        <div>
                                            <p className="text-zinc-500">Start</p>
                                            <p className="mt-1 font-mono text-zinc-200">{formatEquity(window.startingEquity)}</p>
                                        </div>
                                        <div>
                                            <p className="text-zinc-500">End</p>
                                            <p className="mt-1 font-mono text-zinc-200">{formatEquity(window.endingEquity)}</p>
                                        </div>
                                    </div>
                                    <div className="mt-3 flex items-center justify-between">
                                        <span className={`text-lg font-bold ${window.absoluteReturn >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                                            {formatCurrency(window.absoluteReturn)}
                                        </span>
                                        <span className={`text-sm font-mono ${window.returnPercentage >= 0 ? 'text-green-300' : 'text-red-300'}`}>
                                            {window.returnPercentage >= 0 ? '+' : ''}{window.returnPercentage.toFixed(2)}%
                                        </span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>

                    <div className="rounded-2xl border border-white/10 bg-zinc-900/60 p-6 xl:col-span-7">
                        <div>
                            <h2 className="text-sm font-bold uppercase tracking-wider text-zinc-300">Realized Symbol Attribution</h2>
                            <p className="mt-1 text-[10px] text-zinc-600">Closed-trade contribution by instrument, ordered by realized impact.</p>
                        </div>
                        <div className="mt-5 space-y-3">
                            {filteredSymbolAttribution.length === 0 ? (
                                <p className="rounded-xl border border-dashed border-white/10 bg-black/20 px-4 py-6 text-sm text-zinc-500">
                                    {normalizedSymbolFilter
                                        ? 'No realized attribution rows match the current symbol filter.'
                                        : 'No realized trade history yet. Attribution appears as trades close and realized PnL is recorded.'}
                                </p>
                            ) : (
                                filteredSymbolAttribution.map((row) => (
                                    <div key={row.symbol} className="flex items-center justify-between rounded-xl border border-white/5 bg-black/20 px-4 py-4">
                                        <div>
                                            <p className="text-sm font-bold text-white">{row.symbol}</p>
                                            <p className="mt-1 text-xs text-zinc-500">{row.tradeCount} recorded trades</p>
                                        </div>
                                        <p className={`text-lg font-bold font-mono ${row.realizedPnl >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                                            {formatCurrency(row.realizedPnl)}
                                        </p>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                </div>

                <div className="mb-6 rounded-2xl border border-white/10 bg-zinc-900/60 p-6">
                    <div className="flex items-center justify-between">
                        <div>
                            <h2 className="text-sm font-bold uppercase tracking-wider text-zinc-300">Exposure By Symbol</h2>
                            <p className="mt-1 text-[10px] text-zinc-600">Current open-risk concentration across live positions, ordered by gross exposure.</p>
                        </div>
                        <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs text-zinc-300">
                            {filteredRiskAttribution.length} symbols
                        </span>
                    </div>
                    <div className="mt-5 space-y-3">
                        {filteredRiskAttribution.length === 0 ? (
                            <p className="rounded-xl border border-dashed border-white/10 bg-black/20 px-4 py-6 text-sm text-zinc-500">
                            {normalizedSymbolFilter
                                    ? 'No live exposure rows match the current symbol filter.'
                                    : 'No live positions. Exposure attribution appears when the portfolio has open holdings.'}
                            </p>
                        ) : (
                            <>
                                <div className="rounded-xl border border-white/5 bg-black/20 p-4">
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <p className="text-[10px] uppercase tracking-[0.25em] text-zinc-500">Exposure Distribution</p>
                                            <p className="mt-1 text-xs text-zinc-500">Stacked by gross exposure share across current live positions.</p>
                                        </div>
                                        <p className="text-xs text-zinc-400">
                                            Total {formatEquity(filteredRiskAttribution.reduce((total, row) => total + row.exposure, 0))}
                                        </p>
                                    </div>
                                    <div className="mt-4 overflow-hidden rounded-full border border-white/5 bg-zinc-950/80">
                                        <div className="flex h-5 w-full">
                                            {filteredRiskAttribution.map((row, index) => (
                                                <div
                                                    key={`${row.symbol}-${row.side}-segment`}
                                                    title={`${row.symbol}: ${row.exposureShare.toFixed(2)}%`}
                                                    className="h-full"
                                                    style={{
                                                        width: `${Math.max(row.exposureShare, 1.5)}%`,
                                                        backgroundColor: exposurePalette[index % exposurePalette.length],
                                                    }}
                                                />
                                            ))}
                                        </div>
                                    </div>
                                    <div className="mt-4 grid gap-2 md:grid-cols-2 xl:grid-cols-4">
                                        {filteredRiskAttribution.map((row, index) => (
                                            <div key={`${row.symbol}-${row.side}-legend`} className="flex items-center gap-2 text-xs text-zinc-400">
                                                <span
                                                    className="h-2.5 w-2.5 rounded-full"
                                                    style={{ backgroundColor: exposurePalette[index % exposurePalette.length] }}
                                                />
                                                <span className="font-medium text-zinc-200">{row.symbol}</span>
                                                <span className="ml-auto font-mono">{row.exposureShare.toFixed(2)}%</span>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                                {filteredRiskAttribution.map((row, index) => (
                                    <div key={`${row.symbol}-${row.side}-${row.leverage}`} className="rounded-xl border border-white/5 bg-black/20 px-4 py-4">
                                        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                                            <div>
                                                <div className="flex items-center gap-2">
                                                    <span
                                                        className="h-2.5 w-2.5 rounded-full"
                                                        style={{ backgroundColor: exposurePalette[index % exposurePalette.length] }}
                                                    />
                                                    <span className="text-sm font-bold text-white">{row.symbol}</span>
                                                    <span className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${row.side === 'SHORT' ? 'bg-red-500/15 text-red-300' : 'bg-emerald-500/15 text-emerald-300'}`}>
                                                        {row.side} {row.leverage > 1 ? `${row.leverage}x` : ''}
                                                    </span>
                                                    <span className="rounded-full border border-white/10 bg-white/5 px-2 py-0.5 text-[10px] text-zinc-400">
                                                        {row.exposureShare.toFixed(2)}% share
                                                    </span>
                                                </div>
                                                <p className="mt-1 text-xs text-zinc-500">
                                                    Qty {row.quantity} | Avg {formatEquity(row.averagePrice)} | Now {formatEquity(row.currentPrice)}
                                                </p>
                                            </div>
                                            <div className="grid gap-2 text-left text-xs lg:grid-cols-3 lg:text-right">
                                                <div>
                                                    <p className="text-zinc-500">Exposure</p>
                                                    <p className="mt-1 font-mono font-bold text-white">{formatEquity(row.exposure)}</p>
                                                </div>
                                                <div>
                                                    <p className="text-zinc-500">Unrealized</p>
                                                    <p className={`mt-1 font-mono font-bold ${row.unrealizedPnl >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                                                        {formatCurrency(row.unrealizedPnl)}
                                                    </p>
                                                </div>
                                                <div>
                                                    <p className="text-zinc-500">Move</p>
                                                    <p className={`mt-1 font-mono font-bold ${row.movePercentage >= 0 ? 'text-green-300' : 'text-red-300'}`}>
                                                        {row.movePercentage >= 0 ? '+' : ''}{row.movePercentage.toFixed(2)}%
                                                    </p>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                ))}
                            </>
                        )}
                    </div>
                </div>

                <div className="mb-6 rounded-2xl border border-white/10 bg-zinc-900/60 p-6">
                    <div className="flex items-center justify-between">
                        <div>
                            <h2 className="text-sm font-bold uppercase tracking-wider text-zinc-300">Symbol Mini Timelines</h2>
                            <p className="mt-1 text-[10px] text-zinc-600">Per-symbol cumulative realized PnL sparklines from recorded trade history.</p>
                        </div>
                        <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs text-zinc-300">
                            {filteredSymbolMiniTimelines.length} symbols
                        </span>
                    </div>
                    <div className="mt-5 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                        {filteredSymbolMiniTimelines.length === 0 ? (
                            <p className="rounded-xl border border-dashed border-white/10 bg-black/20 px-4 py-6 text-sm text-zinc-500 md:col-span-2 xl:col-span-3">
                                {normalizedSymbolFilter
                                    ? 'No symbol timelines match the current symbol filter.'
                                    : 'No realized trade sequences yet. Mini timelines appear once symbols accumulate trade history.'}
                            </p>
                        ) : (
                            filteredSymbolMiniTimelines.map((timeline) => {
                                const path = buildMiniSparklinePath(timeline.points);
                                const latestPoint = timeline.points[timeline.points.length - 1];
                                const latestTimestamp = latestPoint?.timestamp ?? null;
                                const positive = timeline.finalRealizedPnl >= 0;
                                return (
                                    <div key={timeline.symbol} className="rounded-xl border border-white/5 bg-black/20 p-4">
                                        <div className="flex items-start justify-between gap-3">
                                            <div>
                                                <p className="text-sm font-bold text-white">{timeline.symbol}</p>
                                                <p className="mt-1 text-[11px] text-zinc-500">
                                                    {timeline.tradeCount} trades | {timeline.realizedTradeCount} realized updates
                                                </p>
                                            </div>
                                            <div className="text-right">
                                                <p className={`text-sm font-bold font-mono ${positive ? 'text-green-400' : 'text-red-400'}`}>
                                                    {formatCurrency(timeline.finalRealizedPnl)}
                                                </p>
                                                <p className="mt-1 text-[10px] text-zinc-500">{formatTimestamp(latestTimestamp)}</p>
                                            </div>
                                        </div>
                                        <div className="mt-4 rounded-lg border border-white/5 bg-zinc-950/80 px-3 py-3">
                                            <svg viewBox="0 0 100 44" className="h-16 w-full overflow-visible">
                                                <path d="M 0 22 L 100 22" stroke="rgba(255,255,255,0.08)" strokeWidth="1" fill="none" />
                                                {path ? (
                                                    <path
                                                        d={path}
                                                        stroke={positive ? '#22c55e' : '#f87171'}
                                                        strokeWidth="2"
                                                        fill="none"
                                                        strokeLinecap="round"
                                                        strokeLinejoin="round"
                                                    />
                                                ) : null}
                                            </svg>
                                        </div>
                                    </div>
                                );
                            })
                        )}
                    </div>
                </div>

                <div className="mb-6 rounded-2xl border border-white/10 bg-zinc-900/60 p-6">
                    <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                        <div>
                            <h2 className="text-sm font-bold uppercase tracking-wider text-zinc-300">Selected Symbol PnL Detail</h2>
                            <p className="mt-1 text-[10px] text-zinc-600">Realized path from trade history with current unrealized PnL overlaid as the live baseline.</p>
                        </div>
                        <div className="flex flex-wrap gap-2">
                            {symbolDetailCandidates.length === 0 ? (
                                <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs text-zinc-500">
                                    No symbol detail available
                                </span>
                            ) : (
                                symbolDetailCandidates.map((symbol) => (
                                    <button
                                        key={symbol}
                                        type="button"
                                        onClick={() => setSelectedSymbolDetail(symbol)}
                                        className={`rounded-full border px-3 py-1 text-xs font-bold tracking-[0.2em] transition-colors ${
                                            selectedSymbolDetail === symbol
                                                ? 'border-cyan-500/40 bg-cyan-500/15 text-cyan-300'
                                                : 'border-white/10 bg-white/5 text-zinc-400 hover:text-white'
                                        }`}
                                    >
                                        {symbol}
                                    </button>
                                ))
                            )}
                        </div>
                    </div>
                    {selectedSymbolTimeline || selectedSymbolRisk || selectedSymbolAttribution ? (
                        <div className="mt-5 grid gap-6 xl:grid-cols-[minmax(0,1.45fr)_minmax(280px,0.85fr)]">
                            <div className="rounded-xl border border-white/5 bg-black/20 p-4">
                                <div className="mb-3 flex items-center justify-between">
                                    <div>
                                        <p className="text-sm font-bold text-white">{selectedSymbolDetail}</p>
                                        <p className="mt-1 text-[10px] text-zinc-500">
                                            Realized cumulative path with current unrealized displacement from the latest realized baseline.
                                        </p>
                                    </div>
                                    <div className="flex gap-4 text-[10px] text-zinc-400">
                                        <span className="flex items-center gap-1.5">
                                            <span className="h-0.5 w-3 rounded bg-cyan-400"></span>
                                            Realized
                                        </span>
                                        <span className="flex items-center gap-1.5">
                                            <span className="h-0.5 w-3 rounded bg-amber-400"></span>
                                            Current Unrealized
                                        </span>
                                    </div>
                                </div>
                                <div className="rounded-lg border border-white/5 bg-zinc-950/80 px-4 py-4">
                                    <svg viewBox="0 0 280 96" className="h-48 w-full overflow-visible">
                                        <path d="M 0 48 L 280 48" stroke="rgba(255,255,255,0.08)" strokeWidth="1" fill="none" />
                                        <path
                                            d={`M 0 ${selectedSymbolVisuals.baselineY.toFixed(2)} L 280 ${selectedSymbolVisuals.baselineY.toFixed(2)}`}
                                            stroke="rgba(255,255,255,0.12)"
                                            strokeWidth="1"
                                            strokeDasharray="3 4"
                                            fill="none"
                                        />
                                        {selectedSymbolVisuals.realizedPath ? (
                                            <path
                                                d={selectedSymbolVisuals.realizedPath}
                                                stroke={(selectedSymbolTimeline?.finalRealizedPnl ?? 0) >= 0 ? '#22d3ee' : '#f87171'}
                                                strokeWidth="2.5"
                                                fill="none"
                                                strokeLinecap="round"
                                                strokeLinejoin="round"
                                            />
                                        ) : null}
                                        <path
                                            d={selectedSymbolVisuals.liveConnectorPath}
                                            stroke={(selectedSymbolRisk?.unrealizedPnl ?? 0) >= (selectedSymbolTimeline?.finalRealizedPnl ?? 0) ? '#34d399' : '#f87171'}
                                            strokeWidth="1.5"
                                            fill="none"
                                            strokeLinecap="round"
                                            strokeLinejoin="round"
                                        />
                                        <path
                                            d={`M 0 ${selectedSymbolVisuals.unrealizedY.toFixed(2)} L 280 ${selectedSymbolVisuals.unrealizedY.toFixed(2)}`}
                                            stroke="#f59e0b"
                                            strokeWidth="1.5"
                                            strokeDasharray="5 5"
                                            fill="none"
                                        />
                                        <circle
                                            cx="280"
                                            cy={selectedSymbolVisuals.unrealizedY.toFixed(2)}
                                            r="3.5"
                                            fill="#f59e0b"
                                        />
                                    </svg>
                                    <div className="mt-3 flex flex-wrap items-center gap-3 text-[10px] text-zinc-500">
                                        <span>
                                            Baseline {formatCurrency(selectedSymbolTimeline?.finalRealizedPnl ?? 0)}
                                        </span>
                                        <span className={selectedSymbolVisuals.unrealizedDelta >= 0 ? 'text-emerald-300' : 'text-red-300'}>
                                            Live displacement {formatCurrency(selectedSymbolVisuals.unrealizedDelta)}
                                        </span>
                                        {!selectedSymbolVisuals.hasRealizedPoints ? (
                                            <span className="text-amber-300">
                                                No realized trade path yet, showing live open-risk against zero baseline.
                                            </span>
                                        ) : null}
                                    </div>
                                </div>
                            </div>
                            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-1">
                                <div className="rounded-xl border border-white/5 bg-black/20 p-4">
                                    <p className="text-[10px] uppercase tracking-[0.25em] text-zinc-500">Final Realized</p>
                                    <p className={`mt-2 text-2xl font-bold font-mono ${(selectedSymbolTimeline?.finalRealizedPnl ?? 0) >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                                        {formatCurrency(selectedSymbolTimeline?.finalRealizedPnl ?? 0)}
                                    </p>
                                    <p className="mt-2 text-xs text-zinc-500">
                                        {selectedSymbolTimeline?.realizedTradeCount ?? 0} realized updates across {selectedSymbolTimeline?.tradeCount ?? 0} trades
                                    </p>
                                </div>
                                <div className="rounded-xl border border-white/5 bg-black/20 p-4">
                                    <p className="text-[10px] uppercase tracking-[0.25em] text-zinc-500">Current Unrealized</p>
                                    <p className={`mt-2 text-2xl font-bold font-mono ${(selectedSymbolRisk?.unrealizedPnl ?? 0) >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                                        {formatCurrency(selectedSymbolRisk?.unrealizedPnl ?? 0)}
                                    </p>
                                    <p className="mt-2 text-xs text-zinc-500">
                                        Exposure {formatEquity(selectedSymbolRisk?.exposure ?? 0)} | Share {(selectedSymbolRisk?.exposureShare ?? 0).toFixed(2)}%
                                    </p>
                                </div>
                                <div className="rounded-xl border border-white/5 bg-black/20 p-4">
                                    <p className="text-[10px] uppercase tracking-[0.25em] text-zinc-500">Realized Attribution</p>
                                    <p className={`mt-2 text-2xl font-bold font-mono ${(selectedSymbolAttribution?.realizedPnl ?? 0) >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                                        {formatCurrency(selectedSymbolAttribution?.realizedPnl ?? 0)}
                                    </p>
                                    <p className="mt-2 text-xs text-zinc-500">
                                        {selectedSymbolAttribution?.tradeCount ?? 0} recorded trades for this symbol
                                    </p>
                                </div>
                            </div>
                        </div>
                    ) : (
                        <p className="mt-5 rounded-xl border border-dashed border-white/10 bg-black/20 px-4 py-6 text-sm text-zinc-500">
                            No symbol detail is available for the current analytics filter.
                        </p>
                    )}
                </div>

                <div className="mb-6 grid gap-6 xl:grid-cols-2">
                    {[
                        {
                            label: 'Best Interval Move',
                            move: periodExtremes.bestMove,
                            accent: 'text-green-400',
                            softAccent: 'text-green-300',
                        },
                        {
                            label: 'Worst Interval Move',
                            move: periodExtremes.worstMove,
                            accent: 'text-red-400',
                            softAccent: 'text-red-300',
                        },
                    ].map(({ label, move, accent, softAccent }) => (
                        <div key={label} className="rounded-2xl border border-white/10 bg-zinc-900/60 p-6">
                            <div className="flex items-center justify-between">
                                <div>
                                    <h2 className="text-sm font-bold uppercase tracking-wider text-zinc-300">{label}</h2>
                                    <p className="mt-1 text-[10px] text-zinc-600">Largest point-to-point equity step in the stored curve.</p>
                                </div>
                            </div>
                            <div className="mt-5 flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
                                <div>
                                    <p className={`text-3xl font-bold ${accent}`}>{formatCurrency(move.absoluteReturn)}</p>
                                    <p className={`mt-1 text-sm font-mono ${softAccent}`}>
                                        {move.returnPercentage >= 0 ? '+' : ''}{move.returnPercentage.toFixed(2)}%
                                    </p>
                                </div>
                                <div className="text-xs text-zinc-500">
                                    <p>{formatTimestamp(move.from)}</p>
                                    <p className="mt-1">to {formatTimestamp(move.to)}</p>
                                </div>
                            </div>
                        </div>
                    ))}
                </div>

                <div className="bg-zinc-900/50 border border-zinc-800 rounded-2xl p-6 mb-6">
                    <div className="flex items-center justify-between mb-4">
                        <div>
                            <h2 className="text-sm font-bold text-zinc-300 uppercase tracking-wider">PnL Timeline Split</h2>
                            <p className="text-[10px] text-zinc-600">
                                Realized, unrealized, and net PnL across the selected {selectedCurveWindow} window
                                {curveWindowStats.selectedStart && curveWindowStats.selectedEnd
                                    ? ` (${formatTimestamp(curveWindowStats.selectedStart)} to ${formatTimestamp(curveWindowStats.selectedEnd)})`
                                    : '.'}
                                {(!curveWindowStats.isFilteredWindowDistinct && selectedCurveWindow !== 'ALL') ? ' Current history is shorter than this window.' : ''}
                            </p>
                        </div>
                        <div className="flex gap-4 text-[10px]">
                            <span className="flex items-center gap-1.5"><span className="w-3 h-0.5 bg-sky-400 rounded"></span> Realized</span>
                            <span className="flex items-center gap-1.5"><span className="w-3 h-0.5 bg-amber-400 rounded"></span> Unrealized</span>
                            <span className="flex items-center gap-1.5"><span className="w-3 h-0.5 bg-green-500 rounded"></span> Net</span>
                        </div>
                    </div>
                    {pnlTimeline.length === 0 ? (
                        <p className="rounded-xl border border-dashed border-white/10 bg-black/20 px-4 py-6 text-sm text-zinc-500">
                            No PnL timeline available yet. It will appear once snapshots and realized trade events accumulate.
                        </p>
                    ) : (
                        <canvas ref={pnlCanvasRef} className="w-full h-64 rounded-xl"></canvas>
                    )}
                </div>

                {/* Equity Curve */}
                <div className="bg-zinc-900/50 border border-zinc-800 rounded-2xl p-6 mb-6">
                    <div className="flex items-center justify-between mb-4">
                        <div>
                            <h2 className="text-sm font-bold text-zinc-300 uppercase tracking-wider">Equity Curve</h2>
                            <p className="text-[10px] text-zinc-600">
                                {selectedCurveWindow} view, {filteredEquityCurve.length} plotted points, drawdown overlay enabled.
                                {(!curveWindowStats.isFilteredWindowDistinct && selectedCurveWindow !== 'ALL') ? ' Current account history is shorter than this window.' : ''}
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
                                {filteredSymbolBreakdownEntries
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
                                {filteredSymbolBreakdownEntries.length === 0 && (
                                    <p className="text-xs text-zinc-600 italic">
                                        {normalizedSymbolFilter ? 'No symbol breakdown rows match the filter' : 'No trades yet'}
                                    </p>
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
