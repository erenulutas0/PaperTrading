'use client';

import React, { useState, useEffect } from 'react';
import CryptoChart from './CryptoChart';
import { apiFetch } from '../lib/api-client';

interface Portfolio {
    id: string;
    name: string;
    balance?: number;
}

interface TradeModalProps {
    symbol: string;
    portfolios: Portfolio[];
    availablePrices: Record<string, number>;
    initialPortfolioId?: string;
    onClose: () => void;
    onSuccess: () => void;
}

export default function TradeModal({
    symbol: initialSymbol,
    portfolios,
    availablePrices,
    initialPortfolioId,
    onClose,
    onSuccess
}: TradeModalProps) {
    const [activeTab, setActiveTab] = useState<'buy' | 'sell'>('buy');
    const [side, setSide] = useState<'LONG' | 'SHORT'>('LONG');
    const [symbol, setSymbol] = useState(initialSymbol);
    const [portfolioId, setPortfolioId] = useState(initialPortfolioId || '');
    const [quantity, setQuantity] = useState('');
    const [leverage, setLeverage] = useState(1);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const price = availablePrices[symbol] || 0;

    useEffect(() => {
        if (portfolios.length > 0 && !portfolioId) {
            setPortfolioId(portfolios[0].id);
        }
    }, [portfolios, portfolioId]);

    const handleTrade = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setLoading(true);

        if (!portfolioId) {
            setError('Please create a portfolio first.');
            setLoading(false);
            return;
        }

        const endpoint = activeTab === 'buy' ? 'buy' : 'sell';

        try {
            const res = await apiFetch(`/api/v1/trade/${endpoint}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    portfolioId,
                    symbol,
                    quantity: parseFloat(quantity),
                    leverage: activeTab === 'buy' ? leverage : undefined,
                    side: activeTab === 'buy' ? side : undefined // Send side for opening positions
                }),
            });

            if (res.ok) {
                onSuccess();
                onClose();
            } else {
                const msg = await res.text();
                setError(msg || 'Trade failed');
            }
        } catch (err) {
            setError('Something went wrong');
        } finally {
            setLoading(false);
        }
    };

    const cost = quantity ? (parseFloat(quantity) * price) : 0;
    const margin = cost / leverage;
    const isBuy = activeTab === 'buy';

    return (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <div className={`bg-zinc-900 border ${isBuy ? 'border-green-900/50' : 'border-red-900/50'} rounded-xl w-full max-w-5xl overflow-hidden shadow-2xl flex flex-col md:flex-row h-auto md:h-[600px]`}>

                {/* Left: Chart */}
                <div className="md:w-2/3 bg-black/50 p-4 flex flex-col border-b md:border-b-0 md:border-r border-zinc-800 relative">
                    <div className="flex justify-between items-center mb-4">
                        <div className="flex flex-col">
                            <select
                                value={symbol}
                                onChange={(e) => setSymbol(e.target.value)}
                                className="bg-transparent text-xl font-bold outline-none cursor-pointer hover:text-green-400 transition-colors"
                            >
                                {Object.keys(availablePrices).map(s => (
                                    <option key={s} value={s} className="bg-zinc-900">{s}</option>
                                ))}
                            </select>
                            <span className="text-xs text-zinc-500">Binance Spot</span>
                        </div>
                        <span className={`text-xl font-mono ${isBuy ? 'text-green-500' : 'text-red-500'}`}>${price.toLocaleString()}</span>
                    </div>
                    <div className="flex-1 w-full min-h-[300px] relative">
                        <div className="absolute inset-0">
                            <CryptoChart symbol={symbol} />
                        </div>
                    </div>
                </div>

                {/* Right: Form */}
                <div className="md:w-1/3 p-6 flex flex-col bg-zinc-900 overflow-y-auto">
                    <div className="flex justify-between items-center mb-6">
                        <div className="flex bg-black/50 p-1 rounded-lg border border-zinc-800">
                            <button
                                onClick={() => setActiveTab('buy')}
                                className={`px-6 py-2 rounded-md font-bold text-sm transition-all ${isBuy ? 'bg-green-600 text-white shadow-lg' : 'text-zinc-500 hover:text-zinc-300'}`}
                            >
                                BUY
                            </button>
                            <button
                                onClick={() => setActiveTab('sell')}
                                className={`px-6 py-2 rounded-md font-bold text-sm transition-all ${!isBuy ? 'bg-red-600 text-white shadow-lg' : 'text-zinc-500 hover:text-zinc-300'}`}
                            >
                                SELL
                            </button>
                        </div>
                        <button onClick={onClose} className="text-zinc-400 hover:text-white text-2xl leading-none">&times;</button>
                    </div>

                    {error && <div className="bg-red-500/10 border border-red-500/20 p-3 rounded text-red-500 text-sm mb-4">{error}</div>}

                    {/* Side & Leverage Selector (Only for Buy) */}
                    {isBuy && (
                        <div className="mb-6 space-y-4">
                            <div>
                                <label className="block text-xs text-zinc-400 mb-2 uppercase tracking-wider font-semibold">Side</label>
                                <div className="grid grid-cols-2 gap-2">
                                    <button
                                        type="button"
                                        onClick={() => setSide('LONG')}
                                        className={`py-2 rounded border text-sm font-bold transition-all ${side === 'LONG' ? 'bg-green-600 border-green-500 text-white' : 'bg-transparent text-zinc-500 border-zinc-700 hover:border-zinc-500'}`}
                                    >
                                        LONG
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => setSide('SHORT')}
                                        className={`py-2 rounded border text-sm font-bold transition-all ${side === 'SHORT' ? 'bg-red-600 border-red-500 text-white' : 'bg-transparent text-zinc-500 border-zinc-700 hover:border-zinc-500'}`}
                                    >
                                        SHORT
                                    </button>
                                </div>
                            </div>
                            <div>
                                <label className="block text-xs text-zinc-400 mb-2 uppercase tracking-wider font-semibold">Leverage</label>
                                <div className="grid grid-cols-4 gap-2">
                                    {[1, 2, 5, 10].map((lev) => (
                                        <button
                                            key={lev}
                                            type="button"
                                            onClick={() => setLeverage(lev)}
                                            className={`py-2 rounded border text-sm font-bold transition-all ${leverage === lev ? 'bg-zinc-100 text-black border-white' : 'bg-transparent text-zinc-500 border-zinc-700 hover:border-zinc-500'}`}
                                        >
                                            {lev}x
                                        </button>
                                    ))}
                                </div>
                            </div>
                        </div>
                    )}

                    <form onSubmit={handleTrade} className="space-y-6 flex-1 flex flex-col">
                        <div>
                            <label className="block text-xs text-zinc-400 mb-1 uppercase tracking-wider font-semibold">Target Portfolio</label>
                            <select
                                value={portfolioId}
                                onChange={(e) => setPortfolioId(e.target.value)}
                                className="w-full bg-black border border-zinc-700 rounded p-3 text-white outline-none focus:border-green-500 appearance-none cursor-pointer hover:border-zinc-600 transition-colors"
                            >
                                {portfolios.map(p => (
                                    <option key={p.id} value={p.id}>{p.name} ({isBuy ? 'Bal: $' + (p.balance?.toLocaleString() ?? 0) : 'Positions Available'})</option>
                                ))}
                            </select>
                        </div>

                        <div>
                            <label className="block text-xs text-zinc-400 mb-1 uppercase tracking-wider font-semibold">Quantity</label>
                            <div className="relative">
                                <input
                                    type="number"
                                    step="0.00000001"
                                    value={quantity}
                                    onChange={(e) => setQuantity(e.target.value)}
                                    className={`w-full bg-black border border-zinc-700 rounded p-3 text-white outline-none focus:border-${isBuy ? 'green' : 'red'}-500 text-lg font-mono`}
                                    placeholder="0.00"
                                    required
                                />
                                <span className="absolute right-4 top-1/2 -translate-y-1/2 text-zinc-500 text-sm font-bold">{symbol}</span>
                            </div>
                        </div>

                        <div className="bg-zinc-800/50 rounded p-4 border border-zinc-800 mt-auto">
                            <div className="flex justify-between text-sm mb-2 text-zinc-400">
                                <span>Market Price</span>
                                <span>${price.toLocaleString()}</span>
                            </div>

                            {isBuy && leverage > 1 && (
                                <div className="flex justify-between text-sm mb-2 text-zinc-400">
                                    <span>Notional Value</span>
                                    <span>${cost.toLocaleString(undefined, { maximumFractionDigits: 0 })}</span>
                                </div>
                            )}

                            {isBuy && leverage > 1 && (
                                <div className="flex justify-between text-xs mb-2 text-red-400 font-semibold">
                                    <span>Est. Liquidation Price</span>
                                    <span>
                                        ${(side === 'LONG'
                                            ? price * (1 - 1 / leverage)
                                            : price * (1 + 1 / leverage)
                                        ).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                    </span>
                                </div>
                            )}

                            <div className="border-t border-zinc-700 my-2"></div>
                            <div className="flex justify-between text-lg font-bold text-white">
                                <span>{isBuy ? 'Required Margin' : 'Est. Received'}</span>
                                <span className={isBuy ? 'text-zinc-200' : 'text-green-500'}>
                                    ${(isBuy ? margin : cost).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                </span>
                            </div>
                        </div>

                        <button
                            type="submit"
                            disabled={loading || portfolios.length === 0}
                            className={`w-full py-4 rounded text-white font-bold disabled:opacity-50 disabled:cursor-not-allowed transition-all shadow-lg mt-4 ${isBuy ? 'bg-green-600 hover:bg-green-500 shadow-green-900/20' : 'bg-red-600 hover:bg-red-500 shadow-red-900/20'}`}
                        >
                            {loading ? 'Processing...' : (isBuy ? 'Confirm Buy' : 'Confirm Sell')}
                        </button>
                    </form>
                </div>
            </div>
        </div>
    );
}
