'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { apiFetch } from '../../../../lib/api-client';

export default function NewAnalysis() {
    const router = useRouter();
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const [formData, setFormData] = useState({
        title: '',
        content: '',
        instrumentSymbol: 'BTCUSDT',
        direction: 'BULLISH',
        targetPrice: '',
        stopPrice: '',
        targetDays: '7',
        timeframe: '1W'
    });

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setError('');

        const userId = localStorage.getItem('userId');
        if (!userId) {
            router.push('/auth/login');
            return;
        }

        try {
            const res = await apiFetch('/api/v1/analysis-posts', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    ...formData,
                    targetPrice: formData.targetPrice ? parseFloat(formData.targetPrice) : null,
                    stopPrice: formData.stopPrice ? parseFloat(formData.stopPrice) : null,
                    targetDays: parseInt(formData.targetDays)
                })
            });

            if (res.ok) {
                router.push('/dashboard/analysis');
            } else {
                const data = await res.json();
                setError(data.message || 'Failed to create post');
            }
        } catch {
            setError('Connection failed. Please try again.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen bg-background text-foreground">
            <div className="noise" />
            <div className="relative z-10 max-w-4xl mx-auto px-4 py-8 space-y-6">
                <header className="space-y-2">
                    <Link href="/dashboard/analysis" className="text-sm text-muted-foreground hover:text-foreground transition-colors inline-block">
                        ← Back to Analysis Hub
                    </Link>
                    <h1 className="text-3xl font-bold tracking-tight">Post Analysis</h1>
                    <p className="text-sm text-muted-foreground">
                        Share your thesis. Posts are immutable and server-timestamped.
                    </p>
                </header>

                <section className="grid gap-4 lg:grid-cols-[1.3fr_0.7fr]">
                    <div className="glass-panel rounded-2xl border border-border/80 p-6">
                        <p className="text-[11px] uppercase tracking-[0.32em] text-muted-foreground">Before You Publish</p>
                        <h2 className="mt-3 text-2xl font-black tracking-tight">Write the thesis as if you cannot touch it again.</h2>
                        <p className="mt-3 text-sm leading-7 text-muted-foreground">
                            The system fixes the creation timestamp, stores the initial market price, and resolves target or stop outcomes automatically.
                        </p>
                    </div>
                    <div className="glass-panel rounded-2xl border border-primary/20 bg-primary/5 p-6">
                        <p className="text-[11px] uppercase tracking-[0.32em] text-primary/80">Locked In At Publish</p>
                        <ul className="mt-4 space-y-3 text-sm leading-6 text-foreground/85">
                            <li>Instrument and directional stance</li>
                            <li>Target and stop thresholds</li>
                            <li>Server-side timestamp and reference price</li>
                        </ul>
                    </div>
                </section>

                <div className="glass-panel border border-border/80 rounded-2xl p-6 md:p-8">
                    <form onSubmit={handleSubmit} className="space-y-6">
                    {error && (
                        <div className="rounded-xl border border-destructive/35 bg-destructive/10 p-3 text-sm text-destructive">
                            {error}
                        </div>
                    )}

                    <div className="space-y-2">
                        <label className="text-xs font-medium text-muted-foreground">Title</label>
                        <input
                            type="text"
                            placeholder="e.g. BTC Support Level Breakout"
                            className="w-full rounded-xl border border-border bg-input-background p-3 text-foreground outline-none transition focus:border-primary/50"
                            value={formData.title}
                            onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                            required
                        />
                    </div>

                    <div className="grid gap-4 md:grid-cols-2">
                        <div className="space-y-2">
                            <label className="text-xs font-medium text-muted-foreground">Instrument</label>
                            <select
                                className="w-full rounded-xl border border-border bg-input-background p-3 text-foreground outline-none transition focus:border-primary/50"
                                value={formData.instrumentSymbol}
                                onChange={(e) => setFormData({ ...formData, instrumentSymbol: e.target.value })}
                            >
                                <option value="BTCUSDT">BTC/USDT</option>
                                <option value="ETHUSDT">ETH/USDT</option>
                                <option value="SOLUSDT">SOL/USDT</option>
                                <option value="AVAXUSDT">AVAX/USDT</option>
                                <option value="BNBUSDT">BNB/USDT</option>
                            </select>
                        </div>
                        <div className="space-y-2">
                            <label className="text-xs font-medium text-muted-foreground">Direction</label>
                            <div className="grid grid-cols-3 gap-2">
                                {(['BULLISH', 'BEARISH', 'NEUTRAL'] as const).map(dir => (
                                    <button
                                        key={dir}
                                        type="button"
                                        onClick={() => setFormData({ ...formData, direction: dir })}
                                        className={`rounded-lg border px-3 py-2 text-xs font-semibold transition ${formData.direction === dir
                                                ? dir === 'BULLISH'
                                                    ? 'border-success/40 bg-success/15 text-success'
                                                    : dir === 'BEARISH'
                                                        ? 'border-destructive/40 bg-destructive/15 text-destructive'
                                                        : 'border-primary/40 bg-primary/15 text-primary'
                                                : 'border-border bg-background/50 text-muted-foreground hover:text-foreground'
                                            }`}
                                    >
                                        {dir}
                                    </button>
                                ))}
                            </div>
                        </div>
                    </div>

                    <div className="space-y-2">
                        <label className="text-xs font-medium text-muted-foreground">Analysis</label>
                        <textarea
                            rows={7}
                            placeholder="Explain your technical or fundamental reasoning..."
                            className="w-full resize-none rounded-xl border border-border bg-input-background p-3 text-foreground outline-none transition focus:border-primary/50"
                            value={formData.content}
                            onChange={(e) => setFormData({ ...formData, content: e.target.value })}
                            required
                        />
                        <p className="text-xs text-muted-foreground">
                            Keep it specific. Explain why the setup exists, what invalidates it, and what timeline the market should respect.
                        </p>
                    </div>

                    <div className="grid gap-4 md:grid-cols-3">
                        <div className="space-y-2">
                            <label className="text-xs font-medium text-muted-foreground">Target Price</label>
                            <input
                                type="number"
                                step="any"
                                placeholder="$"
                                className="w-full rounded-xl border border-border bg-input-background p-3 text-foreground outline-none transition focus:border-primary/50"
                                value={formData.targetPrice}
                                onChange={(e) => setFormData({ ...formData, targetPrice: e.target.value })}
                            />
                        </div>
                        <div className="space-y-2">
                            <label className="text-xs font-medium text-muted-foreground">Stop Loss</label>
                            <input
                                type="number"
                                step="any"
                                placeholder="$"
                                className="w-full rounded-xl border border-border bg-input-background p-3 text-foreground outline-none transition focus:border-primary/50"
                                value={formData.stopPrice}
                                onChange={(e) => setFormData({ ...formData, stopPrice: e.target.value })}
                            />
                        </div>
                        <div className="space-y-2">
                            <label className="text-xs font-medium text-muted-foreground">Horizon (days)</label>
                            <input
                                type="number"
                                className="w-full rounded-xl border border-border bg-input-background p-3 text-foreground outline-none transition focus:border-primary/50"
                                value={formData.targetDays}
                                onChange={(e) => setFormData({ ...formData, targetDays: e.target.value })}
                                required
                            />
                        </div>
                    </div>

                    <div className="grid gap-4 md:grid-cols-2">
                        <div className="rounded-xl border border-border bg-background/60 p-4">
                            <p className="text-xs uppercase tracking-wide text-muted-foreground">Resolution Hint</p>
                            <p className="mt-2 text-sm leading-6 text-foreground/80">
                                If you set a target or stop, the post can resolve to <span className="font-semibold text-success">HIT</span>, <span className="font-semibold text-destructive">MISSED</span>, or <span className="font-semibold text-warning">EXPIRED</span> without manual intervention.
                            </p>
                        </div>
                        <div className="rounded-xl border border-border bg-background/60 p-4">
                            <p className="text-xs uppercase tracking-wide text-muted-foreground">Quality Bar</p>
                            <p className="mt-2 text-sm leading-6 text-foreground/80">
                                Avoid generic calls. Strong posts tie price thesis, invalidation, and time horizon together so later accuracy is interpretable.
                            </p>
                        </div>
                    </div>

                    <div className="rounded-xl border border-warning/35 bg-warning/10 p-3 text-sm text-warning">
                        Important: once posted, this analysis cannot be edited or deleted.
                    </div>

                    <button
                        type="submit"
                        disabled={loading}
                        className="w-full rounded-xl bg-gradient-to-r from-primary to-secondary px-4 py-3 font-semibold text-primary-foreground transition hover:opacity-90 disabled:opacity-50"
                    >
                        {loading ? 'Publishing...' : 'Publish Analysis'}
                    </button>
                    <p className="text-center text-xs text-muted-foreground">
                        Immutable. Timestamped. Auditable.
                    </p>
                    </form>
                </div>
            </div>
        </div>
    );
}
