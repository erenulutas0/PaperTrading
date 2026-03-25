'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import LogoutButton from '../../../components/LogoutButton';
import { clearAuthSession } from '../../../lib/auth-storage';
import { apiFetch, userIdHeaders } from '../../../lib/api-client';
import {
    fetchTerminalLayouts,
    fetchUserPreferences,
    updateNotificationPreferences,
    type TerminalLayoutResponsePayload,
    type NotificationPreferencesResponsePayload,
    type UserPreferencesResponsePayload,
} from '../../../lib/user-preferences';

type SettingsWorkspaceTab = 'PROFILE' | 'TERMINAL' | 'SESSION' | 'ACCOUNT';

interface EditableProfile {
    id: string;
    username: string;
    displayName: string | null;
    bio: string | null;
    avatarUrl: string | null;
    memberSince: string;
    followerCount: number;
    followingCount: number;
    portfolioCount: number;
    trustScore?: number;
    winRate?: number;
}

const LOCAL_SETTINGS_CACHE_KEYS = [
    'market.terminal.session',
    'market.terminal.compare-baskets',
    'market.terminal.scanner-views',
    'market.favoriteSymbols',
    'dashboard_leaderboard_preferences_v1',
    'public_leaderboard_preferences_v1',
];

function SettingsLoadingShell() {
    return (
        <div className="min-h-screen bg-background text-foreground">
            <div className="noise" />
            <div className="relative z-10 mx-auto max-w-6xl px-4 py-8 space-y-6">
                <div className="flex items-center justify-between">
                    <div className="h-4 w-32 animate-pulse rounded bg-white/10" />
                    <div className="h-4 w-20 animate-pulse rounded bg-white/10" />
                </div>
                <div className="glass-panel rounded-2xl border border-border/80 p-6">
                    <div className="h-8 w-40 animate-pulse rounded bg-white/10" />
                    <div className="mt-4 flex flex-wrap gap-2">
                        {Array.from({ length: 4 }).map((_, index) => (
                            <div key={index} className="h-10 w-28 animate-pulse rounded-full bg-white/5" />
                        ))}
                    </div>
                </div>
                <div className="grid gap-4 lg:grid-cols-3">
                    {Array.from({ length: 3 }).map((_, index) => (
                        <div key={index} className="glass-panel rounded-2xl border border-border/80 p-6">
                            <div className="h-5 w-32 animate-pulse rounded bg-white/10" />
                            <div className="mt-3 h-20 animate-pulse rounded bg-white/5" />
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
}

function SettingsPanel({
    title,
    body,
    children,
    className,
}: {
    title: string;
    body?: string;
    children: React.ReactNode;
    className?: string;
}) {
    return (
        <section className={`glass-panel rounded-2xl border border-border/80 p-6 ${className ?? ''}`}>
            <div className="flex flex-col gap-1">
                <h2 className="text-lg font-semibold">{title}</h2>
                {body ? <p className="text-sm text-muted-foreground">{body}</p> : null}
            </div>
            <div className="mt-5">{children}</div>
        </section>
    );
}

function SettingsValueCard({
    label,
    value,
    detail,
}: {
    label: string;
    value: string;
    detail?: string;
}) {
    return (
        <article className="rounded-xl border border-border bg-background/60 p-4">
            <p className="text-[10px] uppercase tracking-[0.24em] text-muted-foreground">{label}</p>
            <p className="mt-2 text-lg font-semibold text-foreground">{value}</p>
            {detail ? <p className="mt-1 text-xs text-muted-foreground">{detail}</p> : null}
        </article>
    );
}

export default function DashboardSettingsPage() {
    const [workspaceTab, setWorkspaceTab] = useState<SettingsWorkspaceTab>('PROFILE');
    const [userId, setUserId] = useState<string | null>(null);
    const [profile, setProfile] = useState<EditableProfile | null>(null);
    const [preferences, setPreferences] = useState<UserPreferencesResponsePayload | null>(null);
    const [layouts, setLayouts] = useState<TerminalLayoutResponsePayload[]>([]);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState<string | null>(null);
    const [sessionSummary, setSessionSummary] = useState({
        username: 'Unknown',
        hasAccessToken: false,
        hasRefreshToken: false,
    });
    const [displayName, setDisplayName] = useState('');
    const [bio, setBio] = useState('');
    const [avatarUrl, setAvatarUrl] = useState('');
    const [notificationPrefs, setNotificationPrefs] = useState<NotificationPreferencesResponsePayload>({
        inApp: {
            social: true,
            watchlist: true,
            tournaments: true,
        },
        digestCadence: 'INSTANT',
        quietHours: {
            enabled: false,
            start: '22:00',
            end: '08:00',
        },
    });

    const loadSettings = useCallback(async () => {
        const localUserId = localStorage.getItem('userId');
        setUserId(localUserId);
        setSessionSummary({
            username: localStorage.getItem('username') ?? 'Unknown',
            hasAccessToken: Boolean(localStorage.getItem('accessToken')),
            hasRefreshToken: Boolean(localStorage.getItem('refreshToken')),
        });

        if (!localUserId) {
            setLoading(false);
            setError('You need an active session to open settings.');
            return;
        }

        try {
            const [profileRes, preferenceRes, layoutRes] = await Promise.all([
                apiFetch(`/api/v1/users/${localUserId}/profile`, {
                    headers: userIdHeaders(localUserId),
                    cache: 'no-store',
                }),
                fetchUserPreferences(localUserId),
                fetchTerminalLayouts(localUserId),
            ]);

            if (!profileRes.ok) {
                throw new Error(`Failed to load profile (${profileRes.status})`);
            }

            const profilePayload: EditableProfile = await profileRes.json();
            setProfile(profilePayload);
            setDisplayName(profilePayload.displayName ?? '');
            setBio(profilePayload.bio ?? '');
            setAvatarUrl(profilePayload.avatarUrl ?? '');
            setPreferences(preferenceRes);
            if (preferenceRes?.notification) {
                setNotificationPrefs(preferenceRes.notification);
            }
            setLayouts(layoutRes);
        } catch (loadError) {
            console.error(loadError);
            setError('Settings could not be loaded. Refresh and try again.');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        void loadSettings();
    }, [loadSettings]);

    const handleProfileSave = async (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (!profile || !userId) {
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
                    ...userIdHeaders(userId),
                },
                body: JSON.stringify({
                    displayName: displayName.trim() || null,
                    bio: bio.trim() || null,
                    avatarUrl: avatarUrl.trim() || null,
                }),
            });
            if (!response.ok) {
                throw new Error(`Failed to save profile (${response.status})`);
            }

            setProfile((current) => current ? {
                ...current,
                displayName: displayName.trim() || null,
                bio: bio.trim() || null,
                avatarUrl: avatarUrl.trim() || null,
            } : current);
            setSuccess('Profile settings saved.');
        } catch (saveError) {
            console.error(saveError);
            setError('Profile settings could not be saved.');
        } finally {
            setSaving(false);
        }
    };

    const terminal = preferences?.terminal;
    const notification = preferences?.notification ?? notificationPrefs;
    const compareBasketCount = terminal?.compareBaskets?.length ?? 0;
    const scannerViewCount = terminal?.scannerViews?.length ?? 0;
    const favoriteCount = terminal?.favoriteSymbols?.length ?? 0;
    const compareCount = terminal?.compareSymbols?.length ?? 0;

    const handleSaveNotificationPreferences = useCallback(async () => {
        if (!userId) {
            setError('You need an active session to save notification preferences.');
            return;
        }

        setSaving(true);
        setError(null);
        setSuccess(null);
        try {
            const updated = await updateNotificationPreferences(userId, notificationPrefs);
            if (!updated) {
                throw new Error('notification save failed');
            }
            setPreferences(updated);
            if (updated.notification) {
                setNotificationPrefs(updated.notification);
            }
            setSuccess('Notification preferences saved.');
        } catch (saveError) {
            console.error(saveError);
            setError('Notification preferences could not be saved.');
        } finally {
            setSaving(false);
        }
    }, [notificationPrefs, userId]);

    const handleCopyAccountSummary = useCallback(async () => {
        if (!profile) {
            setError('Account summary is not ready yet.');
            return;
        }
        const summary = [
            'Account Settings Snapshot',
            `User Id: ${profile.id}`,
            `Username: @${profile.username}`,
            `Display Name: ${profile.displayName ?? 'N/A'}`,
            `Member Since: ${profile.memberSince ? new Date(profile.memberSince).toLocaleDateString() : 'N/A'}`,
            `Followers: ${profile.followerCount}`,
            `Following: ${profile.followingCount}`,
            `Portfolios: ${profile.portfolioCount}`,
            `Trust Score: ${profile.trustScore !== undefined ? profile.trustScore.toFixed(1) : 'N/A'}`,
            `Terminal: ${terminal ? `${terminal.market} · ${terminal.symbol} · ${terminal.range}/${terminal.interval}` : 'N/A'}`,
            `Favorites: ${favoriteCount}`,
            `Compare Symbols: ${compareCount}`,
            `Compare Baskets: ${compareBasketCount}`,
            `Scanner Views: ${scannerViewCount}`,
            `Layouts: ${layouts.length}`,
            `Inbox Digest: ${notification ? notification.digestCadence : 'N/A'}`,
            `Quiet Hours: ${notification ? (notification.quietHours.enabled ? `${notification.quietHours.start}-${notification.quietHours.end}` : 'OFF') : 'N/A'}`,
            `Inbox Categories: ${notification ? `social=${notification.inApp.social}, watchlist=${notification.inApp.watchlist}, tournaments=${notification.inApp.tournaments}` : 'N/A'}`,
        ].join('\n');

        try {
            await navigator.clipboard.writeText(summary);
            setError(null);
            setSuccess('Account summary copied.');
        } catch (copyError) {
            console.error(copyError);
            setError('Account summary could not be copied.');
        }
    }, [compareBasketCount, compareCount, favoriteCount, layouts.length, notification, profile, scannerViewCount, terminal]);

    const handleExportSettingsSnapshot = useCallback(() => {
        if (!profile) {
            setError('Settings snapshot is not ready yet.');
            return;
        }
        const payload = {
            exportedAt: new Date().toISOString(),
            profile,
            preferences,
            layouts,
            sessionSummary,
        };

        try {
            const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
            const url = URL.createObjectURL(blob);
            const anchor = document.createElement('a');
            anchor.href = url;
            anchor.download = `settings-${profile.username}.json`;
            document.body.appendChild(anchor);
            anchor.click();
            anchor.remove();
            URL.revokeObjectURL(url);
            setError(null);
            setSuccess('Settings snapshot exported.');
        } catch (exportError) {
            console.error(exportError);
            setError('Settings snapshot could not be exported.');
        }
    }, [layouts, preferences, profile, sessionSummary]);

    const handleCopySessionSummary = useCallback(async () => {
        const summary = [
            'Browser Session Snapshot',
            `User Id: ${userId ?? 'N/A'}`,
            `Username: ${sessionSummary.username}`,
            `Access Token: ${sessionSummary.hasAccessToken ? 'Present' : 'Missing'}`,
            `Refresh Token: ${sessionSummary.hasRefreshToken ? 'Present' : 'Missing'}`,
            `Local Cache Keys: ${LOCAL_SETTINGS_CACHE_KEYS.length}`,
        ].join('\n');

        try {
            await navigator.clipboard.writeText(summary);
            setError(null);
            setSuccess('Session summary copied.');
        } catch (copyError) {
            console.error(copyError);
            setError('Session summary could not be copied.');
        }
    }, [sessionSummary, userId]);

    const handleClearLocalTerminalCache = useCallback(() => {
        for (const key of LOCAL_SETTINGS_CACHE_KEYS) {
            window.localStorage.removeItem(key);
        }
        setError(null);
        setSuccess('Local terminal and leaderboard cache cleared. Account-backed preferences remain on the server.');
    }, []);

    const handleClearBrowserSession = useCallback(() => {
        clearAuthSession();
        for (const key of LOCAL_SETTINGS_CACHE_KEYS) {
            window.localStorage.removeItem(key);
        }
        setSessionSummary({
            username: 'Unknown',
            hasAccessToken: false,
            hasRefreshToken: false,
        });
        setUserId(null);
        setError(null);
        setSuccess('Browser session cleared. Re-open login to start a fresh session.');
    }, []);

    if (loading) {
        return <SettingsLoadingShell />;
    }

    return (
        <div className="min-h-screen bg-background text-foreground">
            <div className="noise" />
            <div className="relative z-10 mx-auto max-w-6xl px-4 py-8 space-y-6">
                <header className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                        <p className="text-xs uppercase tracking-[0.28em] text-muted-foreground">Settings Workspace</p>
                        <h1 className="mt-2 text-3xl font-bold">Account And Terminal Settings</h1>
                        <p className="mt-2 text-sm text-muted-foreground">
                            Keep identity, session, and terminal state in one control surface instead of scattering them across profile and market views.
                        </p>
                    </div>
                    <div className="flex flex-wrap gap-2">
                        {profile ? (
                            <Link href={`/profile/${profile.id}`} className="rounded-xl border border-border bg-accent px-4 py-2 text-sm font-medium text-foreground transition hover:border-primary/30">
                                View Profile
                            </Link>
                        ) : null}
                        <Link href="/watchlist" className="rounded-xl border border-primary/30 bg-primary/10 px-4 py-2 text-sm font-medium text-primary transition hover:bg-primary/20">
                            Open Terminal
                        </Link>
                    </div>
                </header>

                <section className="glass-panel rounded-2xl border border-border/80 p-4">
                    <div className="flex flex-wrap gap-2">
                        {([
                            ['PROFILE', 'Profile'],
                            ['TERMINAL', 'Terminal'],
                            ['SESSION', 'Session'],
                            ['ACCOUNT', 'Account'],
                        ] as const).map(([value, label]) => (
                            <button
                                key={value}
                                type="button"
                                onClick={() => setWorkspaceTab(value)}
                                className={`rounded-full px-4 py-2 text-sm font-medium transition ${workspaceTab === value
                                    ? 'bg-primary/15 text-primary'
                                    : 'bg-background/60 text-muted-foreground hover:bg-accent hover:text-foreground'
                                    }`}
                            >
                                {label}
                            </button>
                        ))}
                    </div>
                </section>

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

                {workspaceTab === 'PROFILE' ? (
                    <div className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]">
                        <SettingsPanel
                            title="Public Profile"
                            body="This identity is visible across leaderboards, portfolio pages, and discussion surfaces."
                        >
                            <form onSubmit={handleProfileSave} className="space-y-4">
                                <div>
                                    <label className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Username</label>
                                    <div className="mt-2 rounded-xl border border-border bg-background/60 px-4 py-3 text-sm text-muted-foreground">
                                        @{profile?.username ?? 'unknown'}
                                    </div>
                                </div>
                                <div>
                                    <label htmlFor="settings-display-name" className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Display Name</label>
                                    <input
                                        id="settings-display-name"
                                        value={displayName}
                                        onChange={(event) => setDisplayName(event.target.value)}
                                        maxLength={80}
                                        className="mt-2 w-full rounded-xl border border-border bg-background/60 px-4 py-3 text-sm text-foreground outline-none transition-colors focus:border-primary/40"
                                        placeholder="How your profile appears publicly"
                                    />
                                </div>
                                <div>
                                    <label htmlFor="settings-bio" className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Bio</label>
                                    <textarea
                                        id="settings-bio"
                                        value={bio}
                                        onChange={(event) => setBio(event.target.value)}
                                        rows={5}
                                        maxLength={500}
                                        className="mt-2 w-full rounded-xl border border-border bg-background/60 px-4 py-3 text-sm text-foreground outline-none transition-colors focus:border-primary/40"
                                        placeholder="Describe style, focus, or proof-of-performance context"
                                    />
                                    <p className="mt-2 text-xs text-muted-foreground">{bio.length}/500</p>
                                </div>
                                <div>
                                    <label htmlFor="settings-avatar-url" className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Avatar URL</label>
                                    <input
                                        id="settings-avatar-url"
                                        value={avatarUrl}
                                        onChange={(event) => setAvatarUrl(event.target.value)}
                                        className="mt-2 w-full rounded-xl border border-border bg-background/60 px-4 py-3 text-sm text-foreground outline-none transition-colors focus:border-primary/40"
                                        placeholder="https://..."
                                    />
                                </div>
                                <div className="flex flex-wrap gap-3 pt-2">
                                    <button
                                        type="submit"
                                        disabled={saving}
                                        className="rounded-xl border border-primary/30 bg-primary/15 px-5 py-3 text-sm font-semibold text-primary transition hover:bg-primary/25 disabled:cursor-not-allowed disabled:opacity-60"
                                    >
                                        {saving ? 'Saving...' : 'Save Profile'}
                                    </button>
                                    {profile ? (
                                        <Link
                                            href={`/profile/${profile.id}`}
                                            className="rounded-xl border border-border bg-accent px-5 py-3 text-sm font-semibold text-foreground transition hover:border-primary/30"
                                        >
                                            Open Public Profile
                                        </Link>
                                    ) : null}
                                </div>
                            </form>
                        </SettingsPanel>

                        <SettingsPanel
                            title="Identity Summary"
                            body="Quick profile metrics and trust context from the current account record."
                        >
                            <div className="grid gap-3">
                                <SettingsValueCard label="Followers" value={`${profile?.followerCount ?? 0}`} />
                                <SettingsValueCard label="Following" value={`${profile?.followingCount ?? 0}`} />
                                <SettingsValueCard label="Portfolios" value={`${profile?.portfolioCount ?? 0}`} />
                                <SettingsValueCard
                                    label="Trust Score"
                                    value={profile?.trustScore !== undefined ? profile.trustScore.toFixed(1) : 'N/A'}
                                    detail={profile?.winRate !== undefined ? `Win rate ${profile.winRate.toFixed(1)}%` : 'Trust data will fill as evidence accrues.'}
                                />
                            </div>
                        </SettingsPanel>
                    </div>
                ) : null}

                {workspaceTab === 'TERMINAL' ? (
                    <div className="grid gap-6 xl:grid-cols-[1fr_1fr]">
                        <SettingsPanel
                            title="Terminal State"
                            body="The current account-backed market workspace baseline."
                        >
                            <div className="grid gap-3 sm:grid-cols-2">
                                <SettingsValueCard label="Market" value={terminal?.market ?? 'N/A'} />
                                <SettingsValueCard label="Symbol" value={terminal?.symbol ?? 'N/A'} />
                                <SettingsValueCard label="Range / Interval" value={terminal ? `${terminal.range} / ${terminal.interval}` : 'N/A'} />
                                <SettingsValueCard label="Compare" value={`${compareCount} symbols`} detail={terminal?.compareVisible ? 'Overlay visible' : 'Overlay hidden'} />
                                <SettingsValueCard label="Favorites" value={`${favoriteCount}`} />
                                <SettingsValueCard label="Layouts" value={`${layouts.length}`} />
                                <SettingsValueCard label="Compare Baskets" value={`${compareBasketCount}`} />
                                <SettingsValueCard label="Scanner Views" value={`${scannerViewCount}`} />
                            </div>
                            <div className="mt-5 flex flex-wrap gap-3">
                                <Link href="/watchlist" className="rounded-xl border border-primary/30 bg-primary/10 px-4 py-2 text-sm font-medium text-primary transition hover:bg-primary/20">
                                    Open Market Terminal
                                </Link>
                                <Link href="/dashboard/leaderboard" className="rounded-xl border border-border bg-accent px-4 py-2 text-sm font-medium text-foreground transition hover:border-primary/30">
                                    Open Leaderboard
                                </Link>
                            </div>
                        </SettingsPanel>

                        <SettingsPanel
                            title="Saved Terminal Assets"
                            body="Reusable state already attached to the account."
                        >
                            <div className="space-y-4">
                                <div className="rounded-xl border border-border bg-background/60 p-4">
                                    <p className="text-xs uppercase tracking-[0.24em] text-muted-foreground">Recent Layouts</p>
                                    <div className="mt-3 space-y-2">
                                        {layouts.length === 0 ? (
                                            <p className="text-sm text-muted-foreground">No saved layouts yet.</p>
                                        ) : layouts.slice(0, 5).map((layout) => (
                                            <div key={layout.id} className="flex items-center justify-between gap-3 rounded-lg border border-border/70 px-3 py-2 text-sm">
                                                <div className="min-w-0">
                                                    <p className="truncate font-medium text-foreground">{layout.name}</p>
                                                    <p className="text-xs text-muted-foreground">{layout.market} · {layout.symbol} · {layout.range}/{layout.interval}</p>
                                                </div>
                                                <span className="text-xs text-muted-foreground">{layout.compareSymbols.length} compare</span>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                                <div className="rounded-xl border border-border bg-background/60 p-4">
                                    <p className="text-xs uppercase tracking-[0.24em] text-muted-foreground">Preference Footprint</p>
                                    <div className="mt-3 flex flex-wrap gap-2">
                                        <span className="rounded-full border border-border px-3 py-1 text-xs text-muted-foreground">Favorites {favoriteCount}</span>
                                        <span className="rounded-full border border-border px-3 py-1 text-xs text-muted-foreground">Baskets {compareBasketCount}</span>
                                        <span className="rounded-full border border-border px-3 py-1 text-xs text-muted-foreground">Scanner Views {scannerViewCount}</span>
                                        <span className="rounded-full border border-border px-3 py-1 text-xs text-muted-foreground">Layouts {layouts.length}</span>
                                    </div>
                                </div>
                            </div>
                        </SettingsPanel>
                    </div>
                ) : null}

                {workspaceTab === 'SESSION' ? (
                    <div className="grid gap-6 xl:grid-cols-[1fr_1fr]">
                        <SettingsPanel
                            title="Session Identity"
                            body="What the current browser session holds for authenticated access."
                        >
                            <div className="grid gap-3 sm:grid-cols-2">
                                <SettingsValueCard label="User Id" value={userId ?? 'N/A'} />
                                <SettingsValueCard label="Username" value={sessionSummary.username} />
                                <SettingsValueCard label="Access Token" value={sessionSummary.hasAccessToken ? 'Present' : 'Missing'} />
                                <SettingsValueCard label="Refresh Token" value={sessionSummary.hasRefreshToken ? 'Present' : 'Missing'} />
                            </div>
                            <div className="mt-5 flex flex-wrap gap-3">
                                <LogoutButton className="rounded-xl border border-red-500/20 bg-red-500/10 px-4 py-2 text-sm font-medium text-red-200 transition hover:bg-red-500/15" label="Logout All Sessions" />
                                <Link href="/auth/login" className="rounded-xl border border-border bg-accent px-4 py-2 text-sm font-medium text-foreground transition hover:border-primary/30">
                                    Re-open Login
                                </Link>
                                <button
                                    type="button"
                                    onClick={() => void handleCopySessionSummary()}
                                    className="rounded-xl border border-primary/30 bg-primary/10 px-4 py-2 text-sm font-medium text-primary transition hover:bg-primary/20"
                                >
                                    Copy Session Summary
                                </button>
                                <button
                                    type="button"
                                    onClick={handleClearLocalTerminalCache}
                                    className="rounded-xl border border-border bg-accent px-4 py-2 text-sm font-medium text-foreground transition hover:border-primary/30"
                                >
                                    Clear Local Cache
                                </button>
                                <button
                                    type="button"
                                    onClick={handleClearBrowserSession}
                                    className="rounded-xl border border-red-500/20 bg-red-500/10 px-4 py-2 text-sm font-medium text-red-200 transition hover:bg-red-500/15"
                                >
                                    Clear Browser Session
                                </button>
                            </div>
                        </SettingsPanel>

                        <SettingsPanel
                            title="Session Notes"
                            body="This workspace is intentionally local-state aware because token-backed web flows still bootstrap from browser storage."
                        >
                            <div className="space-y-3 text-sm text-muted-foreground">
                                <p>Use this surface to verify that the current browser still carries both access and refresh credentials before debugging auth or notification issues.</p>
                                <p>If tokens are missing but the app partially works, refresh churn or stale browser storage is the first place to inspect.</p>
                                <p>For portfolio, market, and analytics issues, it is better to verify session state here before assuming backend failure.</p>
                                <p>`Clear Local Cache` only removes browser-side terminal and leaderboard state. `Clear Browser Session` also removes auth tokens and identity keys.</p>
                            </div>
                        </SettingsPanel>
                    </div>
                ) : null}

                {workspaceTab === 'ACCOUNT' ? (
                    <div className="grid gap-6 xl:grid-cols-[1fr_1fr]">
                        <SettingsPanel
                            title="Account Record"
                            body="Stable account identity and public-surface routing."
                        >
                            <div className="grid gap-3 sm:grid-cols-2">
                                <SettingsValueCard label="Member Since" value={profile?.memberSince ? new Date(profile.memberSince).toLocaleDateString() : 'N/A'} />
                                <SettingsValueCard label="Public Route" value={profile ? `/profile/${profile.id}` : 'N/A'} />
                                <SettingsValueCard label="Discoverable Identity" value={profile?.displayName || `@${profile?.username ?? 'unknown'}`} />
                                <SettingsValueCard label="Portfolio Surface" value={`${profile?.portfolioCount ?? 0} public/private records`} />
                            </div>
                            <div className="mt-5 flex flex-wrap gap-3">
                                {profile ? (
                                    <Link href={`/profile/${profile.id}`} className="rounded-xl border border-primary/30 bg-primary/10 px-4 py-2 text-sm font-medium text-primary transition hover:bg-primary/20">
                                        Open Public Profile
                                    </Link>
                                ) : null}
                                <Link href="/discover" className="rounded-xl border border-border bg-accent px-4 py-2 text-sm font-medium text-foreground transition hover:border-primary/30">
                                    Open Discover
                                </Link>
                                <Link href="/trust-score" className="rounded-xl border border-border bg-accent px-4 py-2 text-sm font-medium text-foreground transition hover:border-primary/30">
                                    Open Trust Score
                                </Link>
                                <button
                                    type="button"
                                    onClick={() => void handleCopyAccountSummary()}
                                    className="rounded-xl border border-primary/30 bg-primary/10 px-4 py-2 text-sm font-medium text-primary transition hover:bg-primary/20"
                                >
                                    Copy Account Summary
                                </button>
                                <button
                                    type="button"
                                    onClick={handleExportSettingsSnapshot}
                                    className="rounded-xl border border-border bg-accent px-4 py-2 text-sm font-medium text-foreground transition hover:border-primary/30"
                                >
                                    Export Settings JSON
                                </button>
                            </div>
                            <p className="mt-4 text-xs text-muted-foreground">
                                Export produces a local JSON snapshot of current profile, preferences, layouts, and browser-session visibility. It is for inspection and backup, not a server-owned account archive.
                            </p>
                        </SettingsPanel>

                        <SettingsPanel
                            title="Control Surface"
                            body="High-value links that sit adjacent to settings even when they live on separate routes."
                        >
                            <div className="grid gap-3 sm:grid-cols-2">
                                <Link href="/profile/edit" className="rounded-xl border border-border bg-background/60 px-4 py-4 text-sm transition hover:border-primary/30">
                                    <p className="font-medium text-foreground">Dedicated Profile Editor</p>
                                    <p className="mt-1 text-muted-foreground">Open the standalone edit route if you want a cleaner full-page edit surface.</p>
                                </Link>
                                <Link href="/dashboard/audit" className="rounded-xl border border-border bg-background/60 px-4 py-4 text-sm transition hover:border-primary/30">
                                    <p className="font-medium text-foreground">Audit Workspace</p>
                                    <p className="mt-1 text-muted-foreground">Inspect append-only ops trails when account or portfolio writes need forensic review.</p>
                                </Link>
                                <Link href="/dashboard/analysis" className="rounded-xl border border-border bg-background/60 px-4 py-4 text-sm transition hover:border-primary/30">
                                    <p className="font-medium text-foreground">Analysis Workspace</p>
                                    <p className="mt-1 text-muted-foreground">Immutable post workflows and accountability surfaces live there, not inside settings.</p>
                                </Link>
                                <Link href="/notifications" className="rounded-xl border border-border bg-background/60 px-4 py-4 text-sm transition hover:border-primary/30">
                                    <p className="font-medium text-foreground">Inbox</p>
                                    <p className="mt-1 text-muted-foreground">Follow, interaction, and alert stream handling remains in the notification workspace.</p>
                                </Link>
                            </div>
                        </SettingsPanel>

                        <SettingsPanel
                            title="Inbox Routing"
                            body="Tune in-app categories, digest cadence, and quiet hours without leaving the settings workspace."
                        >
                            <div className="space-y-5">
                                <div className="grid gap-3 sm:grid-cols-3">
                                    {([
                                        ['social', 'Social'],
                                        ['watchlist', 'Watchlist'],
                                        ['tournaments', 'Tournaments'],
                                    ] as const).map(([key, label]) => (
                                        <label key={key} className="rounded-xl border border-border bg-background/60 px-4 py-4 text-sm text-foreground">
                                            <div className="flex items-start justify-between gap-3">
                                                <div>
                                                    <p className="font-medium">{label}</p>
                                                    <p className="mt-1 text-xs text-muted-foreground">Keep in-app inbox alerts for {label.toLowerCase()} activity.</p>
                                                </div>
                                                <input
                                                    type="checkbox"
                                                    checked={notification.inApp[key]}
                                                    onChange={(event) => setNotificationPrefs((current) => ({
                                                        ...current,
                                                        inApp: {
                                                            ...current.inApp,
                                                            [key]: event.target.checked,
                                                        },
                                                    }))}
                                                    className="mt-1 h-4 w-4 rounded border-border bg-background"
                                                />
                                            </div>
                                        </label>
                                    ))}
                                </div>

                                <div className="grid gap-4 md:grid-cols-2">
                                    <div>
                                        <label className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Digest Cadence</label>
                                        <select
                                            value={notification.digestCadence}
                                            onChange={(event) => setNotificationPrefs((current) => ({
                                                ...current,
                                                digestCadence: event.target.value as NotificationPreferencesResponsePayload['digestCadence'],
                                            }))}
                                            className="mt-2 w-full rounded-xl border border-border bg-background/60 px-4 py-3 text-sm text-foreground outline-none transition-colors focus:border-primary/40"
                                        >
                                            <option value="INSTANT">Instant</option>
                                            <option value="DAILY">Daily Digest</option>
                                            <option value="OFF">Off</option>
                                        </select>
                                    </div>
                                    <label className="rounded-xl border border-border bg-background/60 px-4 py-4 text-sm text-foreground">
                                        <div className="flex items-start justify-between gap-3">
                                            <div>
                                                <p className="font-medium">Quiet Hours</p>
                                                <p className="mt-1 text-xs text-muted-foreground">Keep non-urgent in-app alerts muted during your offline window.</p>
                                            </div>
                                            <input
                                                type="checkbox"
                                                checked={notification.quietHours.enabled}
                                                onChange={(event) => setNotificationPrefs((current) => ({
                                                    ...current,
                                                    quietHours: {
                                                        ...current.quietHours,
                                                        enabled: event.target.checked,
                                                    },
                                                }))}
                                                className="mt-1 h-4 w-4 rounded border-border bg-background"
                                            />
                                        </div>
                                    </label>
                                </div>

                                <div className="grid gap-4 md:grid-cols-2">
                                    <div>
                                        <label className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Quiet Hours Start</label>
                                        <input
                                            type="time"
                                            value={notification.quietHours.start}
                                            onChange={(event) => setNotificationPrefs((current) => ({
                                                ...current,
                                                quietHours: {
                                                    ...current.quietHours,
                                                    start: event.target.value,
                                                },
                                            }))}
                                            className="mt-2 w-full rounded-xl border border-border bg-background/60 px-4 py-3 text-sm text-foreground outline-none transition-colors focus:border-primary/40"
                                        />
                                    </div>
                                    <div>
                                        <label className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Quiet Hours End</label>
                                        <input
                                            type="time"
                                            value={notification.quietHours.end}
                                            onChange={(event) => setNotificationPrefs((current) => ({
                                                ...current,
                                                quietHours: {
                                                    ...current.quietHours,
                                                    end: event.target.value,
                                                },
                                            }))}
                                            className="mt-2 w-full rounded-xl border border-border bg-background/60 px-4 py-3 text-sm text-foreground outline-none transition-colors focus:border-primary/40"
                                        />
                                    </div>
                                </div>

                                <div className="flex flex-wrap gap-3">
                                    <button
                                        type="button"
                                        onClick={() => void handleSaveNotificationPreferences()}
                                        disabled={saving}
                                        className="rounded-xl border border-primary/30 bg-primary/10 px-4 py-2 text-sm font-medium text-primary transition hover:bg-primary/20 disabled:cursor-not-allowed disabled:opacity-60"
                                    >
                                        {saving ? 'Saving...' : 'Save Notification Preferences'}
                                    </button>
                                    <Link href="/notifications" className="rounded-xl border border-border bg-accent px-4 py-2 text-sm font-medium text-foreground transition hover:border-primary/30">
                                        Open Inbox
                                    </Link>
                                </div>
                            </div>
                        </SettingsPanel>

                        <SettingsPanel
                            className="xl:col-span-2"
                            title="Data Footprint"
                            body="What this account keeps on the server versus what this browser keeps locally."
                        >
                            <div className="grid gap-4 md:grid-cols-2">
                                <div className="rounded-xl border border-border bg-background/60 p-4">
                                    <p className="text-xs uppercase tracking-[0.24em] text-muted-foreground">Server-Owned</p>
                                    <div className="mt-3 flex flex-wrap gap-2">
                                        <span className="rounded-full border border-border px-3 py-1 text-xs text-muted-foreground">Profile</span>
                                        <span className="rounded-full border border-border px-3 py-1 text-xs text-muted-foreground">Trust History</span>
                                        <span className="rounded-full border border-border px-3 py-1 text-xs text-muted-foreground">Terminal Preferences</span>
                                        <span className="rounded-full border border-border px-3 py-1 text-xs text-muted-foreground">Notification Preferences</span>
                                        <span className="rounded-full border border-border px-3 py-1 text-xs text-muted-foreground">Layouts</span>
                                        <span className="rounded-full border border-border px-3 py-1 text-xs text-muted-foreground">Analytics</span>
                                        <span className="rounded-full border border-border px-3 py-1 text-xs text-muted-foreground">Audit Trails</span>
                                    </div>
                                    <p className="mt-3 text-xs text-muted-foreground">
                                        These survive logout/login and can follow the account across devices.
                                    </p>
                                </div>
                                <div className="rounded-xl border border-border bg-background/60 p-4">
                                    <p className="text-xs uppercase tracking-[0.24em] text-muted-foreground">Browser-Owned</p>
                                    <div className="mt-3 flex flex-wrap gap-2">
                                        <span className="rounded-full border border-border px-3 py-1 text-xs text-muted-foreground">Access Token</span>
                                        <span className="rounded-full border border-border px-3 py-1 text-xs text-muted-foreground">Refresh Token</span>
                                        <span className="rounded-full border border-border px-3 py-1 text-xs text-muted-foreground">Local Scanner Cache</span>
                                        <span className="rounded-full border border-border px-3 py-1 text-xs text-muted-foreground">Compare Cache</span>
                                        <span className="rounded-full border border-border px-3 py-1 text-xs text-muted-foreground">Temporary Terminal State</span>
                                    </div>
                                    <p className="mt-3 text-xs text-muted-foreground">
                                        These can be reset from the `Session` workspace without deleting the server-owned account record.
                                    </p>
                                </div>
                            </div>
                        </SettingsPanel>
                    </div>
                ) : null}
            </div>
        </div>
    );
}
