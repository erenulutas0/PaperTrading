const DEFAULT_WS_PATH = "/ws";

export function apiPath(path: string): string {
  if (!path) return "/";
  return path.startsWith("/") ? path : `/${path}`;
}

export function wsBrokerUrl(path: string = DEFAULT_WS_PATH): string {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  const explicitWsUrl = process.env.NEXT_PUBLIC_WS_BROKER_URL;
  if (explicitWsUrl && explicitWsUrl.length > 0) {
    return `${explicitWsUrl.replace(/\/$/, "")}${normalizedPath}`;
  }

  if (typeof window === "undefined") {
    const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL;
    if (apiBase && apiBase.length > 0) {
      const wsBase = apiBase
        .replace(/^https:\/\//, "wss://")
        .replace(/^http:\/\//, "ws://")
        .replace(/\/$/, "");
      return `${wsBase}${normalizedPath}`;
    }
    return normalizedPath;
  }

  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  return `${protocol}://${window.location.host}${normalizedPath}`;
}

export function wsHttpUrl(path: string = DEFAULT_WS_PATH): string {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  const explicitHttpWsUrl = process.env.NEXT_PUBLIC_WS_HTTP_URL;
  if (explicitHttpWsUrl && explicitHttpWsUrl.length > 0) {
    return `${explicitHttpWsUrl.replace(/\/$/, "")}${normalizedPath}`;
  }

  return normalizedPath;
}
