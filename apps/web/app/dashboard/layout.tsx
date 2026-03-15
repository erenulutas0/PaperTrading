'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import NotificationBell from '../../components/NotificationBell';
import LogoutButton from '../../components/LogoutButton';

const navigation = [
    { name: 'Dashboard', href: '/dashboard' },
    { name: 'Analysis', href: '/dashboard/analysis' },
    { name: 'Discover', href: '/discover' },
    { name: 'Leaderboard', href: '/dashboard/leaderboard' },
    { name: 'Tournaments', href: '/tournaments' },
    { name: 'Markets', href: '/watchlist' },
    { name: 'Audit', href: '/dashboard/audit' },
];

export default function DashboardLayout({
    children,
}: {
    children: React.ReactNode;
}) {
    const pathname = usePathname();
    const currentUserId = typeof window !== 'undefined' ? localStorage.getItem('userId') : null;
    const currentSection = navigation.find((item) => pathname === item.href || pathname.startsWith(item.href + '/'))?.name ?? 'Workspace';

    return (
        <div className="min-h-screen bg-background text-foreground noise-bg">
            <nav className="sticky top-0 z-[80] border-b border-border glass-panel">
                <div className="px-6 py-4">
                    <div className="flex flex-wrap items-center justify-between gap-4">
                        <div className="flex min-w-0 items-center gap-4">
                            <Link href="/dashboard" className="flex items-center gap-3">
                                <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-br from-primary to-secondary font-bold text-background">
                                    P
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-[0.28em] text-muted-foreground">Proof Workspace</p>
                                    <span className="text-xl font-bold bg-gradient-to-r from-primary to-secondary bg-clip-text text-transparent">
                                        PaperTradePro
                                    </span>
                                </div>
                            </Link>
                            <div className="hidden rounded-full border border-border bg-background/60 px-3 py-1.5 text-[11px] font-medium uppercase tracking-[0.24em] text-muted-foreground xl:inline-flex">
                                {currentSection}
                            </div>
                        </div>

                        <div className="flex flex-wrap items-center gap-2">
                            {currentUserId && (
                                <Link href={`/profile/${currentUserId}`} className="rounded-lg px-3 py-2 text-sm text-muted-foreground transition hover:bg-accent hover:text-foreground">
                                    Profile
                                </Link>
                            )}
                            <NotificationBell />
                            <Link href="/notifications" className="rounded-lg px-3 py-2 text-sm text-muted-foreground transition hover:bg-accent hover:text-foreground">
                                Inbox
                            </Link>
                            <LogoutButton className="rounded-lg px-3 py-2 text-sm text-muted-foreground transition hover:bg-accent hover:text-foreground disabled:opacity-60" />
                        </div>
                    </div>

                    <div className="mt-4 flex flex-wrap items-center gap-1">
                        {navigation.map((item) => {
                            const isActive = pathname === item.href || pathname.startsWith(item.href + '/');
                            return (
                                <Link
                                    key={item.name}
                                    href={item.href}
                                    className={`rounded-lg px-4 py-2 text-sm font-medium transition-colors ${isActive
                                        ? 'bg-primary/10 text-primary'
                                        : 'text-muted-foreground hover:bg-accent hover:text-foreground'
                                        }`}
                                >
                                    {item.name}
                                </Link>
                            );
                        })}
                    </div>
                </div>
            </nav>

            <div className="relative z-10 w-full h-full">
                {children}
            </div>
        </div>
    );
}
