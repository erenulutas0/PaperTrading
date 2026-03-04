'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { extractContent } from '../../lib/page';
import { apiFetch } from '../../lib/api-client';

interface PublicPortfolio {
    id: string;
    name: string;
    description: string | null;
    balance: number;
    ownerId: string;
    createdAt: string;
    items?: {
        id: string;
        symbol: string;
        quantity: number;
        averagePrice: number;
        leverage: number;
        side: 'LONG' | 'SHORT';
    }[];
}

export default function DiscoverPage() {
    const [portfolios, setPortfolios] = useState<PublicPortfolio[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        fetchDiscoverPortfolios();
    }, []);

    const fetchDiscoverPortfolios = async () => {
        try {
            const res = await apiFetch('/api/v1/portfolios/discover');
            if (res.ok) {
                const data = await res.json();
                setPortfolios(extractContent<PublicPortfolio>(data));
            }
        } catch (err) {
            console.error(err);
        } finally {
            setLoading(false);
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
                    <Link href="/dashboard/leaderboard" className="hover:text-white transition-colors">Leaderboard</Link>
                    <Link href="/discover" className="text-white">Discover</Link>
                </div>
            </nav>

            <div className="max-w-5xl mx-auto px-6 py-10">
                {/* Header */}
                <div className="mb-8">
                    <h1 className="text-3xl font-bold mb-2">
                        🔍 Discover <span className="text-green-400">Portfolios</span>
                    </h1>
                    <p className="text-zinc-400 text-sm">
                        Explore publicly shared portfolios. Every trade is timestamped and verifiable.
                    </p>
                </div>

                {loading ? (
                    <div className="flex justify-center py-20">
                        <div className="animate-spin w-8 h-8 border-2 border-green-500 border-t-transparent rounded-full"></div>
                    </div>
                ) : portfolios.length === 0 ? (
                    <div className="text-center py-20 border border-dashed border-white/10 rounded-xl">
                        <p className="text-zinc-500 text-lg mb-2">No public portfolios yet</p>
                        <p className="text-zinc-600 text-sm">Be the first to share your portfolio!</p>
                    </div>
                ) : (
                    <div className="grid gap-4 md:grid-cols-2">
                        {portfolios.map(p => (
                            <Link
                                key={p.id}
                                href={`/dashboard/portfolio/${p.id}`}
                                className="border border-white/10 rounded-xl p-6 hover:border-green-500/30 hover:bg-white/[0.02] transition-all group"
                            >
                                <div className="flex items-center justify-between mb-3">
                                    <h3 className="font-semibold text-lg group-hover:text-green-400 transition-colors">
                                        {p.name}
                                    </h3>
                                    <span className="text-xs bg-green-500/10 text-green-400 px-2 py-0.5 rounded-full border border-green-500/20">
                                        🌍 Public
                                    </span>
                                </div>
                                {p.description && (
                                    <p className="text-sm text-zinc-400 mb-3 line-clamp-2">{p.description}</p>
                                )}
                                <div className="flex items-center justify-between">
                                    <div className="flex gap-4 text-xs text-zinc-500">
                                        <span className="font-mono">${p.balance?.toLocaleString('en-US', { minimumFractionDigits: 2 })}</span>
                                        <span>•</span>
                                        <span>{p.items?.length || 0} positions</span>
                                    </div>
                                    <div className="flex gap-4">
                                        <button
                                            onClick={async (e) => {
                                                e.preventDefault();
                                                e.stopPropagation();
                                                try {
                                                    const userId = localStorage.getItem('userId');
                                                    if (!userId) {
                                                        alert('Please sign in first.');
                                                        return;
                                                    }
                                                    const res = await apiFetch(`/api/v1/portfolios/${p.id}/join`, {
                                                        method: 'POST',
                                                        headers: {
                                                            'Content-Type': 'application/json'
                                                        }
                                                    });
                                                    if (res.ok) {
                                                        const data = await res.json();
                                                        alert(`Subscribed! Created copy portfolio ${data.clonedPortfolioId}`);
                                                    } else {
                                                        const txt = await res.text();
                                                        alert(`Failed to subscribe: ${txt}`);
                                                    }
                                                } catch (e) {
                                                    console.error(e);
                                                }
                                            }}
                                            className="text-xs bg-emerald-500/10 text-emerald-400 hover:bg-emerald-500/20 px-3 py-1 rounded-full font-bold transition-all border border-emerald-500/20 shadow-[0_0_10px_rgba(16,185,129,0.1)] hover:shadow-[0_0_15px_rgba(16,185,129,0.2)]"
                                        >
                                            <span className="mr-1">⚡</span> COPY
                                        </button>
                                        <Link
                                            href={`/profile/${p.ownerId}`}
                                            onClick={(e) => e.stopPropagation()}
                                            className="text-xs text-zinc-500 hover:text-green-400 transition-colors flex items-center"
                                        >
                                            View Trader →
                                        </Link>
                                    </div>
                                </div>
                                <div className="mt-3 pt-3 border-t border-white/5 text-xs text-zinc-600">
                                    Created {new Date(p.createdAt).toLocaleDateString()}
                                </div>
                            </Link>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}
