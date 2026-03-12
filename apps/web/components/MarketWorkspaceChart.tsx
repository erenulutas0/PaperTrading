'use client';

import {
    CandlestickData,
    CandlestickSeries,
    ColorType,
    HistogramSeries,
    IChartApi,
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

interface MarketWorkspaceChartProps {
    data: CandlePoint[];
    resetKey: string;
    onReachStart?: (oldestOpenTime: number) => void;
}

export default function MarketWorkspaceChart({ data, resetKey, onReachStart }: MarketWorkspaceChartProps) {
    const chartContainerRef = useRef<HTMLDivElement>(null);
    const chartRef = useRef<IChartApi | null>(null);
    const seriesRef = useRef<any>(null);
    const volumeSeriesRef = useRef<any>(null);
    const lastRequestedOldestRef = useRef<number | null>(null);
    const dataRef = useRef<CandlePoint[]>(data);
    const onReachStartRef = useRef(onReachStart);
    const previousDataLengthRef = useRef(0);
    const previousOldestTimeRef = useRef<number | null>(null);
    const previousResetKeyRef = useRef(resetKey);
    const [activePoint, setActivePoint] = useState<CandlePoint | null>(data[data.length - 1] ?? null);

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

    const resolvedActivePoint = activePoint ?? data[data.length - 1] ?? null;
    const activeChange = resolvedActivePoint ? resolvedActivePoint.close - resolvedActivePoint.open : 0;
    const activeChangePercent = resolvedActivePoint && resolvedActivePoint.open !== 0
        ? (activeChange / resolvedActivePoint.open) * 100
        : 0;

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
        onReachStartRef.current = onReachStart;
    }, [onReachStart]);

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

        chart.priceScale('').applyOptions({
            scaleMargins: {
                top: 0.78,
                bottom: 0,
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

        chart.timeScale().subscribeVisibleLogicalRangeChange(handleVisibleRangeChange);
        chart.subscribeCrosshairMove(handleCrosshairMove);
        window.addEventListener('resize', handleResize);

        chartRef.current = chart;
        seriesRef.current = series;
        volumeSeriesRef.current = volumeSeries;

        return () => {
            chart.timeScale().unsubscribeVisibleLogicalRangeChange(handleVisibleRangeChange);
            chart.unsubscribeCrosshairMove(handleCrosshairMove);
            window.removeEventListener('resize', handleResize);
            chart.remove();
            chartRef.current = null;
            seriesRef.current = null;
            volumeSeriesRef.current = null;
        };
    }, []);

    useEffect(() => {
        if (!seriesRef.current || !volumeSeriesRef.current || !chartRef.current) {
            return;
        }
        const timeScale = chartRef.current.timeScale();
        const visibleRange = timeScale.getVisibleLogicalRange();
        const previousLength = previousDataLengthRef.current;
        const previousOldestTime = previousOldestTimeRef.current;
        const currentOldestTime = data[0]?.openTime ?? null;

        seriesRef.current.setData(seriesData);
        volumeSeriesRef.current.setData(volumeSeriesData);

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
    }, [data, resetKey, seriesData, volumeSeriesData]);

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
            </div>
            <div ref={chartContainerRef} className="h-[520px] w-full" />
        </div>
    );
}
