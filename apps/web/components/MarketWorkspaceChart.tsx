'use client';

import {
    CandlestickData,
    CandlestickSeries,
    ColorType,
    IChartApi,
    UTCTimestamp,
    createChart,
} from 'lightweight-charts';
import { useEffect, useMemo, useRef } from 'react';

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
    const lastRequestedOldestRef = useRef<number | null>(null);
    const dataRef = useRef<CandlePoint[]>(data);
    const onReachStartRef = useRef(onReachStart);
    const previousDataLengthRef = useRef(0);
    const previousOldestTimeRef = useRef<number | null>(null);
    const previousResetKeyRef = useRef(resetKey);

    const seriesData = useMemo<CandlestickData[]>(() => {
        return data.map((point) => ({
            time: Math.floor(point.openTime / 1000) as UTCTimestamp,
            open: point.open,
            high: point.high,
            low: point.low,
            close: point.close,
        }));
    }, [data]);

    useEffect(() => {
        dataRef.current = data;
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

        chart.timeScale().subscribeVisibleLogicalRangeChange(handleVisibleRangeChange);
        window.addEventListener('resize', handleResize);

        chartRef.current = chart;
        seriesRef.current = series;

        return () => {
            chart.timeScale().unsubscribeVisibleLogicalRangeChange(handleVisibleRangeChange);
            window.removeEventListener('resize', handleResize);
            chart.remove();
            chartRef.current = null;
            seriesRef.current = null;
        };
    }, []);

    useEffect(() => {
        if (!seriesRef.current || !chartRef.current) {
            return;
        }
        const timeScale = chartRef.current.timeScale();
        const visibleRange = timeScale.getVisibleLogicalRange();
        const previousLength = previousDataLengthRef.current;
        const previousOldestTime = previousOldestTimeRef.current;
        const currentOldestTime = data[0]?.openTime ?? null;

        seriesRef.current.setData(seriesData);

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
    }, [data, resetKey, seriesData]);

    useEffect(() => {
        if (!chartRef.current) {
            return;
        }
        lastRequestedOldestRef.current = null;
        previousResetKeyRef.current = resetKey;
        previousDataLengthRef.current = data.length;
        previousOldestTimeRef.current = data[0]?.openTime ?? null;
        chartRef.current.timeScale().fitContent();
    }, [data, resetKey]);

    return <div ref={chartContainerRef} className="h-[520px] w-full" />;
}
