import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap, timeout } from 'rxjs';
import { API_BASE_URL } from './api.config';

interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

interface Session {
  accessToken: string;
  username: string;
  expiresAt: number;
}

const SESSION_KEY = 'hrm.session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private session = signal<Session | null>(this.restoreSession());

  login(username: string, password: string) {
    return this.http.post<LoginResponse>(`${API_BASE_URL}/auth/login`, { username, password }).pipe(
      timeout(15000),
      tap(response => {
        if (typeof response.accessToken !== 'string' || !response.accessToken ||
            response.tokenType?.toLowerCase() !== 'bearer' ||
            !Number.isFinite(response.expiresInSeconds) || response.expiresInSeconds <= 0) {
          throw new Error('Phản hồi đăng nhập không hợp lệ.');
        }
        const session: Session = {
          accessToken: response.accessToken,
          username,
          expiresAt: Date.now() + response.expiresInSeconds * 1000
        };
        this.session.set(session);
        try { sessionStorage.setItem(SESSION_KEY, JSON.stringify(session)); } catch { /* Keep the session in memory. */ }
      })
    );
  }

  logout() {
    return this.http.post<void>(`${API_BASE_URL}/auth/logout`, {}).pipe(
      timeout(15000),
      tap(() => this.clear())
    );
  }

  token(): string | null {
    const session = this.session();
    if (!session) return null;
    if (session.expiresAt <= Date.now()) {
      this.clear();
      return null;
    }
    return session.accessToken;
  }

  username(): string {
    return this.token() ? this.session()?.username ?? '' : '';
  }

  clear() {
    this.session.set(null);
    try { sessionStorage.removeItem(SESSION_KEY); } catch { /* Storage is optional. */ }
  }

  private restoreSession(): Session | null {
    try {
      const saved = sessionStorage.getItem(SESSION_KEY);
      if (!saved) return null;
      const session: unknown = JSON.parse(saved);
      if (session && typeof session === 'object' &&
          'accessToken' in session && typeof session.accessToken === 'string' && session.accessToken &&
          'username' in session && typeof session.username === 'string' &&
          'expiresAt' in session && typeof session.expiresAt === 'number' && session.expiresAt > Date.now()) {
        return session as Session;
      }
      sessionStorage.removeItem(SESSION_KEY);
    } catch { /* Storage is optional. */ }
    return null;
  }
}
