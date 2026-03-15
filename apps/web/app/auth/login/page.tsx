'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { apiFetch } from '../../../lib/api-client';
import { storeAuthSession } from '../../../lib/auth-storage';

export default function LoginPage() {
    const router = useRouter();
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const handleLogin = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setLoading(true);

        try {
            const res = await apiFetch('/api/v1/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, password }),
            });

            if (!res.ok) {
                setError('Invalid credentials');
                return;
            }

            const user = await res.json();
            storeAuthSession(user);
            router.push('/dashboard');
        } catch {
            setError('Something went wrong');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen bg-background text-foreground noise-bg flex items-center justify-center px-4">
            <div className="grid w-full max-w-5xl gap-6 lg:grid-cols-[0.9fr_1.1fr]">
                <section className="glass-panel border border-border rounded-2xl p-8">
                    <p className="text-[11px] uppercase tracking-[0.32em] text-muted-foreground">Proof Of Performance</p>
                    <h1 className="mt-4 text-4xl font-black tracking-tight">Welcome back</h1>
                    <p className="mt-3 text-sm leading-7 text-muted-foreground">
                        Sign back into your paper-only terminal, social track record, trust score, and auditable research workflow.
                    </p>
                    <div className="mt-8 space-y-4">
                        <div className="rounded-xl border border-border bg-background/60 p-4">
                            <p className="text-xs uppercase tracking-wide text-muted-foreground">What persists</p>
                            <p className="mt-2 text-sm leading-6 text-foreground/85">
                                Terminal layouts, chart notes, trust history, analytics snapshots, and leaderboard context stay tied to the account.
                            </p>
                        </div>
                        <div className="rounded-xl border border-border bg-background/60 p-4">
                            <p className="text-xs uppercase tracking-wide text-muted-foreground">Why sign in matters</p>
                            <p className="mt-2 text-sm leading-6 text-foreground/85">
                                Auth is not just session gating. It binds accountability, publish history, and portfolio analytics to one identity.
                            </p>
                        </div>
                    </div>
                </section>

                <section className="glass-panel border border-border rounded-2xl p-8">
                    <div className="mb-6">
                        <h2 className="text-3xl font-bold">Sign in</h2>
                        <p className="text-muted-foreground mt-1">Access dashboard, market terminal, analytics, and profile state.</p>
                    </div>

                    {error && (
                        <div className="mb-4 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-red-300">
                            {error}
                        </div>
                    )}

                    <form onSubmit={handleLogin} className="space-y-4">
                        <div>
                            <label className="mb-1 block text-sm text-muted-foreground">Email</label>
                            <input
                                type="email"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                className="w-full rounded-lg border border-input bg-input-background p-3 text-foreground focus:outline-none focus:border-primary"
                                required
                            />
                        </div>
                        <div>
                            <label className="mb-1 block text-sm text-muted-foreground">Password</label>
                            <input
                                type="password"
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                className="w-full rounded-lg border border-input bg-input-background p-3 text-foreground focus:outline-none focus:border-primary"
                                required
                            />
                        </div>
                        <button
                            type="submit"
                            disabled={loading}
                            className="w-full rounded-lg bg-gradient-to-r from-primary to-secondary py-3 font-semibold text-primary-foreground hover:opacity-90 disabled:opacity-60"
                        >
                            {loading ? 'Signing in...' : 'Sign In'}
                        </button>
                    </form>

                    <div className="mt-6 rounded-xl border border-border bg-background/60 p-4">
                        <p className="text-xs uppercase tracking-wide text-muted-foreground">Session note</p>
                        <p className="mt-2 text-sm leading-6 text-foreground/80">
                            Login restores account-backed terminal preferences and keeps your analytics, notes, and layouts aligned across sessions.
                        </p>
                    </div>

                    <p className="mt-6 text-center text-sm text-muted-foreground">
                        Don&apos;t have an account?{' '}
                        <Link href="/auth/register" className="text-primary hover:underline">
                            Register
                        </Link>
                    </p>
                </section>
            </div>
        </div>
    );
}
