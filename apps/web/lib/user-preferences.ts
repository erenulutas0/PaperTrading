import { apiFetch, userIdHeaders } from "./api-client";
import { extractContent } from "./page";

export type LeaderboardSortBy = "RETURN_PERCENTAGE" | "PROFIT_LOSS" | "WIN_RATE" | "TRUST_SCORE";
export type LeaderboardDirection = "DESC" | "ASC";
export type LeaderboardPeriod = "1D" | "1W" | "1M" | "ALL";

export interface LeaderboardDashboardPreferences {
  period: LeaderboardPeriod;
  sortBy: LeaderboardSortBy;
  direction: LeaderboardDirection;
}

export interface LeaderboardPublicPreferences {
  sortBy: LeaderboardSortBy;
  direction: LeaderboardDirection;
}

export interface LeaderboardPreferencesResponsePayload {
  dashboard: LeaderboardDashboardPreferences;
  publicPage: LeaderboardPublicPreferences;
}

export interface UserPreferencesResponsePayload {
  leaderboard: LeaderboardPreferencesResponsePayload;
  terminal?: TerminalPreferencesResponsePayload;
}

export interface UpdateLeaderboardPreferencesPayload {
  dashboard?: Partial<LeaderboardDashboardPreferences>;
  publicPage?: Partial<LeaderboardPublicPreferences>;
}

export interface TerminalPreferencesResponsePayload {
  market: "CRYPTO" | "BIST100";
  symbol: string;
  compareSymbols: string[];
  compareVisible: boolean;
  range: "1D" | "1W" | "1M" | "3M" | "6M" | "1Y" | "ALL";
  interval: "1m" | "15m" | "30m" | "1h" | "4h" | "1d";
  favoriteSymbols: string[];
  compareBaskets?: TerminalCompareBasketPayload[];
  scannerViews?: TerminalScannerViewPayload[];
}

export interface TerminalCompareBasketPayload {
  name: string;
  market: TerminalPreferencesResponsePayload["market"];
  symbols: string[];
  updatedAt?: string | null;
}

export interface TerminalScannerViewPayload {
  name: string;
  market: TerminalPreferencesResponsePayload["market"];
  quickFilter: "ALL" | "GAINERS" | "LOSERS" | "FAVORITES" | "SECTOR";
  sortMode: "MOVE_DESC" | "MOVE_ASC" | "PRICE_DESC" | "ALPHA";
  query: string;
  anchorSymbol?: string | null;
  updatedAt?: string | null;
}

export interface TerminalLayoutResponsePayload {
  id: string;
  name: string;
  watchlistId: string | null;
  market: TerminalPreferencesResponsePayload["market"];
  symbol: string;
  compareSymbols: string[];
  compareVisible: boolean;
  range: TerminalPreferencesResponsePayload["range"];
  interval: TerminalPreferencesResponsePayload["interval"];
  favoriteSymbols: string[];
  createdAt: string;
  updatedAt: string;
}

export interface UpdateTerminalPreferencesPayload {
  market?: TerminalPreferencesResponsePayload["market"];
  symbol?: string;
  compareSymbols?: string[];
  compareVisible?: boolean;
  range?: TerminalPreferencesResponsePayload["range"];
  interval?: TerminalPreferencesResponsePayload["interval"];
  favoriteSymbols?: string[];
  compareBaskets?: TerminalCompareBasketPayload[];
  scannerViews?: TerminalScannerViewPayload[];
}

export interface SaveTerminalLayoutPayload extends UpdateTerminalPreferencesPayload {
  name: string;
  watchlistId?: string | null;
}

export async function fetchUserPreferences(
  userId: string
): Promise<UserPreferencesResponsePayload | null> {
  try {
    const res = await apiFetch("/api/v1/users/me/preferences", {
      headers: userIdHeaders(userId),
      cache: "no-store",
    });
    if (!res.ok) {
      return null;
    }
    return (await res.json()) as UserPreferencesResponsePayload;
  } catch {
    return null;
  }
}

export async function updateLeaderboardPreferences(
  userId: string,
  payload: UpdateLeaderboardPreferencesPayload
): Promise<UserPreferencesResponsePayload | null> {
  try {
    const res = await apiFetch("/api/v1/users/me/preferences/leaderboard", {
      method: "PUT",
      headers: userIdHeaders(userId, {
        "Content-Type": "application/json",
      }),
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      return null;
    }
    return (await res.json()) as UserPreferencesResponsePayload;
  } catch {
    return null;
  }
}

export async function updateTerminalPreferences(
  userId: string,
  payload: UpdateTerminalPreferencesPayload
): Promise<UserPreferencesResponsePayload | null> {
  try {
    const res = await apiFetch("/api/v1/users/me/preferences/terminal", {
      method: "PUT",
      headers: userIdHeaders(userId, {
        "Content-Type": "application/json",
      }),
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      return null;
    }
    return (await res.json()) as UserPreferencesResponsePayload;
  } catch {
    return null;
  }
}

export async function fetchTerminalLayouts(
  userId: string
): Promise<TerminalLayoutResponsePayload[]> {
  try {
    const res = await apiFetch("/api/v1/users/me/preferences/terminal-layouts", {
      headers: userIdHeaders(userId),
      cache: "no-store",
    });
    if (!res.ok) {
      return [];
    }
    return extractContent<TerminalLayoutResponsePayload>(await res.json());
  } catch {
    return [];
  }
}

export async function createTerminalLayout(
  userId: string,
  payload: SaveTerminalLayoutPayload
): Promise<TerminalLayoutResponsePayload | null> {
  try {
    const res = await apiFetch("/api/v1/users/me/preferences/terminal-layouts", {
      method: "POST",
      headers: userIdHeaders(userId, {
        "Content-Type": "application/json",
      }),
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      return null;
    }
    return (await res.json()) as TerminalLayoutResponsePayload;
  } catch {
    return null;
  }
}

export async function updateTerminalLayout(
  userId: string,
  layoutId: string,
  payload: SaveTerminalLayoutPayload
): Promise<TerminalLayoutResponsePayload | null> {
  try {
    const res = await apiFetch(`/api/v1/users/me/preferences/terminal-layouts/${layoutId}`, {
      method: "PUT",
      headers: userIdHeaders(userId, {
        "Content-Type": "application/json",
      }),
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      return null;
    }
    return (await res.json()) as TerminalLayoutResponsePayload;
  } catch {
    return null;
  }
}

export async function deleteTerminalLayout(
  userId: string,
  layoutId: string
): Promise<boolean> {
  try {
    const res = await apiFetch(`/api/v1/users/me/preferences/terminal-layouts/${layoutId}`, {
      method: "DELETE",
      headers: userIdHeaders(userId),
    });
    return res.ok;
  } catch {
    return false;
  }
}
