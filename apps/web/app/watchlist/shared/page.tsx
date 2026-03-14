'use client';

import Link from 'next/link';
import { useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { decodeSharedLayout, SharedTerminalLayoutPayload } from '../../../lib/market-terminal-share';

function formatPreviewLines(layout: SharedTerminalLayoutPayload) {
  return [
    `Market: ${layout.market}`,
    `Symbol: ${layout.symbol}`,
    `Range / Interval: ${layout.range} / ${layout.interval}`,
    `Compare: ${layout.compareSymbols.length > 0 ? layout.compareSymbols.join(', ') : 'None'}`,
    `Favorites: ${layout.favoriteSymbols.length}`,
    `Watchlist linked: ${layout.watchlistId ? 'Yes' : 'No'}`,
  ];
}

export default function SharedWatchlistLayoutPage() {
  const params = useSearchParams();
  const [message, setMessage] = useState('');
  const encoded = params.get('layout') || params.get('sharedLayout') || '';

  const sharedLayout = useMemo(() => decodeSharedLayout(encoded), [encoded]);
  const previewLines = useMemo(() => sharedLayout ? formatPreviewLines(sharedLayout) : [], [sharedLayout]);

  const openInTerminalHref = useMemo(() => {
    if (!encoded) {
      return '/watchlist';
    }
    return `/watchlist?sharedLayout=${encoded}`;
  }, [encoded]);

  const handleCopyLink = async () => {
    if (typeof window === 'undefined' || !encoded) {
      return;
    }
    const url = `${window.location.origin}/watchlist/shared?layout=${encoded}`;
    try {
      await navigator.clipboard.writeText(url);
      setMessage('Shared preview link copied.');
    } catch {
      setMessage(url);
    }
  };

  return (
    <div className="min-h-screen bg-[radial-gradient(circle_at_top_left,_rgba(245,158,11,0.10),_transparent_28%),radial-gradient(circle_at_bottom_right,_rgba(34,197,94,0.10),_transparent_28%),#040404] px-6 py-10 text-white">
      <div className="mx-auto max-w-4xl">
        <div className="rounded-3xl border border-white/10 bg-black/45 p-8 shadow-[0_0_60px_rgba(0,0,0,0.4)] backdrop-blur-xl">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <p className="text-[11px] uppercase tracking-[0.3em] text-zinc-500">Shared Terminal Layout</p>
              <h1 className="mt-3 text-4xl font-bold">
                <span className="bg-gradient-to-r from-amber-400 via-yellow-300 to-orange-500 bg-clip-text text-transparent">Market</span> Preview
              </h1>
              <p className="mt-3 text-sm text-zinc-400">
                Lightweight preview page for a portable market-terminal preset.
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <button
                onClick={handleCopyLink}
                className="rounded-full border border-white/10 bg-white/[0.03] px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] text-zinc-300 transition hover:text-white"
              >
                Copy Link
              </button>
              <Link
                href={openInTerminalHref}
                className="rounded-full border border-amber-400/20 bg-amber-400/10 px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] text-amber-300 transition hover:bg-amber-400/20"
              >
                Open In Terminal
              </Link>
            </div>
          </div>

          {!sharedLayout ? (
            <div className="mt-8 rounded-2xl border border-dashed border-white/10 px-6 py-8 text-sm text-zinc-500">
              Shared layout link is invalid or incomplete.
            </div>
          ) : (
            <>
              <div className="mt-8 rounded-2xl border border-sky-400/20 bg-sky-400/8 p-5">
                <p className="text-xs font-bold uppercase tracking-[0.18em] text-sky-300">{sharedLayout.name}</p>
                <div className="mt-3 flex flex-wrap gap-2">
                  <span className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-zinc-300">
                    {sharedLayout.market}
                  </span>
                  <span className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-zinc-300">
                    {sharedLayout.symbol}
                  </span>
                  <span className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-zinc-300">
                    {sharedLayout.range} · {sharedLayout.interval}
                  </span>
                  {sharedLayout.compareSymbols.length > 0 && (
                    <span className="rounded-full border border-sky-400/20 bg-sky-400/10 px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-sky-300">
                      Compare {sharedLayout.compareSymbols.join(', ')}
                    </span>
                  )}
                  <span className="rounded-full border border-emerald-400/20 bg-emerald-400/10 px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-emerald-300">
                    Favorites {sharedLayout.favoriteSymbols.length}
                  </span>
                </div>
              </div>

              <div className="mt-8 grid gap-4 md:grid-cols-2">
                {previewLines.map((line) => (
                  <div key={line} className="rounded-2xl border border-white/10 bg-white/[0.03] px-4 py-4 text-sm text-zinc-300">
                    {line}
                  </div>
                ))}
              </div>

              <div className="mt-8 rounded-2xl border border-white/10 bg-black/35 p-5">
                <p className="text-[11px] uppercase tracking-[0.24em] text-zinc-500">What Happens Next</p>
                <div className="mt-3 space-y-2 text-sm text-zinc-400">
                  <p>`Open In Terminal` loads this preset into the full market workspace.</p>
                  <p>The terminal page can then apply it directly or save it as a new account layout.</p>
                </div>
              </div>
            </>
          )}

          {message && (
            <p className="mt-6 break-all text-xs text-zinc-500">{message}</p>
          )}
        </div>
      </div>
    </div>
  );
}
