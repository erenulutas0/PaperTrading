import { apiPath } from "./network";

let refreshInFlight: Promise<boolean> | null = null;

export async function apiFetch(input: string, init?: RequestInit): Promise<Response> {
  const headers = normalizeHeaders(init?.headers);

  if (typeof window !== "undefined") {
    const accessToken = window.localStorage.getItem("accessToken");
    const userId = window.localStorage.getItem("userId");

    if (accessToken && !hasHeader(headers, "Authorization")) {
      headers["Authorization"] = `Bearer ${accessToken}`;
    }
    // Transitional fallback only when token is absent.
    if (!accessToken && userId && !hasHeader(headers, "X-User-Id")) {
      headers["X-User-Id"] = userId;
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
  const userId = window.localStorage.getItem("userId");
  if (refreshedAccessToken && !hasHeader(retryHeaders, "Authorization")) {
    retryHeaders["Authorization"] = `Bearer ${refreshedAccessToken}`;
  }
  if (!refreshedAccessToken && userId && !hasHeader(retryHeaders, "X-User-Id")) {
    retryHeaders["X-User-Id"] = userId;
  }
  retryHeaders["X-Auth-Refresh-Retry"] = "1";

  return fetch(apiPath(input), {
    ...init,
    headers: retryHeaders,
  });
}

export function userIdHeaders(userId: string | null, base?: HeadersInit): HeadersInit {
  const headers = normalizeHeaders(base);
  const hasAccessToken =
    typeof window !== "undefined" && Boolean(window.localStorage.getItem("accessToken"));

  // Transitional fallback only when token is absent.
  if (!hasAccessToken && userId && !hasHeader(headers, "X-User-Id")) {
    headers["X-User-Id"] = userId;
  }

  return headers;
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

      window.localStorage.setItem("accessToken", payload.accessToken);
      window.localStorage.setItem("refreshToken", payload.refreshToken);
      const idValue = payload.id ?? payload.userId;
      if (idValue) {
        window.localStorage.setItem("userId", idValue);
      }
      if (payload.username) {
        window.localStorage.setItem("username", payload.username);
      }
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
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.removeItem("accessToken");
  window.localStorage.removeItem("refreshToken");
}
