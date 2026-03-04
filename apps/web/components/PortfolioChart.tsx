'use client';

import { useEffect, useRef } from 'react';
import { createChart, ColorType, Time, AreaSeries, IChartApi, ISeriesApi, AreaData } from 'lightweight-charts';

interface Snapshot {
    timestamp: string;
    totalEquity: number;
}

interface PortfolioChartProps {
    data: Snapshot[];
}

export default function PortfolioChart({ data }: PortfolioChartProps) {
    const chartContainerRef = useRef<HTMLDivElement>(null);
    const chartRef = useRef<IChartApi | null>(null);
    const seriesRef = useRef<ISeriesApi<'Area', Time> | null>(null);

    useEffect(() => {
        if (!chartContainerRef.current) return;

        const chart = createChart(chartContainerRef.current, {
            layout: {
                background: { type: ColorType.Solid, color: 'transparent' },
                textColor: '#71717a',
            },
            grid: {
                vertLines: { color: '#18181b' },
                horzLines: { color: '#18181b' },
            },
            width: chartContainerRef.current.clientWidth,
            height: 300,
            timeScale: {
                timeVisible: true,
                secondsVisible: true,
                borderColor: '#27272a',
            },
            rightPriceScale: {
                borderColor: '#27272a',
            }
        });

        const series = chart.addSeries(AreaSeries, {
            lineColor: '#22c55e',
            topColor: 'rgba(34, 197, 94, 0.4)',
            bottomColor: 'rgba(34, 197, 94, 0.0)',
            lineWidth: 2,
        });

        seriesRef.current = series;
        chartRef.current = chart;

        const handleResize = () => {
            chart.applyOptions({ width: chartContainerRef.current?.clientWidth });
        };

        window.addEventListener('resize', handleResize);

        return () => {
            window.removeEventListener('resize', handleResize);
            chart.remove();
        };
    }, []);

    useEffect(() => {
        if (seriesRef.current && data.length > 0) {
            const chartData: AreaData<Time>[] = data.map(item => ({
                time: Math.floor(new Date(item.timestamp).getTime() / 1000) as Time,
                value: item.totalEquity,
            }));

            // Filter duplicates as lightweight-charts doesn't like same-time points
            const uniqueData = chartData.filter((val, index, self) =>
                index === self.findIndex((t) => t.time === val.time)
            );

            seriesRef.current.setData(uniqueData);
            chartRef.current?.timeScale().fitContent();
        }
    }, [data]);

    return (
        <div ref={chartContainerRef} className="w-full h-full min-h-[300px]" />
    );
}
