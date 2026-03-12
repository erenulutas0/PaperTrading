'use client';

import { CandlestickData, CandlestickSeries, ColorType, IChartApi, UTCTimestamp, createChart } from 'lightweight-charts';
import { useEffect, useMemo, useRef } from 'react';

interface CandlePoint {
    openTime: number;
    open: number;
    high: number;
    low: number;
    close: number;
    volume: number;
}

export default function MarketWorkspaceChart({ data }: { data: CandlePoint[] }) {
    const chartContainerRef = useRef<HTMLDivElement>(null);
    const chartRef = useRef<IChartApi | null>(null);

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
        if (!chartContainerRef.current) {
            return;
        }

        const chart = createChart(chartContainerRef.current, {
            layout: {
                background: { type: ColorType.Solid, color: 'transparent' },
                textColor: '#a1a1aa',
            },
            width: chartContainerRef.current.clientWidth,
            height: 420,
            rightPriceScale: {
                borderColor: 'rgba(255,255,255,0.08)',
            },
            timeScale: {
                borderColor: 'rgba(255,255,255,0.08)',
                timeVisible: true,
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
        series.setData(seriesData);
        chart.timeScale().fitContent();
        chartRef.current = chart;

        const handleResize = () => {
            if (!chartContainerRef.current) {
                return;
            }
            chart.applyOptions({ width: chartContainerRef.current.clientWidth });
            chart.timeScale().fitContent();
        };

        window.addEventListener('resize', handleResize);
        return () => {
            window.removeEventListener('resize', handleResize);
            chart.remove();
        };
    }, [seriesData]);

    return <div ref={chartContainerRef} className="h-[420px] w-full" />;
}
