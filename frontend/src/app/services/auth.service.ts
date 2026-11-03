import { DestroyRef, Injectable, NgZone, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { catchError, defer, map, of, tap, timeout } from 'rxjs';
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

export interface CurrentUser {
  username: string;
  role: string;
  employeeCode: string | null;
  fullName: string;
  birthDate: string | null;
  email: string | null;
  phone: string | null;
  address: string | null;
  identityNumber: string | null;
  hireDate: string | null;
  employmentStatus: string | null;
  departmentName: string | null;
  positionName: string | null;
}

const SESSION_KEY = 'hrm.session';

export class ManagementAccessDeniedError extends Error {
  constructor() {
    super('Cổng quản lý chỉ dành cho tài khoản ADMIN hoặc HR.');
  }
}

function managementRole(token: string): boolean {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return false;
    const payload: unknown = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
    if (!payload || typeof payload !== 'object' || !('role' in payload)) return false;
    return payload.role === 'ADMIN' || payload.role === 'HR';
  } catch {
    return false;
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private zone = inject(NgZone);
  private destroyRef = inject(DestroyRef);
  private session = signal<Session | null>(this.restoreSession());
  private currentUser = signal<CurrentUser | null>(null);
  private expirationTimer: ReturnType<typeof setTimeout> | null = null;
  readonly sessionNotice = signal('');

  constructor() {
    this.scheduleExpiration();
    // Background tabs can have their timers paused; recheck as soon as the user returns.
    if (typeof document !== 'undefined') {
      const recheck = () => this.checkExpiration();
      document.addEventListener('visibilitychange', recheck);
      this.destroyRef.onDestroy(() => document.removeEventListener('visibilitychange', recheck));
    }
    if (typeof window !== 'undefined') {
      const recheck = () => this.checkExpiration();
      window.addEventListener('focus', recheck);
      this.destroyRef.onDestroy(() => window.removeEventListener('focus', recheck));
    }
    this.destroyRef.onDestroy(() => this.cancelExpiration());
  }

  login(username: string, password: string) {
    return defer(() => {
      const requestStartedAt = Date.now();
      return this.http.post<LoginResponse>(`${API_BASE_URL}/auth/login`, { username, password }).pipe(
      timeout(15000),
      tap(response => {
        if (typeof response.accessToken !== 'string' || !response.accessToken ||
            response.tokenType?.toLowerCase() !== 'bearer' ||
            !Number.isFinite(response.expiresInSeconds) || response.expiresInSeconds <= 0) {
          throw new Error('Phản hồi đăng nhập không hợp lệ.');
        }
        if (!managementRole(response.accessToken)) {
          this.clear();
          throw new ManagementAccessDeniedError();
        }
        const session: Session = {
          accessToken: response.accessToken,
          username,
          expiresAt: requestStartedAt + response.expiresInSeconds * 1000
        };
        this.currentUser.set(null);
        this.session.set(session);
        try { sessionStorage.setItem(SESSION_KEY, JSON.stringify(session)); } catch { /* Keep the session in memory. */ }
        this.sessionNotice.set('');
        this.scheduleExpiration();
      })
      );
    });
  }

  logout() {
    return defer(() => {
      const session = this.session();
      return this.http.post<void>(`${API_BASE_URL}/auth/logout`, {}).pipe(
        timeout(3000),
        catchError(() => of(void 0)),
        map(() => {
          // Do not clear a newer session if this request finishes after another login.
          if (this.session() !== null && this.session() !== session) return false;
          this.clear();
          return true;
        })
      );
    });
  }

  changePassword(currentPassword: string, newPassword: string, confirmPassword: string) {
    return this.http.post<void>(`${API_BASE_URL}/auth/change-password`, {
      currentPassword, newPassword, confirmPassword
    }).pipe(timeout(15000), tap(() => this.clear()));
  }

  requestRecovery(username: string) {
    return this.http.post<void>(`${API_BASE_URL}/auth/forgot-password`, { username }).pipe(timeout(15000));
  }

  verifyRecovery(username: string, code: string) {
    return this.http.post<void>(`${API_BASE_URL}/auth/verify-recovery-code`, { username, code }).pipe(timeout(15000));
  }

  resetForgottenPassword(username: string, code: string, newPassword: string, confirmPassword: string) {
    return this.http.post<void>(`${API_BASE_URL}/auth/reset-forgotten-password`, { username, code, newPassword, confirmPassword }).pipe(timeout(15000));
  }

  token(): string | null {
    const session = this.session();
    if (!session) return null;
    if (session.expiresAt <= Date.now()) {
      this.endInvalidSession('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
      return null;
    }
    return session.accessToken;
  }

  username(): string {
    return this.token() ? this.session()?.username ?? '' : '';
  }

  displayName(): string {
    return this.token() ? this.currentUser()?.fullName || this.session()?.username || '' : '';
  }

  profile(): CurrentUser | null {
    return this.token() ? this.currentUser() : null;
  }

  loadCurrentUser() {
    const token = this.token();
    return this.http.get<CurrentUser>(`${API_BASE_URL}/auth/me`).pipe(
      timeout(15000),
      tap(user => {
        if (token && token === this.token() && typeof user.fullName === 'string') {
          this.currentUser.set(user);
        }
      })
    );
  }

  clear() {
    this.cancelExpiration();
    this.session.set(null);
    this.currentUser.set(null);
    try { sessionStorage.removeItem(SESSION_KEY); } catch { /* Storage is optional. */ }
  }

  invalidateSession(token: string | null): void {
    if (token && this.session()?.accessToken === token) {
      this.endInvalidSession('Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại.');
    }
  }

  exitExpiredSession(): void {
    this.sessionNotice.set('');
    void this.router.navigateByUrl('/login', { replaceUrl: true });
  }

  private endInvalidSession(message: string): void {
    if (!this.session()) return;
    // The timer or an HTTP callback can run outside Angular's zone.
    this.zone.run(() => {
      if (!this.session()) return;
      this.clear();
      this.sessionNotice.set(message);
    });
  }

  private checkExpiration(): void {
    if (this.session() && this.session()!.expiresAt <= Date.now()) {
      this.endInvalidSession('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
    }
  }

  private scheduleExpiration(): void {
    this.cancelExpiration();
    const session = this.session();
    if (!session) return;
    const delay = Math.max(0, Math.min(session.expiresAt - Date.now(), 2147483647));
    this.expirationTimer = setTimeout(() => {
      this.expirationTimer = null;
      if (this.session() !== session) return;
      if (session.expiresAt <= Date.now()) this.checkExpiration();
      else this.scheduleExpiration();
    }, delay);
  }

  private cancelExpiration(): void {
    if (this.expirationTimer !== null) clearTimeout(this.expirationTimer);
    this.expirationTimer = null;
  }

  private restoreSession(): Session | null {
    try {
      const saved = sessionStorage.getItem(SESSION_KEY);
      if (!saved) return null;
      const session: unknown = JSON.parse(saved);
      if (session && typeof session === 'object' &&
          'accessToken' in session && typeof session.accessToken === 'string' && session.accessToken &&
          'username' in session && typeof session.username === 'string' &&
          'expiresAt' in session && typeof session.expiresAt === 'number' && Number.isFinite(session.expiresAt) &&
          managementRole(session.accessToken)) {
        return session as Session;
      }
      sessionStorage.removeItem(SESSION_KEY);
    } catch { /* Storage is optional. */ }
    return null;
  }
}
