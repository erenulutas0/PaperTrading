'use client';

import {
    CandlestickData,
    CandlestickSeries,
    ColorType,
    HistogramSeries,
    IChartApi,
    LineData,
    LineSeries,
    LineStyle,
    UTCTimestamp,
    createChart,
} from 'lightweight-charts';
import { useEffect, useMemo, useRef, useState } from 'react';

interface CandlePoint {
    openTime: number;
    open: number;
    high: number;
    low: number;
    close: number;
    volume: number;
}

type DrawingMode = 'none' | 'horizontal' | 'trend';

interface ChartDrawing {
    id: string;
    type: 'horizontal' | 'trend';
    price: number;
    startTime: number;
    endTime?: number;
    endPrice?: number;
}

interface MarketWorkspaceChartProps {
    data: CandlePoint[];
    compareData?: CandlePoint[];
    compareLabel?: string | null;
    compareVisible?: boolean;
    drawingMode?: DrawingMode;
    clearDrawingsToken?: number;
    onDrawingComplete?: () => void;
    resetKey: string;
    onReachStart?: (oldestOpenTime: number) => void;
}

export default function MarketWorkspaceChart({
    data,
    compareData = [],
    compareLabel = null,
    compareVisible = true,
    drawingMode = 'none',
    clearDrawingsToken = 0,
    onDrawingComplete,
    resetKey,
    onReachStart,
}: MarketWorkspaceChartProps) {
    const chartContainerRef = useRef<HTMLDivElement>(null);
    const chartRef = useRef<IChartApi | null>(null);
    const seriesRef = useRef<any>(null);
    const volumeSeriesRef = useRef<any>(null);
    const compareSeriesRef = useRef<any>(null);
    const drawingSeriesRef = useRef<Array<{ id: string; series: any }>>([]);
    const lastRequestedOldestRef = useRef<number | null>(null);
    const dataRef = useRef<CandlePoint[]>(data);
    const compareDataRef = useRef<CandlePoint[]>(compareData);
    const onReachStartRef = useRef(onReachStart);
    const onDrawingCompleteRef = useRef(onDrawingComplete);
    const drawingModeRef = useRef<DrawingMode>(drawingMode);
    const previousDataLengthRef = useRef(0);
    const previousOldestTimeRef = useRef<number | null>(null);
    const previousResetKeyRef = useRef(resetKey);
    const pendingTrendAnchorRef = useRef<{ time: number; price: number } | null>(null);
    const [activePoint, setActivePoint] = useState<CandlePoint | null>(data[data.length - 1] ?? null);
    const [drawings, setDrawings] = useState<ChartDrawing[]>([]);

    const seriesData = useMemo<CandlestickData[]>(() => {
        return data.map((point) => ({
            time: Math.floor(point.openTime / 1000) as UTCTimestamp,
            open: point.open,
            high: point.high,
            low: point.low,
            close: point.close,
        }));
    }, [data]);

    const volumeSeriesData = useMemo(() => {
        return data.map((point) => ({
            time: Math.floor(point.openTime / 1000) as UTCTimestamp,
            value: point.volume,
            color: point.close >= point.open ? 'rgba(34,197,94,0.45)' : 'rgba(239,68,68,0.45)',
        }));
    }, [data]);

    const compareSeriesData = useMemo<LineData[]>(() => {
        if (!compareVisible || !compareData.length) {
            return [];
        }
        const baseClose = compareData[0].close || 1;
        return compareData.map((point) => ({
            time: Math.floor(point.openTime / 1000) as UTCTimestamp,
            value: (point.close / baseClose) * 100,
        }));
    }, [compareData, compareVisible]);

    const compareLatestValue = compareSeriesData.length > 0
        ? compareSeriesData[compareSeriesData.length - 1]?.value ?? null
        : null;
    const primaryLatestIndexedValue = data.length > 0
        ? ((data[data.length - 1].close / (data[0].close || 1)) * 100)
        : null;
    const relativePerformanceGap = compareLatestValue !== null && primaryLatestIndexedValue !== null
        ? primaryLatestIndexedValue - compareLatestValue
        : null;

    const resolvedActivePoint = activePoint ?? data[data.length - 1] ?? null;
    const activeChange = resolvedActivePoint ? resolvedActivePoint.close - resolvedActivePoint.open : 0;
    const activeChangePercent = resolvedActivePoint && resolvedActivePoint.open !== 0
        ? (activeChange / resolvedActivePoint.open) * 100
        : 0;
    const drawingSummaries = useMemo(() => {
        return drawings.map((drawing, index) => {
            if (drawing.type === 'horizontal') {
                return {
                    id: drawing.id,
                    label: `H${index + 1}`,
                    detail: `Level ${drawing.price.toFixed(2)}`,
                };
            }
            return {
                id: drawing.id,
                label: `T${index + 1}`,
                detail: `${new Date(drawing.startTime).toLocaleDateString()} → ${drawing.endTime ? new Date(drawing.endTime).toLocaleDateString() : '...'}`,
            };
        });
    }, [drawings]);

    useEffect(() => {
        dataRef.current = data;
        setActivePoint((current) => {
            if (!data.length) {
                return null;
            }
            if (!current) {
                return data[data.length - 1];
            }
            return data.find((point) => point.openTime === current.openTime) ?? data[data.length - 1];
        });
    }, [data]);

    useEffect(() => {
        compareDataRef.current = compareData;
    }, [compareData]);

    useEffect(() => {
        onReachStartRef.current = onReachStart;
    }, [onReachStart]);

    useEffect(() => {
        onDrawingCompleteRef.current = onDrawingComplete;
    }, [onDrawingComplete]);

    useEffect(() => {
        drawingModeRef.current = drawingMode;
    }, [drawingMode]);

    useEffect(() => {
        pendingTrendAnchorRef.current = null;
    }, [drawingMode]);

    useEffect(() => {
        if (!chartContainerRef.current || chartRef.current) {
            return;
        }

        const chart = createChart(chartContainerRef.current, {
            layout: {
                background: { type: ColorType.Solid, color: 'transparent' },
                textColor: '#a1a1aa',
            },
            width: chartContainerRef.current.clientWidth,
            height: 520,
            rightPriceScale: {
                borderColor: 'rgba(255,255,255,0.08)',
            },
            timeScale: {
                borderColor: 'rgba(255,255,255,0.08)',
                timeVisible: true,
                secondsVisible: false,
            },
            grid: {
                vertLines: { color: 'rgba(255,255,255,0.05)' },
                horzLines: { color: 'rgba(255,255,255,0.05)' },
            },
            crosshair: {
                vertLine: { color: 'rgba(250,204,21,0.35)' },
                horzLine: { color: 'rgba(250,204,21,0.15)' },
            },
        });

        const series = chart.addSeries(CandlestickSeries, {
            upColor: '#22c55e',
            downColor: '#ef4444',
            borderVisible: false,
            wickUpColor: '#22c55e',
            wickDownColor: '#ef4444',
        });

        const volumeSeries = chart.addSeries(HistogramSeries, {
            priceFormat: {
                type: 'volume',
            },
            priceScaleId: '',
        });

        const compareSeries = chart.addSeries(LineSeries, {
            priceScaleId: 'left',
            color: '#f59e0b',
            lineWidth: 2,
            crosshairMarkerVisible: false,
            lastValueVisible: true,
            priceLineVisible: false,
        });

        chart.priceScale('').applyOptions({
            scaleMargins: {
                top: 0.78,
                bottom: 0,
            },
        });
        chart.priceScale('left').applyOptions({
            visible: compareDataRef.current.length > 0,
            borderColor: 'rgba(245,158,11,0.18)',
            scaleMargins: {
                top: 0.08,
                bottom: 0.25,
            },
        });
        chart.priceScale('right').applyOptions({
            scaleMargins: {
                top: 0.08,
                bottom: 0.25,
            },
        });

        const handleResize = () => {
            if (!chartContainerRef.current) {
                return;
            }
            chart.applyOptions({ width: chartContainerRef.current.clientWidth });
        };

        const handleVisibleRangeChange = (range: any) => {
            const latestData = dataRef.current;
            const reachStart = onReachStartRef.current;
            if (!range || !reachStart || latestData.length === 0) {
                return;
            }
            if (range.from > 25) {
                return;
            }

            const oldestOpenTime = latestData[0].openTime;
            if (lastRequestedOldestRef.current === oldestOpenTime) {
                return;
            }
            lastRequestedOldestRef.current = oldestOpenTime;
            reachStart(oldestOpenTime);
        };

        const handleCrosshairMove = (param: any) => {
            const latestData = dataRef.current;
            if (!param?.time || latestData.length === 0) {
                setActivePoint(latestData[latestData.length - 1] ?? null);
                return;
            }

            const hoveredTime = Number(param.time) * 1000;
            const matchingPoint = latestData.find((point) => point.openTime === hoveredTime);
            setActivePoint(matchingPoint ?? latestData[latestData.length - 1] ?? null);
        };

        const handleClick = (param: any) => {
            const currentDrawingMode = drawingModeRef.current;
            if (currentDrawingMode === 'none' || !param?.time || !param?.point || !seriesRef.current) {
                return;
            }

            const clickedTime = Number(param.time) * 1000;
            const price = seriesRef.current.coordinateToPrice(param.point.y);
            if (typeof price !== 'number' || Number.isNaN(price)) {
                return;
            }

            if (currentDrawingMode === 'horizontal') {
                setDrawings((current) => [
                    ...current,
                    {
                        id: `horizontal-${clickedTime}-${current.length}`,
                        type: 'horizontal',
                        price,
                        startTime: clickedTime,
                    },
                ]);
                onDrawingCompleteRef.current?.();
                return;
            }

            if (!pendingTrendAnchorRef.current) {
                pendingTrendAnchorRef.current = {
                    time: clickedTime,
                    price,
                };
                return;
            }

            const anchor = pendingTrendAnchorRef.current;
            if (anchor.time === clickedTime) {
                pendingTrendAnchorRef.current = null;
                return;
            }
            pendingTrendAnchorRef.current = null;
            setDrawings((current) => [
                ...current,
                {
                    id: `trend-${anchor.time}-${clickedTime}-${current.length}`,
                    type: 'trend',
                    price: anchor.price,
                    startTime: anchor.time,
                    endTime: clickedTime,
                    endPrice: price,
                },
            ]);
            onDrawingCompleteRef.current?.();
        };

        chart.timeScale().subscribeVisibleLogicalRangeChange(handleVisibleRangeChange);
        chart.subscribeCrosshairMove(handleCrosshairMove);
        chart.subscribeClick(handleClick);
        window.addEventListener('resize', handleResize);

        chartRef.current = chart;
        seriesRef.current = series;
        volumeSeriesRef.current = volumeSeries;
        compareSeriesRef.current = compareSeries;

        return () => {
            chart.timeScale().unsubscribeVisibleLogicalRangeChange(handleVisibleRangeChange);
            chart.unsubscribeCrosshairMove(handleCrosshairMove);
            chart.unsubscribeClick(handleClick);
            window.removeEventListener('resize', handleResize);
            chart.remove();
            chartRef.current = null;
            seriesRef.current = null;
            volumeSeriesRef.current = null;
            compareSeriesRef.current = null;
            drawingSeriesRef.current = [];
        };
    }, []);

    useEffect(() => {
        if (!seriesRef.current || !volumeSeriesRef.current || !compareSeriesRef.current || !chartRef.current) {
            return;
        }
        const timeScale = chartRef.current.timeScale();
        const visibleRange = timeScale.getVisibleLogicalRange();
        const previousLength = previousDataLengthRef.current;
        const previousOldestTime = previousOldestTimeRef.current;
        const currentOldestTime = data[0]?.openTime ?? null;

        seriesRef.current.setData(seriesData);
        volumeSeriesRef.current.setData(volumeSeriesData);
        compareSeriesRef.current.setData(compareSeriesData);
        chartRef.current.priceScale('left').applyOptions({
            visible: compareSeriesData.length > 0,
        });

        const prependedBars = data.length - previousLength;
        const prependedOlderHistory = previousResetKeyRef.current === resetKey
            && visibleRange
            && previousOldestTime !== null
            && currentOldestTime !== null
            && currentOldestTime < previousOldestTime
            && prependedBars > 0;

        if (prependedOlderHistory) {
            timeScale.setVisibleLogicalRange({
                from: visibleRange.from + prependedBars,
                to: visibleRange.to + prependedBars,
            });
        }

        previousDataLengthRef.current = data.length;
        previousOldestTimeRef.current = currentOldestTime;
    }, [compareSeriesData, data, resetKey, seriesData, volumeSeriesData]);

    useEffect(() => {
        if (!chartRef.current) {
            return;
        }
        lastRequestedOldestRef.current = null;
        previousResetKeyRef.current = resetKey;
        previousDataLengthRef.current = data.length;
        previousOldestTimeRef.current = data[0]?.openTime ?? null;
        chartRef.current.timeScale().fitContent();
    }, [resetKey]);

    useEffect(() => {
        if (!chartRef.current) {
            return;
        }
        drawingSeriesRef.current.forEach(({ series }) => {
            chartRef.current?.removeSeries(series);
        });
        drawingSeriesRef.current = [];

        drawings.forEach((drawing) => {
            const lineSeries = chartRef.current?.addSeries(LineSeries, {
                priceScaleId: 'right',
                color: drawing.type === 'horizontal' ? '#38bdf8' : '#f472b6',
                lineWidth: 2,
                lineStyle: drawing.type === 'horizontal' ? LineStyle.Dashed : LineStyle.Solid,
                crosshairMarkerVisible: false,
                lastValueVisible: false,
                priceLineVisible: false,
            });
            if (!lineSeries) {
                return;
            }

            const firstTime = data[0]?.openTime;
            const lastTime = data[data.length - 1]?.openTime;
            if (!firstTime || !lastTime) {
                return;
            }

            if (drawing.type === 'horizontal') {
                lineSeries.setData([
                    { time: Math.floor(firstTime / 1000) as UTCTimestamp, value: drawing.price },
                    { time: Math.floor(lastTime / 1000) as UTCTimestamp, value: drawing.price },
                ]);
            } else if (drawing.endTime && typeof drawing.endPrice === 'number') {
                const firstPoint = { time: Math.floor(drawing.startTime / 1000) as UTCTimestamp, value: drawing.price };
                const secondPoint = { time: Math.floor(drawing.endTime / 1000) as UTCTimestamp, value: drawing.endPrice };
                const orderedPoints = firstPoint.time <= secondPoint.time
                    ? [firstPoint, secondPoint]
                    : [secondPoint, firstPoint];
                lineSeries.setData(orderedPoints);
            }

            drawingSeriesRef.current.push({ id: drawing.id, series: lineSeries });
        });
    }, [data, drawings]);

    useEffect(() => {
        setDrawings([]);
        pendingTrendAnchorRef.current = null;
    }, [clearDrawingsToken, resetKey]);

    return (
        <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-2xl border border-white/8 bg-black/35 px-4 py-3 text-xs">
                <div className="min-w-[132px]">
                    <p className="text-[10px] uppercase tracking-[0.24em] text-zinc-500">Date</p>
                    <p className="mt-1 font-mono text-zinc-200">
                        {resolvedActivePoint ? new Date(resolvedActivePoint.openTime).toLocaleString() : '—'}
                    </p>
                </div>
                <div>
                    <span className="text-zinc-500">O </span>
                    <span className="font-mono text-zinc-200">{resolvedActivePoint ? resolvedActivePoint.open.toFixed(2) : '—'}</span>
                </div>
                <div>
                    <span className="text-zinc-500">H </span>
                    <span className="font-mono text-zinc-200">{resolvedActivePoint ? resolvedActivePoint.high.toFixed(2) : '—'}</span>
                </div>
                <div>
                    <span className="text-zinc-500">L </span>
                    <span className="font-mono text-zinc-200">{resolvedActivePoint ? resolvedActivePoint.low.toFixed(2) : '—'}</span>
                </div>
                <div>
                    <span className="text-zinc-500">C </span>
                    <span className="font-mono text-zinc-200">{resolvedActivePoint ? resolvedActivePoint.close.toFixed(2) : '—'}</span>
                </div>
                <div className={activeChange >= 0 ? 'text-emerald-400' : 'text-red-400'}>
                    <span className="text-zinc-500">Δ </span>
                    <span className="font-mono">
                        {resolvedActivePoint ? `${activeChange >= 0 ? '+' : ''}${activeChange.toFixed(2)} (${activeChangePercent >= 0 ? '+' : ''}${activeChangePercent.toFixed(2)}%)` : '—'}
                    </span>
                </div>
                <div>
                    <span className="text-zinc-500">Vol </span>
                    <span className="font-mono text-zinc-200">
                        {resolvedActivePoint ? resolvedActivePoint.volume.toLocaleString(undefined, { maximumFractionDigits: 2 }) : '—'}
                    </span>
                </div>
                {compareLabel && compareSeriesData.length > 0 && (
                    <div className="text-amber-300">
                        <span className="text-zinc-500">{compareLabel} </span>
                        <span className="font-mono">
                            {compareLatestValue?.toFixed(2)} idx
                        </span>
                    </div>
                )}
            </div>
            <div ref={chartContainerRef} className="h-[520px] w-full" />
            {compareLabel && (
                <div className="flex flex-wrap items-center gap-3 rounded-2xl border border-white/8 bg-black/30 px-4 py-3 text-xs">
                    <span className="uppercase tracking-[0.24em] text-zinc-500">Compare</span>
                    <span className={`rounded-full border px-3 py-1 font-semibold ${compareSeriesData.length > 0
                        ? 'border-amber-400/30 bg-amber-400/10 text-amber-300'
                        : 'border-white/10 bg-white/[0.03] text-zinc-500'}`}>
                        {compareLabel}
                    </span>
                    <span className="text-zinc-500">
                        {compareSeriesData.length > 0
                            ? `Overlay active · ${compareLatestValue?.toFixed(2)} indexed`
                            : 'Overlay hidden'}
                    </span>
                    {compareSeriesData.length > 0 && relativePerformanceGap !== null && (
                        <span className={relativePerformanceGap >= 0 ? 'text-emerald-400' : 'text-red-400'}>
                            {relativePerformanceGap >= 0 ? 'Primary leads' : 'Primary trails'} · {relativePerformanceGap >= 0 ? '+' : ''}{relativePerformanceGap.toFixed(2)} idx
                        </span>
                    )}
                    {drawingMode !== 'none' && (
                        <span className="text-sky-300">
                            Draw mode · {drawingMode === 'horizontal' ? 'click chart to place a level' : 'click two points for a trend line'}
                        </span>
                    )}
                    {pendingTrendAnchorRef.current && (
                        <span className="text-pink-300">
                            Trend anchor locked · choose end point
                        </span>
                    )}
                    {drawings.length > 0 && (
                        <span className="text-zinc-500">
                            {drawings.length} drawing{drawings.length === 1 ? '' : 's'}
                        </span>
                    )}
                </div>
            )}
            {drawings.length > 0 && (
                <div className="flex flex-wrap items-center gap-2 rounded-2xl border border-white/8 bg-black/30 px-4 py-3 text-xs">
                    <span className="uppercase tracking-[0.24em] text-zinc-500">Drawings</span>
                    {drawingSummaries.map((drawing) => (
                        <div
                            key={drawing.id}
                            className="flex items-center gap-2 rounded-full border border-white/10 bg-white/[0.03] px-3 py-1"
                        >
                            <span className="font-semibold text-white">{drawing.label}</span>
                            <span className="text-zinc-500">{drawing.detail}</span>
                            <button
                                onClick={() => setDrawings((current) => current.filter((item) => item.id !== drawing.id))}
                                className="text-zinc-500 transition hover:text-red-400"
                            >
                                ×
                            </button>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}
