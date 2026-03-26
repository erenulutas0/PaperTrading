import { Suspense } from 'react';
import StrategyBotsPageClient from './StrategyBotsPageClient';

export default function StrategyBotsPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen bg-black text-white">
          <div className="mx-auto max-w-7xl px-6 py-10">
            <div className="rounded-3xl border border-white/10 bg-white/[0.02] p-6 text-sm text-zinc-400">
              Loading strategy bot workspace...
            </div>
          </div>
        </div>
      }
    >
      <StrategyBotsPageClient />
    </Suspense>
  );
}
