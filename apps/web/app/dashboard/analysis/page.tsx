'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import LikeCommentWidget from '../../../components/LikeCommentWidget';
import { apiFetch } from '../../../lib/api-client';

interface AnalysisPost {
    id: string;
    title: string;
    content: string;
    instrumentSymbol: string;
    direction: 'BULLISH' | 'BEARISH' | 'NEUTRAL';
    targetPrice: number | null;
    stopPrice: number | null;
    priceAtCreation: number;
    outcome: 'PENDING' | 'HIT' | 'MISSED' | 'EXPIRED';
    authorUsername: string;
    authorDisplayName: string;
    authorAccuracy: number;
    createdAt: string;
}

function AnalysisSummaryCard({
    label,
    value,
    tone = 'default'
}: {
    label: string;
    value: string;
    tone?: 'default' | 'success' | 'warning';
}) {
    const toneClass = tone === 'success'
        ? 'text-emerald-300 border-emerald-500/20 bg-emerald-500/10'
        : tone === 'warning'
            ? 'text-amber-300 border-amber-500/20 bg-amber-500/10'
            : 'text-zinc-100 border-white/10 bg-white/5';

    return (
        <div className={`rounded-2xl border p-4 ${toneClass}`}>
            <p className="text-[10px] uppercase tracking-[0.28em] text-zinc-500">{label}</p>
            <p className="mt-3 text-2xl font-black">{value}</p>
        </div>
    );
}

export default function AnalysisHub() {
    const [posts, setPosts] = useState<AnalysisPost[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        fetchPosts();
    }, []);

    const fetchPosts = async () => {
        try {
            const res = await apiFetch('/api/v1/analysis-posts/feed');
            if (res.ok) {
                const data = await res.json();
                setPosts(data.content || []);
            }
        } catch (error) {
            console.error(error);
        } finally {
            setLoading(false);
        }
    };

    const pendingCount = posts.filter((post) => post.outcome === 'PENDING').length;
    const resolvedCount = posts.filter((post) => post.outcome !== 'PENDING').length;
    const hitCount = posts.filter((post) => post.outcome === 'HIT').length;

    return (
        <div className="p-8 pb-20 relative z-10 w-full max-w-[1400px] mx-auto">
            <header className="flex justify-between items-center bg-black/40 backdrop-blur-xl border border-white/10 p-6 rounded-2xl shadow-2xl relative overflow-hidden mb-12">
                <div className="absolute top-0 right-0 w-64 h-64 bg-teal-500/10 rounded-full blur-[100px] pointer-events-none"></div>
                <div className="relative z-10">
                    <h1 className="text-4xl font-extrabold bg-gradient-to-r from-teal-400 to-emerald-500 bg-clip-text text-transparent">Analysis Hub</h1>
                    <p className="text-zinc-500 mt-2 font-mono text-sm uppercase tracking-widest">Verified insights from the community.</p>
                </div>
                <div className="flex gap-4 items-center relative z-10">
                    <Link href="/dashboard/analysis/new" className="bg-emerald-600/90 hover:bg-emerald-500 text-white font-bold py-2 px-6 rounded-xl border border-emerald-500/50 transition-all shadow-[0_0_15px_rgba(16,185,129,0.2)]">
                        + New Analysis
                    </Link>
                    <Link href="/dashboard" className="text-zinc-400 hover:text-white py-2 px-4 transition-colors text-sm uppercase tracking-[0.2em] font-bold">Terminal</Link>
                </div>
            </header>

            <section className="mb-8 grid gap-4 md:grid-cols-4">
                <AnalysisSummaryCard label="Visible Theses" value={loading ? '...' : posts.length.toString()} />
                <AnalysisSummaryCard label="Pending" value={loading ? '...' : pendingCount.toString()} tone="warning" />
                <AnalysisSummaryCard label="Resolved" value={loading ? '...' : resolvedCount.toString()} />
                <AnalysisSummaryCard label="Hits" value={loading ? '...' : hitCount.toString()} tone="success" />
            </section>

            <section className="mb-8 grid gap-4 lg:grid-cols-[1.35fr_0.65fr]">
                <div className="rounded-3xl border border-white/10 bg-black/35 p-6 backdrop-blur-xl">
                    <p className="text-[11px] uppercase tracking-[0.35em] text-zinc-500">Immutable Analysis Flow</p>
                    <h2 className="mt-3 text-2xl font-black text-white">Every thesis is locked to the server clock.</h2>
                    <p className="mt-3 max-w-3xl text-sm leading-7 text-zinc-400">
                        Posts are timestamped, outcome resolution is automated, and the feed keeps both conviction and accountability visible.
                    </p>
                </div>
                <div className="rounded-3xl border border-white/10 bg-black/35 p-6 backdrop-blur-xl">
                    <p className="text-[11px] uppercase tracking-[0.35em] text-zinc-500">What To Expect</p>
                    <ul className="mt-4 space-y-3 text-sm text-zinc-300">
                        <li>Server timestamp decides when the thesis was actually published.</li>
                        <li>Target and stop resolution stay visible after the post is live.</li>
                        <li>Delete does not erase history; it only tombstones the post.</li>
                    </ul>
                </div>
            </section>

            {loading ? (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
                    {[1, 2, 3].map(i => (
                        <div key={i} className="rounded-2xl border border-white/10 bg-zinc-900/50 p-6 animate-pulse">
                            <div className="h-4 w-24 rounded bg-white/10" />
                            <div className="mt-6 h-7 w-3/4 rounded bg-white/10" />
                            <div className="mt-4 h-16 rounded bg-white/10" />
                            <div className="mt-6 space-y-3">
                                <div className="h-10 rounded bg-white/10" />
                                <div className="h-10 rounded bg-white/10" />
                            </div>
                        </div>
                    ))}
                </div>
            ) : posts.length === 0 ? (
                <div className="rounded-3xl border border-dashed border-zinc-700 bg-zinc-900/20 px-6 py-16 text-center">
                    <p className="text-[11px] uppercase tracking-[0.35em] text-zinc-500">Analysis Feed Empty</p>
                    <h2 className="mt-4 text-2xl font-black text-white">No published thesis yet.</h2>
                    <p className="mx-auto mt-3 max-w-2xl text-sm leading-7 text-zinc-400">
                        The first post will establish a timestamped reference point for accuracy, target resolution, and author track record.
                    </p>
                    <Link href="/dashboard/analysis/new" className="mt-6 inline-flex rounded-xl border border-emerald-500/35 bg-emerald-500/10 px-5 py-3 text-sm font-bold text-emerald-300 transition hover:bg-emerald-500/20">
                        Publish First Analysis
                    </Link>
                </div>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
                    {posts.map((post) => (
                        <div key={post.id} className="bg-black/40 backdrop-blur-xl border border-white/10 rounded-2xl p-6 hover:border-emerald-500/30 hover:shadow-[0_0_30px_rgba(16,185,129,0.1)] transition-all duration-300 flex flex-col h-full relative overflow-hidden group">
                            <div className="absolute top-0 left-0 w-32 h-32 bg-teal-500/5 rounded-full blur-3xl pointer-events-none"></div>

                            {/* Direction Badge */}
                            <div className="absolute top-0 right-0 relative z-10">
                                <span className={`px-4 py-1.5 text-[10px] font-black uppercase tracking-widest rounded-bl-2xl ${post.direction === 'BULLISH' ? 'bg-green-500/20 text-green-400 border-b border-l border-green-500/30' :
                                    post.direction === 'BEARISH' ? 'bg-red-500/20 text-red-400 border-b border-l border-red-500/30' : 'bg-zinc-700/50 text-white border-b border-l border-zinc-600/50'
                                    }`}>
                                    {post.direction}
                                </span>
                            </div>

                            <div className="flex items-center gap-3 mb-4 relative z-10">
                                <div className="w-10 h-10 rounded-full bg-white/5 flex items-center justify-center font-bold text-zinc-400 border border-white/10">
                                    {post.authorUsername[0].toUpperCase()}
                                </div>
                                <div>
                                    <p className="text-sm font-bold text-zinc-100">{post.authorDisplayName}</p>
                                    <p className="text-[10px] text-zinc-500 font-mono tracking-widest">@{post.authorUsername} • {post.authorAccuracy}% Accuracy</p>
                                </div>
                            </div>

                            <h2 className="text-lg font-bold mb-2 line-clamp-1 group-hover:text-emerald-400 transition-colors relative z-10">{post.title}</h2>
                            <p className="text-xs text-zinc-400 line-clamp-3 mb-6 flex-1 italic leading-relaxed relative z-10">&ldquo;{post.content}&rdquo;</p>

                            <div className="space-y-3 bg-white/5 p-4 rounded-xl border border-white/5 relative z-10">
                                <div className="flex justify-between items-center text-[10px] font-bold">
                                    <span className="text-zinc-500 uppercase">Instrument</span>
                                    <span className="text-white bg-zinc-800 px-2 py-0.5 rounded">{post.instrumentSymbol}</span>
                                </div>
                                <div className="flex justify-between items-center text-[10px] font-bold">
                                    <span className="text-zinc-500 uppercase">Entry Price</span>
                                    <span className="text-zinc-300 font-mono">${post.priceAtCreation?.toLocaleString()}</span>
                                </div>
                                <div className="flex justify-between items-center text-[10px] font-bold">
                                    <span className="text-zinc-500 uppercase">Target Price</span>
                                    <span className="text-green-400 font-mono">${post.targetPrice?.toLocaleString() || 'N/A'}</span>
                                </div>
                            </div>

                            <div className="mt-6 flex justify-between items-end">
                                <div className="flex flex-col">
                                    <span className="text-[9px] text-zinc-600 uppercase font-bold mb-1">Status</span>
                                    <span className={`text-[10px] font-black uppercase tracking-tighter ${post.outcome === 'HIT' ? 'text-green-500' :
                                        post.outcome === 'MISSED' ? 'text-red-500' : 'text-yellow-500'
                                        }`}>
                                        ● {post.outcome}
                                    </span>
                                </div>
                                <Link
                                    href={`/dashboard/analysis/${post.id}`}
                                    className="text-[10px] font-bold text-zinc-400 group-hover:text-white transition-all underline underline-offset-4"
                                >
                                    Full Thesis &rarr;
                                </Link>
                            </div>

                            {/* Social Interactions */}
                            <LikeCommentWidget targetId={post.id} targetType="ANALYSIS_POST" />
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}
