'use client';

import Link from 'next/link';

const sections = [
    {
        title: '1. Prediction Accuracy',
        body: 'Structured analysis posts with target, stop, and expiry are the strongest trust input. When the system resolves them as HIT, MISSED, or EXPIRED, they feed the prediction accuracy component.',
    },
    {
        title: '2. Trade Win Rate',
        body: 'Closed trades with realized P/L are counted as evidence. Consistently profitable closes help the score; consistently losing closes pull it down.',
    },
    {
        title: '3. Portfolio Quality',
        body: 'We look at how many of your portfolios are currently positive and at their average return. This keeps the score connected to actual portfolio behavior, not only posts.',
    },
    {
        title: '4. Sample Size',
        body: 'Small samples are discounted on purpose. A few lucky wins should not look highly credible. More resolved posts and more closed trades increase confidence gradually.',
    },
    {
        title: '5. Why It Starts Near 50',
        body: 'New or inactive accounts stay near a neutral baseline until the platform has enough evidence. Trust score is a credibility signal, not a popularity metric.',
    },
];

export default function TrustScorePage() {
    return (
        <div className="min-h-screen bg-background text-foreground">
            <div className="noise" />
            <div className="relative z-10 mx-auto max-w-5xl px-4 py-10 space-y-6">
                <header className="flex items-center justify-between">
                    <Link href="/dashboard" className="text-sm text-muted-foreground hover:text-foreground transition-colors">
                        ← Back to Dashboard
                    </Link>
                    <Link href="/dashboard/leaderboard" className="text-sm text-primary hover:text-primary/80 transition-colors">
                        Leaderboard
                    </Link>
                </header>

                <section className="glass-panel rounded-2xl border border-border/80 p-6">
                    <p className="text-xs uppercase tracking-[0.25em] text-muted-foreground">Documentation</p>
                    <h1 className="mt-3 text-3xl font-bold">How Trust Score Works</h1>
                    <p className="mt-4 max-w-3xl text-sm leading-6 text-muted-foreground">
                        Trust score combines prediction outcomes, realized trade quality, and live portfolio behavior.
                        It is intentionally sample-size-aware, so a tiny lucky streak does not look like durable skill.
                    </p>
                    <p className="mt-2 max-w-3xl text-sm leading-6 text-muted-foreground">
                        The profile page also shows a platform win rate, which blends prediction accuracy, closed-trade quality, and profitable portfolio ratio.
                    </p>
                </section>

                <section className="grid gap-4 md:grid-cols-2">
                    {sections.map((section) => (
                        <article key={section.title} className="glass-panel rounded-2xl border border-border/80 p-5">
                            <h2 className="text-lg font-semibold">{section.title}</h2>
                            <p className="mt-3 text-sm leading-6 text-muted-foreground">{section.body}</p>
                        </article>
                    ))}
                </section>

                <section className="glass-panel rounded-2xl border border-border/80 p-6">
                    <h2 className="text-lg font-semibold">Scoring Principles</h2>
                    <ul className="mt-4 space-y-2 text-sm text-muted-foreground">
                        <li>Trust score is not follower count.</li>
                        <li>Unresolved analysis posts do not count yet.</li>
                        <li>More evidence increases confidence gradually, not instantly.</li>
                        <li>Profitable portfolio behavior helps, but cannot fully replace prediction accuracy.</li>
                        <li>Closed-trade quality matters more than raw activity volume.</li>
                    </ul>
                </section>
            </div>
        </div>
    );
}
