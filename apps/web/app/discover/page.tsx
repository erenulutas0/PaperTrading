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

type DiscoverWorkspaceTab = 'OVERVIEW' | 'FEED';

export default function DiscoverPage() {
    const [portfolios, setPortfolios] = useState<PublicPortfolio[]>([]);
    const [loading, setLoading] = useState(true);
    const [workspaceTab, setWorkspaceTab] = useState<DiscoverWorkspaceTab>('OVERVIEW');

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

    const publicPositionCount = portfolios.reduce((total, portfolio) => total + (portfolio.items?.length ?? 0), 0);

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
                <div className="mb-8">
                    <h1 className="text-3xl font-bold mb-2">
                        Discover <span className="text-green-400">Portfolios</span>
                    </h1>
                    <p className="text-zinc-400 text-sm">
                        Explore publicly shared portfolios. Every trade is timestamped and verifiable.
                    </p>
                </div>

                <section className="mb-8 rounded-2xl border border-white/10 bg-white/[0.02] p-6">
                    <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                        <div>
                            <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Discover Workspace</p>
                            <h2 className="mt-2 text-2xl font-black text-white">Separate public-market context from the live portfolio feed.</h2>
                            <p className="mt-3 max-w-3xl text-sm leading-7 text-zinc-400">
                                Use overview to understand the public discovery layer, then switch into the feed when you want to inspect and copy specific portfolios.
                            </p>
                        </div>
                        <div className="flex flex-wrap gap-2">
                            {([
                                { key: 'OVERVIEW', label: 'Overview', badge: `${portfolios.length} public` },
                                { key: 'FEED', label: 'Feed', badge: `${publicPositionCount} positions` },
                            ] as const).map(({ key, label, badge }) => (
                                <button
                                    key={key}
                                    type="button"
                                    onClick={() => setWorkspaceTab(key)}
                                    className={`inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-semibold transition ${
                                        workspaceTab === key
                                            ? 'border-green-500/35 bg-green-500/15 text-green-300'
                                            : 'border-white/10 bg-white/5 text-zinc-400 hover:text-white'
                                    }`}
                                >
                                    <span>{label}</span>
                                    <span className={`rounded-full px-2 py-0.5 text-[10px] ${
                                        workspaceTab === key ? 'bg-green-500/15 text-green-200' : 'bg-black/30 text-zinc-500'
                                    }`}>
                                        {badge}
                                    </span>
                                </button>
                            ))}
                        </div>
                    </div>
                </section>

                {workspaceTab === 'OVERVIEW' && (
                    <div className="grid gap-4 md:grid-cols-[1.05fr_0.95fr]">
                        <section className="rounded-2xl border border-white/10 bg-white/[0.02] p-6">
                            <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Public Layer</p>
                            <h2 className="mt-3 text-2xl font-black text-white">Discover only shows portfolios that accepted public accountability.</h2>
                            <p className="mt-3 text-sm leading-7 text-zinc-400">
                                Every visible portfolio carries timestamped trades, balance history, and profile ownership. Discovery is not a hype feed; it is the public surface for verifiable paper performance.
                            </p>
                        </section>
                        <section className="rounded-2xl border border-white/10 bg-white/[0.02] p-6">
                            <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">What You Can Do</p>
                            <ul className="mt-4 space-y-3 text-sm leading-6 text-zinc-300">
                                <li>Inspect a public portfolio before opening its full history.</li>
                                <li>Copy a portfolio into your own account when you want a tracked clone.</li>
                                <li>Jump directly to the trader profile behind the published record.</li>
                            </ul>
                        </section>
                        <section className="rounded-2xl border border-white/10 bg-white/[0.02] p-6 md:col-span-2">
                            <div className="grid gap-4 md:grid-cols-3">
                                <div className="rounded-xl border border-white/5 bg-black/30 p-4">
                                    <p className="text-xs uppercase tracking-wide text-zinc-500">Public Portfolios</p>
                                    <p className="mt-2 text-2xl font-bold text-white">{loading ? '...' : portfolios.length}</p>
                                    <p className="mt-1 text-xs text-zinc-500">Visible records available for inspection.</p>
                                </div>
                                <div className="rounded-xl border border-white/5 bg-black/30 p-4">
                                    <p className="text-xs uppercase tracking-wide text-zinc-500">Public Positions</p>
                                    <p className="mt-2 text-2xl font-bold text-green-300">{loading ? '...' : publicPositionCount}</p>
                                    <p className="mt-1 text-xs text-zinc-500">Open positions currently exposed in discovery.</p>
                                </div>
                                <div className="rounded-xl border border-white/5 bg-black/30 p-4">
                                    <p className="text-xs uppercase tracking-wide text-zinc-500">Next Step</p>
                                    <button
                                        type="button"
                                        onClick={() => setWorkspaceTab('FEED')}
                                        className="mt-2 rounded-lg border border-green-500/20 bg-green-500/10 px-3 py-2 text-xs font-bold uppercase tracking-[0.2em] text-green-300"
                                    >
                                        Open Feed
                                    </button>
                                    <p className="mt-2 text-xs text-zinc-500">Move into the live public portfolio list when you want actual candidates.</p>
                                </div>
                            </div>
                        </section>
                    </div>
                )}

                {workspaceTab === 'FEED' && (
                    loading ? (
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
                                            Public
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
                                                COPY
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
                    )
                )}
            </div>
        </div>
    );
}
