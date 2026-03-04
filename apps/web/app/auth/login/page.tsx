'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { apiFetch } from '../../../lib/api-client';

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
            localStorage.setItem('userId', user.id);
            localStorage.setItem('username', user.username);
            if (user.accessToken) {
                localStorage.setItem('accessToken', user.accessToken);
            }
            if (user.refreshToken) {
                localStorage.setItem('refreshToken', user.refreshToken);
            }
            router.push('/dashboard');
        } catch {
            setError('Something went wrong');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen bg-background text-foreground noise-bg flex items-center justify-center px-4">
            <div className="w-full max-w-md glass-panel border border-border rounded-2xl p-8">
                <div className="mb-6">
                    <h1 className="text-3xl font-bold">Welcome back</h1>
                    <p className="text-muted-foreground mt-1">Sign in to your trading terminal</p>
                </div>

                {error && (
                    <div className="mb-4 p-3 rounded-lg border border-destructive/30 bg-destructive/10 text-sm text-red-300">
                        {error}
                    </div>
                )}

                <form onSubmit={handleLogin} className="space-y-4">
                    <div>
                        <label className="block text-sm mb-1 text-muted-foreground">Email</label>
                        <input
                            type="email"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            className="w-full bg-input-background border border-input rounded-lg p-3 text-foreground focus:outline-none focus:border-primary"
                            required
                        />
                    </div>
                    <div>
                        <label className="block text-sm mb-1 text-muted-foreground">Password</label>
                        <input
                            type="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            className="w-full bg-input-background border border-input rounded-lg p-3 text-foreground focus:outline-none focus:border-primary"
                            required
                        />
                    </div>
                    <button
                        type="submit"
                        disabled={loading}
                        className="w-full bg-gradient-to-r from-primary to-secondary text-primary-foreground font-semibold py-3 rounded-lg hover:opacity-90 disabled:opacity-60"
                    >
                        {loading ? 'Signing in...' : 'Sign In'}
                    </button>
                </form>

                <p className="mt-6 text-sm text-muted-foreground text-center">
                    Don&apos;t have an account?{' '}
                    <Link href="/auth/register" className="text-primary hover:underline">
                        Register
                    </Link>
                </p>
            </div>
        </div>
    );
}
