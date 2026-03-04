'use client';

import Link from 'next/link';
import { useState } from 'react';

export default function Home() {
  const [isLoggedIn] = useState(() =>
    typeof window !== 'undefined' ? Boolean(localStorage.getItem('userId')) : false,
  );

  return (
    <div className="min-h-screen bg-background text-foreground noise-bg">
      <header className="border-b border-border glass-panel sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 py-4 flex items-center justify-between">
          <Link href="/" className="flex items-center gap-2">
            <div className="w-8 h-8 bg-gradient-to-br from-primary to-secondary rounded-lg flex items-center justify-center text-background font-bold">
              P
            </div>
            <span className="text-xl font-bold bg-gradient-to-r from-primary to-secondary bg-clip-text text-transparent">
              PaperTradePro
            </span>
          </Link>
          <div className="flex items-center gap-3">
            {isLoggedIn ? (
              <Link href="/dashboard" className="px-4 py-2 rounded-lg bg-gradient-to-r from-primary to-secondary text-background font-semibold hover:opacity-90">
                Dashboard
              </Link>
            ) : (
              <>
                <Link href="/auth/login" className="px-4 py-2 rounded-lg text-muted-foreground hover:text-foreground hover:bg-accent">
                  Login
                </Link>
                <Link href="/auth/register" className="px-4 py-2 rounded-lg bg-gradient-to-r from-primary to-secondary text-background font-semibold hover:opacity-90">
                  Get Started
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      <main>
        <section className="max-w-7xl mx-auto px-4 py-16 md:py-24">
          <div className="max-w-4xl mx-auto text-center space-y-6">
            <div className="inline-flex items-center gap-2 px-4 py-2 bg-primary/10 border border-primary/20 rounded-full text-sm text-primary">
              Paper Trading Only • Social Accountability
            </div>
            <h1 className="text-4xl md:text-6xl font-bold leading-tight">
              Master Trading with
              <br />
              <span className="bg-gradient-to-r from-primary to-secondary bg-clip-text text-transparent">
                Verifiable Performance
              </span>
            </h1>
            <p className="text-xl text-muted-foreground max-w-2xl mx-auto">
              Build portfolios, share immutable analysis, and compete in tournaments with timestamped outcomes.
            </p>
            <div className="flex flex-col sm:flex-row items-center justify-center gap-4 pt-2">
              <Link href={isLoggedIn ? '/dashboard' : '/auth/register'} className="px-6 py-3 rounded-lg bg-gradient-to-r from-primary to-secondary text-background font-semibold hover:opacity-90 w-full sm:w-auto">
                {isLoggedIn ? 'Go to Terminal' : 'Start Trading Free'}
              </Link>
              <Link href="/discover" className="px-6 py-3 rounded-lg border border-border hover:bg-accent text-foreground w-full sm:w-auto">
                Discover Portfolios
              </Link>
            </div>
          </div>
        </section>

        <section className="max-w-7xl mx-auto px-4 py-12 grid md:grid-cols-3 gap-6">
          <article className="p-6 glass-panel rounded-2xl">
            <h3 className="text-xl font-bold mb-2">Immutable Analysis</h3>
            <p className="text-muted-foreground">Posts are timestamped by the server and cannot be silently rewritten.</p>
          </article>
          <article className="p-6 glass-panel rounded-2xl">
            <h3 className="text-xl font-bold mb-2">Live Market Engine</h3>
            <p className="text-muted-foreground">Paper positions track live market prices for realistic outcomes.</p>
          </article>
          <article className="p-6 glass-panel rounded-2xl">
            <h3 className="text-xl font-bold mb-2">Verified Leaderboards</h3>
            <p className="text-muted-foreground">Performance windows are computed from snapshots, not self-reported claims.</p>
          </article>
        </section>
      </main>
    </div>
  );
}
