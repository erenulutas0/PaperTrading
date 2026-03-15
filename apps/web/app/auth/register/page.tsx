'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { apiFetch } from '../../../lib/api-client';
import { storeAuthSession } from '../../../lib/auth-storage';

export default function RegisterPage() {
    const router = useRouter();
    const [email, setEmail] = useState('');
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

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
            <div className="grid w-full max-w-5xl gap-6 lg:grid-cols-[0.95fr_1.05fr]">
                <section className="glass-panel border border-border rounded-2xl p-8">
                    <p className="text-[11px] uppercase tracking-[0.32em] text-muted-foreground">Start Clean</p>
                    <h1 className="mt-4 text-4xl font-black tracking-tight">Create account</h1>
                    <p className="mt-3 text-sm leading-7 text-muted-foreground">
                        Build a public performance record without real capital, silent edits, or unverifiable hindsight.
                    </p>
                    <div className="mt-8 space-y-4">
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
                    </div>
                </section>

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

                    <div className="mt-6 rounded-xl border border-primary/20 bg-primary/5 p-4">
                        <p className="text-xs uppercase tracking-wide text-primary/80">What happens next</p>
                        <p className="mt-2 text-sm leading-6 text-foreground/80">
                            After signup you land in the dashboard with session state initialized, ready to open portfolios, publish analysis, and use the market terminal immediately.
                        </p>
                    </div>

                    <p className="mt-6 text-center text-sm text-muted-foreground">
                        Already have an account?{' '}
                        <Link href="/auth/login" className="text-primary hover:underline">
                            Login
                        </Link>
                    </p>
                </section>
            </div>
        </div>
    );
}
