import { apiFetch, userIdHeaders } from "./api-client";

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
}

export interface UpdateTerminalPreferencesPayload {
  market?: TerminalPreferencesResponsePayload["market"];
  symbol?: string;
  compareSymbols?: string[];
  compareVisible?: boolean;
  range?: TerminalPreferencesResponsePayload["range"];
  interval?: TerminalPreferencesResponsePayload["interval"];
  favoriteSymbols?: string[];
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
