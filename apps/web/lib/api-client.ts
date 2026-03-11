import { apiPath } from "./network";
import { clearAuthSession, storeAuthSession } from "./auth-storage";

let refreshInFlight: Promise<boolean> | null = null;

export async function apiFetch(input: string, init?: RequestInit): Promise<Response> {
  const headers = normalizeHeaders(init?.headers);

  if (typeof window !== "undefined") {
    const accessToken = window.localStorage.getItem("accessToken");

    if (accessToken && !hasHeader(headers, "Authorization")) {
      headers["Authorization"] = `Bearer ${accessToken}`;
    }
  }

  const response = await fetch(apiPath(input), {
    ...init,
    headers,
  });

  if (typeof window === "undefined") {
    return response;
  }

  const isRefreshRequest = input === "/api/v1/auth/refresh";
  const alreadyRetried = getHeaderValue(headers, "X-Auth-Refresh-Retry") === "1";
  if (response.status !== 401 || isRefreshRequest || alreadyRetried) {
    return response;
  }

  const refreshed = await attemptTokenRefresh();
  if (!refreshed) {
    return response;
  }

  const retryHeaders = normalizeHeaders(init?.headers);
  const refreshedAccessToken = window.localStorage.getItem("accessToken");
  if (refreshedAccessToken && !hasHeader(retryHeaders, "Authorization")) {
    retryHeaders["Authorization"] = `Bearer ${refreshedAccessToken}`;
  }
  retryHeaders["X-Auth-Refresh-Retry"] = "1";

  return fetch(apiPath(input), {
    ...init,
    headers: retryHeaders,
  });
}

export function userIdHeaders(userId: string | null, base?: HeadersInit): HeadersInit {
  void userId;
  return normalizeHeaders(base);
}

function normalizeHeaders(base?: HeadersInit): Record<string, string> {
  const headers: Record<string, string> = {};

  if (!base) {
    return headers;
  }

  if (base instanceof Headers) {
    base.forEach((value, key) => {
      headers[key] = value;
    });
    return headers;
  }

  if (Array.isArray(base)) {
    for (const [key, value] of base) {
      headers[key] = value;
    }
    return headers;
  }

  Object.assign(headers, base);
  return headers;
}

function hasHeader(headers: Record<string, string>, headerName: string): boolean {
  const needle = headerName.toLowerCase();
  return Object.keys(headers).some((key) => key.toLowerCase() === needle);
}

function getHeaderValue(headers: Record<string, string>, headerName: string): string | undefined {
  const needle = headerName.toLowerCase();
  const key = Object.keys(headers).find((k) => k.toLowerCase() === needle);
  return key ? headers[key] : undefined;
}

async function attemptTokenRefresh(): Promise<boolean> {
  if (typeof window === "undefined") {
    return false;
  }
  if (refreshInFlight) {
    return refreshInFlight;
  }

  refreshInFlight = (async () => {
    const refreshToken = window.localStorage.getItem("refreshToken");
    if (!refreshToken) {
      return false;
    }

    try {
      const response = await fetch(apiPath("/api/v1/auth/refresh"), {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ refreshToken }),
      });
      if (!response.ok) {
        clearStoredTokens();
        return false;
      }

      const payload = (await response.json()) as {
        accessToken?: string;
        refreshToken?: string;
        userId?: string;
        id?: string;
        username?: string;
      };
      if (!payload.accessToken || !payload.refreshToken) {
        clearStoredTokens();
        return false;
      }

      storeAuthSession(payload);
      return true;
    } catch {
      clearStoredTokens();
      return false;
    }
  })();

  try {
    return await refreshInFlight;
  } finally {
    refreshInFlight = null;
  }
}

function clearStoredTokens() {
  clearAuthSession();
}
