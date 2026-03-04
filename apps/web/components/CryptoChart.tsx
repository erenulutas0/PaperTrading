'use client';

import { createChart, ColorType, CandlestickData, CandlestickSeries, UTCTimestamp } from 'lightweight-charts';
import React, { useEffect, useRef } from 'react';

export default function CryptoChart({ symbol, color = 'white' }: { symbol: string, color?: string }) {
    const chartContainerRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!chartContainerRef.current) return;

        const chart = createChart(chartContainerRef.current, {
            layout: {
                background: { type: ColorType.Solid, color: 'transparent' },
                textColor: color,
            },
            width: chartContainerRef.current.clientWidth,
            height: 300,
            grid: {
                vertLines: { color: '#333' },
                horzLines: { color: '#333' },
            },
        });

        const candlestickSeries = chart.addSeries(CandlestickSeries, {
            upColor: '#26a69a',
            downColor: '#ef5350',
            borderVisible: false,
            wickUpColor: '#26a69a',
            wickDownColor: '#ef5350',
        });

        // 1. Fetch Historical Data (1h candles)
        fetch(`https://api.binance.com/api/v3/klines?symbol=${symbol}&interval=1h&limit=100`)
            .then((res) => res.json())
            .then((data: unknown) => {
                type BinanceKline = [number, string, string, string, string, ...unknown[]];
                const cdata: CandlestickData[] = Array.isArray(data)
                    ? data
                        .filter((row): row is BinanceKline => Array.isArray(row) && row.length >= 5)
                        .map((row) => ({
                            time: Math.floor(Number(row[0]) / 1000) as UTCTimestamp,
                            open: Number(row[1]),
                            high: Number(row[2]),
                            low: Number(row[3]),
                            close: Number(row[4]),
                        }))
                    : [];
                candlestickSeries.setData(cdata);
            })
            .catch((err) => console.error(err));

        // 2. Resize Handling
        const handleResize = () => {
            chart.applyOptions({ width: chartContainerRef.current?.clientWidth });
        };

        window.addEventListener('resize', handleResize);

        return () => {
            window.removeEventListener('resize', handleResize);
            chart.remove();
        };
    }, [symbol, color]);

    return <div ref={chartContainerRef} className="w-full h-[300px]" />;
}
