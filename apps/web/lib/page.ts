export interface PagedResponse<T> {
  content: T[];
}

export function extractContent<T>(payload: unknown): T[] {
  if (Array.isArray(payload)) {
    return payload as T[];
  }

  if (payload && typeof payload === "object" && Array.isArray((payload as PagedResponse<T>).content)) {
    return (payload as PagedResponse<T>).content;
  }

  return [];
}
