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
              <h1 className="text-2xl font-bold tracking-tight">Desktop Required</h1>
              <p className="mt-2 text-sm text-muted-foreground">
                This web app is desktop-only for now. Please open it on a larger screen.
              </p>
              <p className="mt-4 text-xs text-muted-foreground">
                Minimum supported width: 1024px
              </p>
            </div>
          </div>
        </LiveNotificationProvider>
      </body>
    </html>
  );
}
