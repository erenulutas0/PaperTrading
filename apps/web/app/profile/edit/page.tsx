'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { apiFetch, userIdHeaders } from '../../../lib/api-client';

interface EditableProfile {
    id: string;
    username: string;
    displayName: string | null;
    bio: string | null;
    avatarUrl: string | null;
}

function EditProfileLoadingShell() {
    return (
        <div className="min-h-screen bg-background text-foreground">
            <div className="noise" />
            <div className="relative z-10 max-w-3xl mx-auto px-4 py-8 space-y-6">
                <div className="flex items-center justify-between">
                    <div className="h-4 w-28 animate-pulse rounded bg-white/10" />
                    <div className="h-4 w-24 animate-pulse rounded bg-white/10" />
                </div>
                <div className="glass-panel rounded-2xl border border-border/80 p-6">
                    <div className="h-8 w-48 animate-pulse rounded bg-white/10" />
                    <div className="mt-3 h-4 w-64 animate-pulse rounded bg-white/5" />
                    <div className="mt-6 space-y-4">
                        <div className="h-12 rounded-xl animate-pulse bg-white/5" />
                        <div className="h-24 rounded-xl animate-pulse bg-white/5" />
                        <div className="h-12 rounded-xl animate-pulse bg-white/5" />
                    </div>
                </div>
            </div>
        </div>
    );
}

export default function EditProfilePage() {
    const router = useRouter();
    const [profile, setProfile] = useState<EditableProfile | null>(null);
    const [displayName, setDisplayName] = useState('');
    const [bio, setBio] = useState('');
    const [avatarUrl, setAvatarUrl] = useState('');
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState<string | null>(null);

    const hydrateProfile = useCallback(async () => {
        const userId = window.localStorage.getItem('userId');
        if (!userId) {
            router.push('/auth/login');
            return;
        }

        try {
            const response = await apiFetch(`/api/v1/users/${userId}/profile`, {
                headers: userIdHeaders(userId),
            });
            if (!response.ok) {
                throw new Error(`Failed to load profile (${response.status})`);
            }

            const payload: EditableProfile = await response.json();
            setProfile(payload);
            setDisplayName(payload.displayName ?? '');
            setBio(payload.bio ?? '');
            setAvatarUrl(payload.avatarUrl ?? '');
        } catch (fetchError) {
            console.error(fetchError);
            setError('Profile could not be loaded. Try refreshing.');
        } finally {
            setLoading(false);
        }
    }, [router]);

    useEffect(() => {
        void hydrateProfile();
    }, [hydrateProfile]);

    const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (!profile) {
            return;
        }

        setSaving(true);
        setError(null);
        setSuccess(null);

        try {
            const response = await apiFetch(`/api/v1/users/${profile.id}/profile`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    ...userIdHeaders(profile.id),
                },
                body: JSON.stringify({
                    displayName: displayName.trim() || null,
                    bio: bio.trim() || null,
                    avatarUrl: avatarUrl.trim() || null,
                }),
            });

            if (!response.ok) {
                throw new Error(`Failed to update profile (${response.status})`);
            }

            setSuccess('Profile updated.');
            window.setTimeout(() => {
                router.push(`/profile/${profile.id}`);
            }, 600);
        } catch (submitError) {
            console.error(submitError);
            setError('Profile could not be saved. Try again.');
        } finally {
            setSaving(false);
        }
    };

    if (loading) {
        return <EditProfileLoadingShell />;
    }

    if (!profile) {
        return (
            <div className="min-h-screen bg-background text-foreground">
                <div className="relative z-10 max-w-3xl mx-auto px-4 py-12">
                    <div className="rounded-xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
                        <p className="font-medium text-foreground">Profile unavailable</p>
                        <p className="mt-2">The edit surface could not load your profile.</p>
                    </div>
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-background text-foreground">
            <div className="noise" />
            <div className="relative z-10 max-w-3xl mx-auto px-4 py-8 space-y-6">
                <header className="flex items-center justify-between">
                    <Link href={`/profile/${profile.id}`} className="text-sm text-muted-foreground hover:text-foreground transition-colors">
                        ← Back to Profile
                    </Link>
                    <Link href="/dashboard" className="text-sm text-primary hover:text-primary/80 transition-colors">
                        Dashboard
                    </Link>
                </header>

                <section className="glass-panel rounded-2xl border border-border/80 p-6">
                    <div className="flex flex-col gap-2">
                        <p className="text-xs uppercase tracking-[0.24em] text-muted-foreground">Profile Settings</p>
                        <h1 className="text-2xl font-bold">Edit Public Profile</h1>
                        <p className="text-sm text-muted-foreground">
                            Update the identity and context other users see on your public profile surface.
                        </p>
                    </div>

                    <form onSubmit={handleSubmit} className="mt-6 space-y-4">
                        <div>
                            <label className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Username</label>
                            <div className="mt-2 rounded-xl border border-border bg-background/60 px-4 py-3 text-sm text-muted-foreground">
                                @{profile.username}
                            </div>
                        </div>

                        <div>
                            <label htmlFor="displayName" className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Display Name</label>
                            <input
                                id="displayName"
                                value={displayName}
                                onChange={(event) => setDisplayName(event.target.value)}
                                maxLength={80}
                                className="mt-2 w-full rounded-xl border border-border bg-background/60 px-4 py-3 text-sm text-foreground outline-none transition-colors focus:border-primary/40"
                                placeholder="How your profile should appear publicly"
                            />
                        </div>

                        <div>
                            <label htmlFor="bio" className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Bio</label>
                            <textarea
                                id="bio"
                                value={bio}
                                onChange={(event) => setBio(event.target.value)}
                                maxLength={500}
                                rows={5}
                                className="mt-2 w-full rounded-xl border border-border bg-background/60 px-4 py-3 text-sm text-foreground outline-none transition-colors focus:border-primary/40"
                                placeholder="Describe your style, focus, or track record context"
                            />
                            <p className="mt-2 text-xs text-muted-foreground">{bio.length}/500</p>
                        </div>

                        <div>
                            <label htmlFor="avatarUrl" className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Avatar URL</label>
                            <input
                                id="avatarUrl"
                                value={avatarUrl}
                                onChange={(event) => setAvatarUrl(event.target.value)}
                                className="mt-2 w-full rounded-xl border border-border bg-background/60 px-4 py-3 text-sm text-foreground outline-none transition-colors focus:border-primary/40"
                                placeholder="https://..."
                            />
                        </div>

                        {error ? (
                            <div className="rounded-xl border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-200">
                                {error}
                            </div>
                        ) : null}
                        {success ? (
                            <div className="rounded-xl border border-emerald-500/20 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-200">
                                {success}
                            </div>
                        ) : null}

                        <div className="flex flex-wrap gap-3 pt-2">
                            <button
                                type="submit"
                                disabled={saving}
                                className="rounded-xl border border-primary/30 bg-primary/15 px-5 py-3 text-sm font-semibold text-primary transition hover:bg-primary/25 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                                {saving ? 'Saving...' : 'Save Profile'}
                            </button>
                            <Link
                                href={`/profile/${profile.id}`}
                                className="rounded-xl border border-border bg-accent px-5 py-3 text-sm font-semibold text-foreground transition hover:border-primary/30"
                            >
                                Cancel
                            </Link>
                        </div>
                    </form>
                </section>
            </div>
        </div>
    );
}
