'use client';

export const AUTH_STATE_CHANGED_EVENT = 'auth-state-changed';

interface AuthSessionPayload {
  id?: string;
  userId?: string;
  username?: string;
  accessToken?: string;
  refreshToken?: string;
}

export function storeAuthSession(payload: AuthSessionPayload) {
  if (typeof window === 'undefined') {
    return;
  }

  const idValue = payload.id ?? payload.userId;
  if (idValue) {
    window.localStorage.setItem('userId', idValue);
  }
  if (payload.username) {
    window.localStorage.setItem('username', payload.username);
  }
  if (payload.accessToken) {
    window.localStorage.setItem('accessToken', payload.accessToken);
  }
  if (payload.refreshToken) {
    window.localStorage.setItem('refreshToken', payload.refreshToken);
  }

  notifyAuthStateChanged();
}

export function clearAuthSession() {
  if (typeof window === 'undefined') {
    return;
  }

  window.localStorage.removeItem('accessToken');
  window.localStorage.removeItem('refreshToken');
  window.localStorage.removeItem('userId');
  window.localStorage.removeItem('username');
  notifyAuthStateChanged();
}

export function notifyAuthStateChanged() {
  if (typeof window === 'undefined') {
    return;
  }
  window.dispatchEvent(new Event(AUTH_STATE_CHANGED_EVENT));
}
