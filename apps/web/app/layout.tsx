import type { Metadata } from "next";
import "./globals.css";
import { LiveNotificationProvider } from "../components/LiveNotificationProvider";

export const metadata: Metadata = {
  title: "PaperTrade Pro — Paper Trading & Social Finance",
  description: "Paper trade crypto with live Binance data, social features, tournaments, and real-time notifications.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="antialiased">
        <LiveNotificationProvider>
          <div className="hidden lg:block">
            {children}
          </div>
          <div className="lg:hidden min-h-screen bg-background text-foreground flex items-center justify-center p-6">
            <div className="w-full max-w-md glass-panel border border-border rounded-2xl p-6 text-center">
              <p className="text-[11px] uppercase tracking-[0.32em] text-muted-foreground">Desktop Workspace</p>
              <h1 className="mt-4 text-2xl font-black tracking-tight">Desktop Required</h1>
              <p className="mt-3 text-sm leading-7 text-muted-foreground">
                Markets, analytics, layouts, and discussion surfaces are optimized for a wide workspace. Mobile is intentionally blocked until a real compact workflow exists.
              </p>
              <div className="mt-6 rounded-xl border border-border bg-background/60 p-4 text-left">
                <p className="text-xs uppercase tracking-wide text-muted-foreground">Current requirement</p>
                <p className="mt-2 text-sm text-foreground/85">Minimum supported width: 1024px</p>
              </div>
            </div>
          </div>
        </LiveNotificationProvider>
      </body>
    </html>
  );
}
