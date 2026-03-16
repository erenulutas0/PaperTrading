'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { apiFetch } from '../../../lib/api-client';
import { storeAuthSession } from '../../../lib/auth-storage';

type RegisterWorkspaceTab = 'BASELINE' | 'CREATE';

export default function RegisterPage() {
    const router = useRouter();
    const [email, setEmail] = useState('');
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const [workspaceTab, setWorkspaceTab] = useState<RegisterWorkspaceTab>('BASELINE');

    const handleRegister = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');

        if (password !== confirmPassword) {
            setError('Passwords do not match');
            return;
        }

        setLoading(true);
        try {
            const res = await apiFetch('/api/v1/auth/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, username, password }),
            });

            if (!res.ok) {
                const message = await res.text();
                setError(message || 'Registration failed');
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
                            <h1 className="mt-3 text-4xl font-black tracking-tight">Create account</h1>
                            <p className="mt-3 max-w-3xl text-sm leading-7 text-muted-foreground">
                                Separate identity rules from the actual signup step so the account-creation surface stays intentional instead of reading like one long onboarding wall.
                            </p>
                        </div>
                        <div className="flex flex-wrap gap-2">
                            {([
                                { key: 'BASELINE', label: 'Baseline', badge: 'Rules' },
                                { key: 'CREATE', label: 'Create', badge: username ? 'In Progress' : 'Ready' },
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

                {workspaceTab === 'BASELINE' && (
                    <div className="grid gap-6 lg:grid-cols-[0.95fr_1.05fr]">
                        <section className="glass-panel border border-border rounded-2xl p-8">
                            <p className="text-[11px] uppercase tracking-[0.32em] text-muted-foreground">Start Clean</p>
                            <h2 className="mt-4 text-4xl font-black tracking-tight">Open an identity with a real accountability trail</h2>
                            <p className="mt-3 text-sm leading-7 text-muted-foreground">
                                Build a public performance record without real capital, silent edits, or unverifiable hindsight.
                            </p>
                        </section>

                        <section className="grid gap-4 md:grid-cols-2">
                            <div className="rounded-xl border border-border bg-background/60 p-4">
                                <p className="text-xs uppercase tracking-wide text-muted-foreground">Account baseline</p>
                                <p className="mt-2 text-sm leading-6 text-foreground/85">
                                    Registration creates the identity that owns your portfolios, analysis posts, trust history, and terminal state.
                                </p>
                            </div>
                            <div className="rounded-xl border border-border bg-background/60 p-4">
                                <p className="text-xs uppercase tracking-wide text-muted-foreground">Research rules</p>
                                <p className="mt-2 text-sm leading-6 text-foreground/85">
                                    Analysis posts are immutable, outcome resolution is automated, and leaderboard position follows verified performance.
                                </p>
                            </div>
                            <div className="rounded-xl border border-primary/20 bg-primary/5 p-4 md:col-span-2">
                                <p className="text-xs uppercase tracking-wide text-primary/80">What happens next</p>
                                <p className="mt-2 text-sm leading-6 text-foreground/80">
                                    After signup you land in the dashboard with session state initialized, ready to open portfolios, publish analysis, and use the market terminal immediately.
                                </p>
                                <button
                                    type="button"
                                    onClick={() => setWorkspaceTab('CREATE')}
                                    className="mt-4 rounded-xl border border-border bg-background/60 px-4 py-2 text-sm font-semibold transition hover:border-primary/35 hover:text-primary"
                                >
                                    Open Signup
                                </button>
                            </div>
                        </section>
                    </div>
                )}

                {workspaceTab === 'CREATE' && (
                    <section className="glass-panel border border-border rounded-2xl p-8">
                        <div className="mb-6">
                            <h2 className="text-3xl font-bold">Open an account</h2>
                            <p className="text-muted-foreground mt-1">Set the identity that will carry your public track record.</p>
                        </div>

                        {error && (
                            <div className="mb-4 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-red-300">
                                {error}
                            </div>
                        )}

                        <form onSubmit={handleRegister} className="space-y-4">
                            <div>
                                <label className="mb-1 block text-sm text-muted-foreground">Username</label>
                                <input
                                    type="text"
                                    value={username}
                                    onChange={(e) => setUsername(e.target.value)}
                                    className="w-full rounded-lg border border-input bg-input-background p-3 text-foreground focus:outline-none focus:border-primary"
                                    required
                                />
                            </div>
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
                            <div>
                                <label className="mb-1 block text-sm text-muted-foreground">Confirm Password</label>
                                <input
                                    type="password"
                                    value={confirmPassword}
                                    onChange={(e) => setConfirmPassword(e.target.value)}
                                    className="w-full rounded-lg border border-input bg-input-background p-3 text-foreground focus:outline-none focus:border-primary"
                                    required
                                />
                            </div>
                            <button
                                type="submit"
                                disabled={loading}
                                className="w-full rounded-lg bg-gradient-to-r from-primary to-secondary py-3 font-semibold text-primary-foreground hover:opacity-90 disabled:opacity-60"
                            >
                                {loading ? 'Creating account...' : 'Sign Up'}
                            </button>
                        </form>

                        <p className="mt-6 text-center text-sm text-muted-foreground">
                            Already have an account?{' '}
                            <Link href="/auth/login" className="text-primary hover:underline">
                                Login
                            </Link>
                        </p>
                    </section>
                )}
            </div>
        </div>
    );
}
