'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { apiFetch } from '../../../lib/api-client';
import { storeAuthSession } from '../../../lib/auth-storage';

type LoginWorkspaceTab = 'CONTEXT' | 'ACCESS';

export default function LoginPage() {
    const router = useRouter();
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const [workspaceTab, setWorkspaceTab] = useState<LoginWorkspaceTab>('CONTEXT');

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
            <div className="w-full max-w-5xl space-y-6">
                <section className="glass-panel border border-border rounded-2xl p-8">
                    <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                        <div>
                            <p className="text-[11px] uppercase tracking-[0.32em] text-muted-foreground">Auth Workspace</p>
                            <h1 className="mt-3 text-4xl font-black tracking-tight">Welcome back</h1>
                            <p className="mt-3 max-w-3xl text-sm leading-7 text-muted-foreground">
                                Separate account context from the actual sign-in flow so the access step stays short and deliberate.
                            </p>
                        </div>
                        <div className="flex flex-wrap gap-2">
                            {([
                                { key: 'CONTEXT', label: 'Context', badge: 'Why It Matters' },
                                { key: 'ACCESS', label: 'Access', badge: email ? 'In Progress' : 'Ready' },
                            ] as const).map(({ key, label, badge }) => (
                                <button
                                    key={key}
                                    type="button"
                                    onClick={() => setWorkspaceTab(key)}
                                    className={`inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-semibold transition ${
                                        workspaceTab === key
                                            ? 'border-primary/35 bg-primary/15 text-primary'
                                            : 'border-border bg-accent text-muted-foreground hover:text-foreground'
                                    }`}
                                >
                                    <span>{label}</span>
                                    <span className={`rounded-full px-2 py-0.5 text-[10px] ${
                                        workspaceTab === key ? 'bg-primary/15 text-primary' : 'bg-background/70 text-muted-foreground'
                                    }`}>
                                        {badge}
                                    </span>
                                </button>
                            ))}
                        </div>
                    </div>
                </section>

                {workspaceTab === 'CONTEXT' && (
                    <div className="grid gap-6 lg:grid-cols-[0.9fr_1.1fr]">
                        <section className="glass-panel border border-border rounded-2xl p-8">
                            <p className="text-[11px] uppercase tracking-[0.32em] text-muted-foreground">Proof Of Performance</p>
                            <h2 className="mt-4 text-4xl font-black tracking-tight">Return to your verified workspace</h2>
                            <p className="mt-3 text-sm leading-7 text-muted-foreground">
                                Sign back into your paper-only terminal, social track record, trust score, and auditable research workflow.
                            </p>
                        </section>

                        <section className="grid gap-4 md:grid-cols-2">
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
                            <div className="rounded-xl border border-border bg-background/60 p-4 md:col-span-2">
                                <p className="text-xs uppercase tracking-wide text-muted-foreground">Session note</p>
                                <p className="mt-2 text-sm leading-6 text-foreground/80">
                                    Login restores account-backed terminal preferences and keeps your analytics, notes, and layouts aligned across sessions.
                                </p>
                                <button
                                    type="button"
                                    onClick={() => setWorkspaceTab('ACCESS')}
                                    className="mt-4 rounded-xl border border-border bg-accent px-4 py-2 text-sm font-semibold transition hover:border-primary/35 hover:text-primary"
                                >
                                    Open Sign In
                                </button>
                            </div>
                        </section>
                    </div>
                )}

                {workspaceTab === 'ACCESS' && (
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

                        <p className="mt-6 text-center text-sm text-muted-foreground">
                            Don&apos;t have an account?{' '}
                            <Link href="/auth/register" className="text-primary hover:underline">
                                Register
                            </Link>
                        </p>
                    </section>
                )}
            </div>
        </div>
    );
}
