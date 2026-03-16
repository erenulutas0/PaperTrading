'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { extractContent } from '../../lib/page';
import { apiFetch } from '../../lib/api-client';

interface Tournament {
    id: string;
    name: string;
    description: string | null;
    startingBalance: number;
    status: 'UPCOMING' | 'ACTIVE' | 'COMPLETED';
    startsAt: string;
    endsAt: string;
    createdAt: string;
}

interface LeaderboardEntry {
    rank: number;
    userId: string;
    username: string;
    portfolioId: string;
    equity?: number;
    returnPercent: number;
}

type TournamentsWorkspaceTab = 'OVERVIEW' | 'ARENAS' | 'SPOTLIGHT';

export default function TournamentsPage() {
    const router = useRouter();
    const [tournaments, setTournaments] = useState<Tournament[]>([]);
    const [selectedTournament, setSelectedTournament] = useState<string | null>(null);
    const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>([]);
    const [loading, setLoading] = useState(true);
    const [showCreate, setShowCreate] = useState(false);
    const [workspaceTab, setWorkspaceTab] = useState<TournamentsWorkspaceTab>('OVERVIEW');

    // Create form
    const [createName, setCreateName] = useState('');
    const [createDesc, setCreateDesc] = useState('');
    const [createBalance, setCreateBalance] = useState('100000');
    const [createStart, setCreateStart] = useState('');
    const [createEnd, setCreateEnd] = useState('');

    useEffect(() => {
        fetchTournaments();
    }, []);

    useEffect(() => {
        if (selectedTournament) {
            fetchLeaderboard();
            const interval = setInterval(fetchLeaderboard, 10000);
            return () => clearInterval(interval);
        }
    }, [selectedTournament]);

    const fetchTournaments = async () => {
        try {
            const res = await apiFetch('/api/v1/tournaments');
            if (res.ok) {
                const data = await res.json();
                const tournaments = extractContent<Tournament>(data);
                setTournaments(tournaments);
                const active = tournaments.find((t: Tournament) => t.status === 'ACTIVE');
                if (active) setSelectedTournament(active.id);
                else if (tournaments.length > 0) setSelectedTournament(tournaments[0].id);
            }
        } catch (err) { console.error(err); }
        finally { setLoading(false); }
    };

    const fetchLeaderboard = async () => {
        if (!selectedTournament) return;
        try {
            const res = await apiFetch(`/api/v1/tournaments/${selectedTournament}/leaderboard`);
            if (res.ok) setLeaderboard(await res.json());
        } catch (err) { console.error(err); }
    };

    const handleJoin = async (tournamentId: string) => {
        try {
            const userId = localStorage.getItem('userId');
            if (!userId) {
                alert('Please sign in first.');
                return;
            }
            const res = await apiFetch(`/api/v1/tournaments/${tournamentId}/join`, {
                method: 'POST'
            });
            if (res.ok) {
                await res.json();
                router.push(`/tournaments/${tournamentId}/hub`);
            } else {
                const txt = await res.text();
                alert(`Failed to join: ${txt}`);
            }
        } catch (err) { console.error(err); }
    };

    const handleCreate = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            const res = await apiFetch('/api/v1/tournaments', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    name: createName,
                    description: createDesc,
                    startingBalance: parseFloat(createBalance),
                    startsAt: createStart,
                    endsAt: createEnd
                })
            });
            if (res.ok) {
                setShowCreate(false);
                setCreateName(''); setCreateDesc(''); setCreateBalance('100000');
                setCreateStart(''); setCreateEnd('');
                fetchTournaments();
            }
        } catch (err) { console.error(err); }
    };

    const statusBadge = (s: string) => {
        switch (s) {
            case 'ACTIVE': return 'bg-green-500/10 text-green-400 border-green-500/20';
            case 'UPCOMING': return 'bg-blue-500/10 text-blue-400 border-blue-500/20';
            case 'COMPLETED': return 'bg-zinc-500/10 text-zinc-400 border-zinc-500/20';
            default: return '';
        }
    };

    const selected = tournaments.find(t => t.id === selectedTournament);
    const activeCount = tournaments.filter((t) => t.status === 'ACTIVE').length;
    const upcomingCount = tournaments.filter((t) => t.status === 'UPCOMING').length;

    return (
        <div className="min-h-screen bg-black text-white selection:bg-green-500/30">
            <div className="noise" />

            {/* Header / Nav */}
            <nav className="fixed w-full z-[100] py-6 bg-black/50 backdrop-blur-xl border-b border-white/5">
                <div className="max-w-7xl mx-auto px-6 flex justify-between items-center">
                    <Link href="/" className="text-xl font-black tracking-tighter flex items-center gap-2">
                        <span className="bg-green-500 text-black px-2 py-0.5 rounded-md">P</span>
                        <span>PAPERTRADE<span className="text-green-500">PRO</span></span>
                    </Link>
                    <div className="flex gap-8 text-xs font-bold uppercase tracking-widest text-zinc-500">
                        <Link href="/dashboard" className="hover:text-green-500 transition-colors">Terminal</Link>
                        <Link href="/discover" className="hover:text-green-500 transition-colors">Social</Link>
                        <Link href="/tournaments" className="text-white border-b-2 border-green-500 pb-1">Arena</Link>
                    </div>
                </div>
            </nav>

            <main className="pt-32 pb-20 px-6 max-w-7xl mx-auto">
                {/* Hero Header */}
                <div className="flex flex-col md:flex-row md:items-end justify-between gap-6 mb-16">
                    <div>
                        <div className="flex items-center gap-3 mb-4">
                            <span className="w-10 h-[1px] bg-green-500"></span>
                            <span className="text-green-500 text-[10px] font-bold uppercase tracking-[0.2em]">Global Arena</span>
                        </div>
                        <h1 className="text-6xl font-black tracking-tighter mb-4">
                            PAPER TRADING <br /> <span className="text-zinc-500 italic">CHAMPIONSHIPS</span>
                        </h1>
                        <p className="text-zinc-500 max-w-lg font-medium leading-relaxed">
                            Battle against the worlds best traders in real-time. Transparent execution,
                            verified returns, and exclusive reputation badges.
                        </p>
                    </div>
                    <button
                        onClick={() => setShowCreate(!showCreate)}
                        className="group relative px-8 py-4 bg-white text-black font-black uppercase tracking-widest text-xs rounded-full hover:scale-105 active:scale-95 transition-all"
                    >
                        Create Your Arena
                        <div className="absolute inset-0 bg-green-500 rounded-full blur-xl opacity-0 group-hover:opacity-20 transition-opacity"></div>
                    </button>
                </div>

                <section className="mb-10 rounded-[2rem] border border-white/10 bg-white/[0.02] p-6">
                    <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                        <div>
                            <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Arena Workspace</p>
                            <h2 className="mt-2 text-2xl font-black text-white">Separate tournament context, arena operations, and live spotlight rankings.</h2>
                            <p className="mt-3 max-w-3xl text-sm leading-7 text-zinc-400">
                                Use overview to understand the competition layer first, then move into arena selection or the live spotlight board without forcing all three surfaces onto the same first paint.
                            </p>
                        </div>
                        <div className="flex flex-wrap gap-2">
                            {([
                                { key: 'OVERVIEW', label: 'Overview', badge: `${tournaments.length} arenas` },
                                { key: 'ARENAS', label: 'Arenas', badge: `${activeCount} active` },
                                { key: 'SPOTLIGHT', label: 'Spotlight', badge: `${leaderboard.length} ranked` },
                            ] as const).map(({ key, label, badge }) => (
                                <button
                                    key={key}
                                    type="button"
                                    onClick={() => setWorkspaceTab(key)}
                                    className={`inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-semibold transition ${
                                        workspaceTab === key
                                            ? 'border-green-500/35 bg-green-500/15 text-green-300'
                                            : 'border-white/10 bg-white/5 text-zinc-400 hover:text-white'
                                    }`}
                                >
                                    <span>{label}</span>
                                    <span className={`rounded-full px-2 py-0.5 text-[10px] ${
                                        workspaceTab === key ? 'bg-green-500/15 text-green-200' : 'bg-black/30 text-zinc-500'
                                    }`}>
                                        {badge}
                                    </span>
                                </button>
                            ))}
                        </div>
                    </div>
                </section>

                {/* Create Form Modal Style */}
                {showCreate && (
                    <div className="fixed inset-0 z-[200] flex items-center justify-center p-6 bg-black/80 backdrop-blur-md">
                        <form onSubmit={handleCreate} className="glass border border-white/10 p-10 rounded-[2rem] max-w-xl w-full space-y-6 animate-float">
                            <h2 className="text-3xl font-black tracking-tight mb-4">SETUP NEW ARENA</h2>
                            <div className="grid grid-cols-1 gap-4">
                                <input type="text" value={createName} onChange={e => setCreateName(e.target.value)} placeholder="TOURNAMENT NAME" className="bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-sm focus:border-green-500 outline-none transition-all" required />
                                <textarea value={createDesc} onChange={e => setCreateDesc(e.target.value)} placeholder="ARENA DESCRIPTION" className="bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-sm focus:border-green-500 outline-none h-24 transition-all" />
                                <div className="grid grid-cols-2 gap-4">
                                    <div className="flex flex-col gap-1">
                                        <label className="text-[10px] font-bold text-zinc-500 ml-1 uppercase">Starting Cap</label>
                                        <input type="number" value={createBalance} onChange={e => setCreateBalance(e.target.value)} className="bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-sm focus:border-green-500 outline-none" />
                                    </div>
                                    <div className="flex flex-col gap-1">
                                        <label className="text-[10px] font-bold text-zinc-500 ml-1 uppercase">Start Time</label>
                                        <input type="datetime-local" value={createStart} onChange={e => setCreateStart(e.target.value)} className="bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-sm focus:border-green-500 outline-none" required />
                                    </div>
                                </div>
                                <div className="flex flex-col gap-1">
                                    <label className="text-[10px] font-bold text-zinc-500 ml-1 uppercase">End Time</label>
                                    <input type="datetime-local" value={createEnd} onChange={e => setCreateEnd(e.target.value)} className="bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-sm focus:border-green-500 outline-none" required />
                                </div>
                            </div>
                            <div className="flex gap-4">
                                <button type="button" onClick={() => setShowCreate(false)} className="flex-1 px-4 py-4 rounded-xl border border-white/10 font-bold uppercase tracking-widest text-xs hover:bg-white/5 transition-colors">CANCEL</button>
                                <button type="submit" className="flex-2 px-10 py-4 bg-green-500 text-black font-black rounded-xl uppercase tracking-widest text-xs hover:brightness-110 transition-all">INITIALIZE ARENA</button>
                            </div>
                        </form>
                    </div>
                )}

                {workspaceTab === 'OVERVIEW' && (
                    <div className="grid gap-4 md:grid-cols-[1.05fr_0.95fr]">
                        <section className="rounded-[2rem] border border-white/10 bg-white/[0.02] p-8">
                            <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">Competition Layer</p>
                            <h2 className="mt-3 text-3xl font-black tracking-tight text-white">Arenas convert paper performance into comparable public competition.</h2>
                            <p className="mt-4 text-sm leading-7 text-zinc-400">
                                Tournaments define a fixed starting balance, a shared time window, and a live ranking surface. They are the product’s cleanest way to compare performance under a common operating frame.
                            </p>
                        </section>
                        <section className="rounded-[2rem] border border-white/10 bg-white/[0.02] p-8">
                            <p className="text-[11px] uppercase tracking-[0.32em] text-zinc-500">What To Do Here</p>
                            <ul className="mt-4 space-y-3 text-sm leading-6 text-zinc-300">
                                <li>Inspect active and upcoming arenas without opening the live spotlight immediately.</li>
                                <li>Enter an active hub when you want tournament-specific participation.</li>
                                <li>Jump into spotlight when you only care about the current top-ranked competitors.</li>
                            </ul>
                        </section>
                        <section className="rounded-[2rem] border border-white/10 bg-white/[0.02] p-8 md:col-span-2">
                            <div className="grid gap-4 md:grid-cols-4">
                                <div className="rounded-xl border border-white/5 bg-black/30 p-4">
                                    <p className="text-xs uppercase tracking-wide text-zinc-500">Arenas</p>
                                    <p className="mt-2 text-2xl font-bold text-white">{loading ? '...' : tournaments.length}</p>
                                </div>
                                <div className="rounded-xl border border-white/5 bg-black/30 p-4">
                                    <p className="text-xs uppercase tracking-wide text-zinc-500">Active</p>
                                    <p className="mt-2 text-2xl font-bold text-green-300">{loading ? '...' : activeCount}</p>
                                </div>
                                <div className="rounded-xl border border-white/5 bg-black/30 p-4">
                                    <p className="text-xs uppercase tracking-wide text-zinc-500">Upcoming</p>
                                    <p className="mt-2 text-2xl font-bold text-blue-300">{loading ? '...' : upcomingCount}</p>
                                </div>
                                <div className="rounded-xl border border-white/5 bg-black/30 p-4">
                                    <p className="text-xs uppercase tracking-wide text-zinc-500">Next Step</p>
                                    <button
                                        type="button"
                                        onClick={() => setWorkspaceTab('ARENAS')}
                                        className="mt-2 rounded-lg border border-green-500/20 bg-green-500/10 px-3 py-2 text-xs font-bold uppercase tracking-[0.2em] text-green-300"
                                    >
                                        Open Arenas
                                    </button>
                                </div>
                            </div>
                        </section>
                    </div>
                )}

                {workspaceTab === 'ARENAS' && (
                    <div className="space-y-4">
                        <div className="flex items-center justify-between px-2 mb-6">
                            <h3 className="text-sm font-bold uppercase tracking-[0.2em] text-zinc-500">Live Arenas</h3>
                            <span className="text-[10px] font-mono text-zinc-600">{tournaments.length} Matches Found</span>
                        </div>

                        {loading ? (
                            <div className="py-20 flex justify-center">
                                <div className="animate-spin h-8 w-8 border-t-2 border-green-500 rounded-full"></div>
                            </div>
                        ) : tournaments.length === 0 ? (
                            <div className="glass border border-dashed border-white/10 rounded-3xl p-12 text-center text-zinc-600 italic">
                                No active arenas. Use the button above to launch one.
                            </div>
                        ) : (
                            <div className="grid gap-4 xl:grid-cols-2">
                                {tournaments.map(t => (
                                    <div
                                        key={t.id}
                                        onClick={() => setSelectedTournament(t.id)}
                                        className={`group relative glass rounded-3xl p-6 border transition-all duration-500 cursor-pointer overflow-hidden ${selectedTournament === t.id
                                                ? 'border-green-500/40 bg-green-500/[0.03] scale-[1.02]'
                                                : 'border-white/5 hover:border-white/20'
                                            }`}
                                    >
                                        <div className={`absolute -top-10 -right-10 w-32 h-32 blur-3xl rounded-full transition-opacity duration-700 ${selectedTournament === t.id ? 'bg-green-500/20 opacity-100' : 'bg-green-500/10 opacity-0 group-hover:opacity-100'
                                            }`} />

                                        <div className="flex items-center justify-between mb-4">
                                            <div className={`px-3 py-1 rounded-full text-[9px] font-black uppercase tracking-wider border ${statusBadge(t.status)}`}>
                                                {t.status}
                                            </div>
                                            <div className="text-[10px] font-mono text-zinc-500">{new Date(t.endsAt).toLocaleDateString()}</div>
                                        </div>

                                        <h3 className={`text-2xl font-black tracking-tight mb-2 ${selectedTournament === t.id ? 'text-white' : 'text-zinc-400 group-hover:text-white'}`}>
                                            {t.name}
                                        </h3>
                                        <p className="text-sm text-zinc-500 mb-6 line-clamp-2">{t.description || 'Global trading challenge with high starting capital.'}</p>

                                        <div className="flex items-center justify-between">
                                            <div className="flex flex-col">
                                                <span className="text-[9px] font-bold text-zinc-600 uppercase tracking-widest">Starting Cap</span>
                                                <span className="text-sm font-mono font-bold">${Number(t.startingBalance).toLocaleString()}</span>
                                            </div>

                                            {t.status === 'ACTIVE' && (
                                                <button
                                                    onClick={(e) => { e.stopPropagation(); handleJoin(t.id); }}
                                                    className="bg-green-500/10 border border-green-500/20 text-green-400 px-4 py-2 rounded-xl text-[10px] font-black uppercase tracking-widest hover:bg-green-500 hover:text-black transition-all"
                                                >
                                                    Enter Hub →
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                )}

                {workspaceTab === 'SPOTLIGHT' && (
                    selectedTournament && selected ? (
                        <div className="glass-dark border border-white/5 rounded-[2.5rem] p-10 min-h-[600px] flex flex-col">
                            <div className="flex items-start justify-between mb-10">
                                <div>
                                    <h2 className="text-4xl font-black tracking-tighter mb-2 italic">ARENA <span className="text-green-500">SPOTLIGHT</span></h2>
                                    <p className="text-zinc-500 font-medium">Top performers for <span className="text-white">{selected.name}</span></p>
                                </div>
                                {selected.status === 'ACTIVE' && (
                                    <Link
                                        href={`/tournaments/${selected.id}/hub`}
                                        className="px-6 py-3 bg-white/5 border border-white/10 rounded-2xl text-[10px] font-black uppercase tracking-[0.2em] hover:bg-green-500 hover:text-black hover:border-green-500 transition-all"
                                    >
                                        Go To Live Hub
                                    </Link>
                                )}
                            </div>

                            {leaderboard.length === 0 ? (
                                <div className="flex-1 flex flex-col items-center justify-center text-center py-20">
                                    <div className="w-20 h-20 bg-white/5 rounded-full flex items-center justify-center mb-6 text-2xl scale-125 opacity-20">🏁</div>
                                    <p className="text-zinc-600 font-bold uppercase tracking-widest text-xs">Waiting for participants to join the battle...</p>
                                </div>
                            ) : (
                                <div className="space-y-4">
                                    {leaderboard.map((entry, i) => (
                                        <div
                                            key={entry.userId}
                                            className={`group flex items-center justify-between p-5 rounded-2xl border transition-all ${i === 0 ? 'bg-green-500/[0.05] border-green-500/20 shadow-[0_0_30px_rgba(34,197,94,0.05)]' : 'bg-white/[0.02] border-white/5 hover:border-white/10'
                                                }`}
                                        >
                                            <div className="flex items-center gap-6">
                                                <span className={`w-8 text-xl italic font-black ${i === 0 ? 'text-green-500' : i === 1 ? 'text-zinc-300' : 'text-zinc-600'}`}>
                                                    {i === 0 ? '01' : i === 1 ? '02' : i === 2 ? '03' : i + 1}
                                                </span>
                                                <div>
                                                    <div className="font-bold text-lg group-hover:text-green-400 transition-colors">{entry.username}</div>
                                                    <div className="text-[10px] font-mono text-zinc-500 uppercase tracking-widest">
                                                        Equity: ${Number(entry.equity).toLocaleString(undefined, { maximumFractionDigits: 0 })}
                                                    </div>
                                                </div>
                                            </div>

                                            <div className="text-right">
                                                <div className={`text-2xl font-black font-mono tracking-tight ${entry.returnPercent >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                                                    {entry.returnPercent >= 0 ? '+' : ''}{entry.returnPercent.toFixed(2)}%
                                                </div>
                                                <Link
                                                    href={`/dashboard/portfolio/${entry.portfolioId}`}
                                                    className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.2em] hover:text-white transition-colors"
                                                >
                                                    View Strategy →
                                                </Link>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    ) : (
                        <div className="h-full glass border border-dashed border-white/5 rounded-[2.5rem] flex items-center justify-center text-zinc-700 italic min-h-[400px]">
                            Select an arena to view the rankings
                        </div>
                    )
                )}
            </main>
        </div>
    );
}
