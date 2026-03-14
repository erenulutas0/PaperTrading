export type SharedMarketSelection = "CRYPTO" | "BIST100";
export type SharedChartRange = "1D" | "1W" | "1M" | "3M" | "6M" | "1Y" | "ALL";
export type SharedChartInterval = "1m" | "15m" | "30m" | "1h" | "4h" | "1d";

export interface SharedTerminalLayoutPayload {
  version: 1;
  name: string;
  watchlistId: string | null;
  market: SharedMarketSelection;
  symbol: string;
  compareSymbols: string[];
  compareVisible: boolean;
  range: SharedChartRange;
  interval: SharedChartInterval;
  favoriteSymbols: string[];
}

const RANGE_OPTIONS: SharedChartRange[] = ["1D", "1W", "1M", "3M", "6M", "1Y", "ALL"];
const INTERVAL_OPTIONS: SharedChartInterval[] = ["1m", "15m", "30m", "1h", "4h", "1d"];

function toBase64(input: string) {
  if (typeof window !== "undefined" && typeof window.btoa === "function") {
    return window.btoa(unescape(encodeURIComponent(input)));
  }
  return Buffer.from(input, "utf8").toString("base64");
}

function fromBase64(input: string) {
  if (typeof window !== "undefined" && typeof window.atob === "function") {
    return decodeURIComponent(escape(window.atob(input)));
  }
  return Buffer.from(input, "base64").toString("utf8");
}

export function encodeSharedLayout(layout: SharedTerminalLayoutPayload) {
  const json = JSON.stringify(layout);
  return toBase64(json)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

export function decodeSharedLayout(encoded: string): SharedTerminalLayoutPayload | null {
  if (!encoded) {
    return null;
  }
  try {
    const normalized = encoded.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
    const json = fromBase64(padded);
    const parsed = JSON.parse(json);
    if (!parsed || typeof parsed !== "object" || parsed.version !== 1 || typeof parsed.name !== "string") {
      return null;
    }
    return {
      version: 1,
      name: parsed.name,
      watchlistId: typeof parsed.watchlistId === "string" ? parsed.watchlistId : null,
      market: parsed.market === "BIST100" ? "BIST100" : "CRYPTO",
      symbol: typeof parsed.symbol === "string" && parsed.symbol ? parsed.symbol : "BTCUSDT",
      compareSymbols: Array.isArray(parsed.compareSymbols)
        ? parsed.compareSymbols.filter((value: unknown): value is string => typeof value === "string").slice(0, 3)
        : [],
      compareVisible: parsed.compareVisible !== false,
      range: RANGE_OPTIONS.includes(parsed.range) ? parsed.range : "1D",
      interval: INTERVAL_OPTIONS.includes(parsed.interval) ? parsed.interval : "1h",
      favoriteSymbols: Array.isArray(parsed.favoriteSymbols)
        ? parsed.favoriteSymbols.filter((value: unknown): value is string => typeof value === "string")
        : [],
    };
  } catch {
    return null;
  }
}
