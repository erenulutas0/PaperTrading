'use client';

import { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { useRouter, useParams } from 'next/navigation';
import LikeCommentWidget from '../../../../components/LikeCommentWidget';
import { apiFetch } from '../../../../lib/api-client';

interface AnalysisPost {
    id: string;
    authorId: string;
    authorUsername: string;
    authorDisplayName: string;
    title: string;
    content: string;
    instrumentSymbol: string;
    direction: 'BULLISH' | 'BEARISH' | 'NEUTRAL';
    targetPrice: number | null;
    stopPrice: number | null;
    priceAtCreation: number;
    outcome: 'PENDING' | 'HIT' | 'MISSED' | 'EXPIRED';
    createdAt: string;
    authorTotalPosts: number;
    authorHitCount: number;
}

function AnalysisDetailSkeleton() {
    return (
        <div className="min-h-screen bg-background text-foreground">
            <div className="noise" />
            <div className="relative z-10 max-w-5xl mx-auto px-4 py-8 space-y-6 animate-pulse">
                <div className="h-5 w-40 rounded bg-white/10" />
                <div className="rounded-2xl border border-border/80 bg-background/50 p-6 md:p-8">
                    <div className="h-8 w-2/3 rounded bg-white/10" />
                    <div className="mt-6 h-12 w-56 rounded bg-white/10" />
                    <div className="mt-8 h-40 rounded-xl bg-white/10" />
                    <div className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                        {[1, 2, 3, 4].map((item) => (
                            <div key={item} className="h-24 rounded-xl bg-white/10" />
                        ))}
                    </div>
                </div>
                <div className="h-40 rounded-2xl border border-border/80 bg-background/50" />
            </div>
        </div>
    );
}

export default function AnalysisDetail() {
    const params = useParams();
    const id = params.id as string;
    const [post, setPost] = useState<AnalysisPost | null>(null);
    const [loading, setLoading] = useState(true);
    const [myUserId, setMyUserId] = useState('');
    const router = useRouter();

    const fetchPost = useCallback(async () => {
        try {
            const res = await apiFetch(`/api/v1/analysis-posts/${id}`);
            if (res.ok) {
                const data = await res.json();
                setPost(data);
            }
        } catch (error) {
            console.error('Failed to fetch post', error);
        } finally {
            setLoading(false);
        }
    }, [id]);

    useEffect(() => {
        setMyUserId(localStorage.getItem('userId') || '');
        fetchPost();
    }, [fetchPost]);

    const handleDelete = async () => {
        if (!confirm('Are you sure you want to delete this analysis?')) return;

        try {
            const res = await apiFetch(`/api/v1/analysis-posts/${id}`, {
                method: 'DELETE'
            });
            if (res.ok) {
                router.push('/dashboard/analysis');
            }
        } catch (error) {
            console.error('Error deleting post:', error);
        }
    };

    if (loading) return <AnalysisDetailSkeleton />;
    if (!post) {
        return (
            <div className="min-h-screen bg-background text-foreground">
                <div className="noise" />
                <div className="relative z-10 mx-auto flex max-w-3xl flex-col items-center px-4 py-24 text-center">
                    <p className="text-[11px] uppercase tracking-[0.32em] text-muted-foreground">Analysis Missing</p>
                    <h1 className="mt-4 text-3xl font-black tracking-tight">This thesis is not available.</h1>
                    <p className="mt-3 max-w-xl text-sm leading-7 text-muted-foreground">
                        The post may have been tombstoned or the reference may be invalid, but immutable analysis history is not silently erased.
                    </p>
                    <Link
                        href="/dashboard/analysis"
                        className="mt-6 rounded-xl border border-border bg-accent px-5 py-3 text-sm font-semibold transition hover:border-primary/35 hover:text-primary"
                    >
                        Return To Analysis Hub
                    </Link>
                </div>
            </div>
        );
    }

    const accuracy = post.authorTotalPosts > 0 ? Math.round((post.authorHitCount / post.authorTotalPosts) * 100) : 0;

    return (
        <div className="min-h-screen bg-background text-foreground">
            <div className="noise" />
            <div className="relative z-10 max-w-5xl mx-auto px-4 py-8 space-y-6">
                <header className="flex items-center justify-between">
                    <Link href="/dashboard/analysis" className="text-sm text-muted-foreground hover:text-foreground transition-colors">
                        ← Back to Analysis Hub
                    </Link>
                    {post.authorId === myUserId && (
                        <button
                            onClick={handleDelete}
                            className="rounded-lg border border-destructive/35 bg-destructive/10 px-3 py-1.5 text-xs font-semibold uppercase tracking-wide text-destructive transition hover:bg-destructive/15"
                        >
                            Delete
                        </button>
                    )}
                </header>

                <article className="glass-panel rounded-2xl border border-border/80 p-6 md:p-8">
                    <div className="space-y-6">
                        <div className="flex flex-wrap items-start justify-between gap-4">
                            <div className="space-y-3">
                                <h1 className="text-3xl md:text-4xl font-bold tracking-tight">{post.title}</h1>
                                <Link href={`/profile/${post.authorId}`} className="inline-flex items-center gap-3 group">
                                    <div className="h-11 w-11 rounded-full border border-border bg-accent flex items-center justify-center font-semibold">
                                        {post.authorUsername[0].toUpperCase()}
                                    </div>
                                    <div>
                                        <p className="text-sm font-semibold group-hover:text-primary transition-colors">
                                            {post.authorDisplayName || post.authorUsername}
                                        </p>
                                        <p className="text-xs text-muted-foreground">
                                            @{post.authorUsername} · {accuracy}% accuracy
                                        </p>
                                    </div>
                                </Link>
                            </div>
                            <p className="text-xs text-muted-foreground">
                                {new Date(post.createdAt).toLocaleString()}
                            </p>
                        </div>

                        <div className="flex flex-wrap items-center gap-2">
                            <span className="rounded-full border border-border bg-accent px-3 py-1 text-xs font-medium">
                                {post.instrumentSymbol}
                            </span>
                            <span className={`rounded-full border px-3 py-1 text-xs font-medium ${post.direction === 'BULLISH'
                                ? 'border-success/35 bg-success/10 text-success'
                                : post.direction === 'BEARISH'
                                    ? 'border-destructive/35 bg-destructive/10 text-destructive'
                                    : 'border-border bg-accent text-muted-foreground'
                                }`}>
                                {post.direction}
                            </span>
                            <span className={`rounded-full border px-3 py-1 text-xs font-medium ${post.outcome === 'HIT'
                                ? 'border-success/35 bg-success/10 text-success'
                                : post.outcome === 'MISSED'
                                    ? 'border-destructive/35 bg-destructive/10 text-destructive'
                                    : 'border-warning/35 bg-warning/10 text-warning'
                                }`}>
                                {post.outcome}
                            </span>
                            <span className="rounded-full border border-primary/35 bg-primary/10 px-3 py-1 text-xs font-medium text-primary">
                                Immutable
                            </span>
                            <span className="rounded-full border border-border bg-accent px-3 py-1 text-xs font-medium text-muted-foreground">
                                Server Timestamped
                            </span>
                        </div>

                        <div className="grid gap-4 lg:grid-cols-[1.25fr_0.75fr]">
                            <div className="rounded-2xl border border-border bg-background/60 p-5">
                                <p className="text-[11px] uppercase tracking-[0.32em] text-muted-foreground">Thesis Body</p>
                                <p className="mt-4 text-base leading-relaxed text-foreground/90 whitespace-pre-wrap">
                                    {post.content}
                                </p>
                            </div>
                            <div className="rounded-2xl border border-primary/20 bg-primary/5 p-5">
                                <p className="text-[11px] uppercase tracking-[0.32em] text-primary/80">Resolution Semantics</p>
                                <ul className="mt-4 space-y-3 text-sm leading-6 text-foreground/80">
                                    <li>Entry price is fixed at post creation and cannot be backfilled.</li>
                                    <li>Target and stop are checked by system outcome resolution, not by author edits.</li>
                                    <li>Delete only hides the surface; the accountability trail remains.</li>
                                </ul>
                            </div>
                        </div>

                        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                            <div className="rounded-xl border border-border bg-background/60 p-4">
                                <p className="text-xs uppercase tracking-wide text-muted-foreground">Entry Price</p>
                                <p className="mt-2 text-lg font-semibold">${post.priceAtCreation?.toLocaleString()}</p>
                            </div>
                            <div className="rounded-xl border border-border bg-background/60 p-4">
                                <p className="text-xs uppercase tracking-wide text-muted-foreground">Target Price</p>
                                <p className="mt-2 text-lg font-semibold text-success">
                                    {post.targetPrice != null ? `$${post.targetPrice.toLocaleString()}` : 'N/A'}
                                </p>
                            </div>
                            <div className="rounded-xl border border-border bg-background/60 p-4">
                                <p className="text-xs uppercase tracking-wide text-muted-foreground">Stop Price</p>
                                <p className="mt-2 text-lg font-semibold text-destructive">
                                    {post.stopPrice != null ? `$${post.stopPrice.toLocaleString()}` : 'N/A'}
                                </p>
                            </div>
                            <div className="rounded-xl border border-border bg-background/60 p-4">
                                <p className="text-xs uppercase tracking-wide text-muted-foreground">Author Accuracy</p>
                                <p className="mt-2 text-lg font-semibold">{accuracy}%</p>
                            </div>
                        </div>

                        <div className="grid gap-3 md:grid-cols-3">
                            <div className="rounded-xl border border-border bg-background/60 p-4">
                                <p className="text-xs uppercase tracking-wide text-muted-foreground">Author Hit Count</p>
                                <p className="mt-2 text-lg font-semibold">{post.authorHitCount}</p>
                            </div>
                            <div className="rounded-xl border border-border bg-background/60 p-4">
                                <p className="text-xs uppercase tracking-wide text-muted-foreground">Published Analyses</p>
                                <p className="mt-2 text-lg font-semibold">{post.authorTotalPosts}</p>
                            </div>
                            <div className="rounded-xl border border-border bg-background/60 p-4">
                                <p className="text-xs uppercase tracking-wide text-muted-foreground">Audit Status</p>
                                <p className="mt-2 text-lg font-semibold text-primary">Append Only</p>
                            </div>
                        </div>
                    </div>
                </article>

                <section className="glass-panel rounded-2xl border border-border/80 p-6">
                    <h2 className="mb-4 text-lg font-semibold">Community Discussion</h2>
                    <LikeCommentWidget targetId={post.id} targetType="ANALYSIS_POST" />
                </section>
            </div>
        </div>
    );
}
