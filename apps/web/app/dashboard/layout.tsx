'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import NotificationBell from '../../components/NotificationBell';

const navigation = [
    { name: 'Dashboard', href: '/dashboard' },
    { name: 'Analysis', href: '/dashboard/analysis' },
    { name: 'Discover', href: '/discover' },
    { name: 'Leaderboard', href: '/dashboard/leaderboard' },
    { name: 'Tournaments', href: '/tournaments' },
    { name: 'Watchlist', href: '/watchlist' },
];

export default function DashboardLayout({
    children,
}: {
    children: React.ReactNode;
}) {
    const pathname = usePathname();
    const currentUserId = typeof window !== 'undefined' ? localStorage.getItem('userId') : null;

    return (
        <div className="min-h-screen bg-background text-foreground noise-bg">
            <nav className="flex items-center justify-between px-6 py-4 border-b border-border glass-panel sticky top-0 z-[80]">
                <div className="flex items-center gap-8">
                    <Link href="/dashboard" className="flex items-center gap-2">
                        <div className="w-8 h-8 bg-gradient-to-br from-primary to-secondary rounded-lg flex items-center justify-center text-background font-bold">
                            P
                        </div>
                        <span className="text-xl font-bold bg-gradient-to-r from-primary to-secondary bg-clip-text text-transparent">
                            PaperTradePro
                        </span>
                    </Link>
                    <div className="flex items-center gap-1">
                        {navigation.map((item) => {
                            const isActive = pathname === item.href || pathname.startsWith(item.href + '/');
                            return (
                                <Link
                                    key={item.name}
                                    href={item.href}
                                    className={`px-4 py-2 rounded-lg transition-colors text-sm font-medium ${isActive
                                            ? 'bg-primary/10 text-primary'
                                            : 'text-muted-foreground hover:text-foreground hover:bg-accent'
                                        }`}
                                >
                                    {item.name}
                                </Link>
                            );
                        })}
                    </div>
                </div>
                <div className="flex items-center gap-2">
                    {currentUserId && (
                        <Link href={`/profile/${currentUserId}`} className="px-3 py-2 rounded-lg text-muted-foreground hover:text-foreground hover:bg-accent text-sm">
                            Profile
                        </Link>
                    )}
                    <NotificationBell />
                    <Link href="/notifications" className="px-3 py-2 rounded-lg text-muted-foreground hover:text-foreground hover:bg-accent text-sm">
                        Inbox
                    </Link>
                    <Link href="/" className="px-3 py-2 rounded-lg text-muted-foreground hover:text-foreground hover:bg-accent text-sm">
                        Exit
                    </Link>
                </div>
            </nav>

            <div className="relative z-10 w-full h-full">
                {children}
            </div>
        </div>
    );
}
