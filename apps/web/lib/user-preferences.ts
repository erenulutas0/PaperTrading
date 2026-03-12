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
}

export interface UpdateLeaderboardPreferencesPayload {
  dashboard?: Partial<LeaderboardDashboardPreferences>;
  publicPage?: Partial<LeaderboardPublicPreferences>;
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
