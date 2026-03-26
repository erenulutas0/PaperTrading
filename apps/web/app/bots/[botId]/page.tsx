import { Suspense } from 'react';
import PublicStrategyBotDetailPageClient from './PublicStrategyBotDetailPageClient';

export default function PublicStrategyBotDetailPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen bg-black text-white">
          <div className="mx-auto max-w-6xl px-6 py-10">
            <div className="rounded-2xl border border-white/10 bg-white/[0.02] p-6 text-sm text-zinc-400">
              Loading strategy bot detail...
            </div>
          </div>
        </div>
      }
    >
      <PublicStrategyBotDetailPageClient />
    </Suspense>
  );
}
