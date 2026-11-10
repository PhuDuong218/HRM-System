import { DestroyRef, Injectable, NgZone, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Subscription, catchError, defer, map, of, tap, timeout, switchMap, forkJoin, throwError } from 'rxjs';
import { API_BASE_URL } from './api.config';

interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

interface Session {
  sessionId: string;
  accessToken: string;
  username: string;
  expiresAt: number;
  lastActivityAt: number;
  idleTimeoutMs: number;
}

export interface CurrentUser {
  departmentHead?: boolean;
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
const REFRESH_INTERVAL_MS = 30 * 1000;

export class ManagementAccessDeniedError extends Error {
  constructor() {
    super('Web dành cho ADMIN/HR hoặc trưởng phòng đang có nhiệm kỳ hợp lệ.');
  }
}

function managementRole(token: string): boolean {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return false;
    const payload: unknown = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
    if (!payload || typeof payload !== 'object' || !('role' in payload)) return false;
    return payload.role === 'ADMIN' || payload.role === 'HR' || payload.role === 'EMPLOYEE';
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
  private storedSession: string | null = null;
  private storageAvailable = true;
  private session = signal<Session | null>(this.restoreSession());
  private currentUser = signal<CurrentUser | null>(null);
  private expirationTimer: ReturnType<typeof setTimeout> | null = null;
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;
  private refreshRequest?: Subscription;
  private lastRefreshAt = Date.now();
  readonly sessionNotice = signal('');

  constructor() {
    this.scheduleExpiration();
    // Background tabs can have their timers paused; recheck as soon as the user returns.
    if (typeof document !== 'undefined') {
      const recheck = () => this.checkExpiration();
      document.addEventListener('visibilitychange', recheck);
      this.destroyRef.onDestroy(() => document.removeEventListener('visibilitychange', recheck));
      const activity = () => this.recordActivity();
      const events = ['pointerdown', 'keydown', 'wheel', 'touchstart', 'input'];
      for (const event of events) document.addEventListener(event, activity, { passive: true });
      this.destroyRef.onDestroy(() => { for (const event of events) document.removeEventListener(event, activity); });
    }
    if (typeof window !== 'undefined') {
      const recheck = () => this.checkExpiration();
      window.addEventListener('focus', recheck);
      this.destroyRef.onDestroy(() => window.removeEventListener('focus', recheck));
      const sharedSessionChanged = (event: StorageEvent) => {
        if (event.key === SESSION_KEY || event.key === null) this.zone.run(() => this.checkExpiration());
      };
      window.addEventListener('storage', sharedSessionChanged);
      this.destroyRef.onDestroy(() => window.removeEventListener('storage', sharedSessionChanged));
      // A restored document can contain UI from a login that has since ended.
      // Reload this entry so bootstrap and route guards use current session storage.
      const restorePage = (event: PageTransitionEvent) => {
        if (event.persisted) window.location.reload();
      };
      window.addEventListener('pageshow', restorePage);
      this.destroyRef.onDestroy(() => window.removeEventListener('pageshow', restorePage));
    }
    this.destroyRef.onDestroy(() => { this.cancelExpiration(); this.cancelRefresh(); });
  }

  login(username: string, password: string) {
    return defer(() => {
      const requestStartedAt = Date.now();
      let attemptedToken:string|null=null;
      return this.http.post<LoginResponse>(`${API_BASE_URL}/auth/web/login`, { username, password }).pipe(
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
          sessionId: globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`,
          accessToken: response.accessToken,
          username,
          expiresAt: requestStartedAt + response.expiresInSeconds * 1000,
          lastActivityAt: Date.now(),
          idleTimeoutMs: response.expiresInSeconds * 1000
        };
        attemptedToken=response.accessToken;
        this.cancelRefresh();
        this.lastRefreshAt = requestStartedAt;
        this.currentUser.set(null);
        this.session.set(session);
        this.persistSession(session);
        this.sessionNotice.set('');
        this.scheduleExpiration();
      }),
      switchMap(()=>this.loadCurrentUser()),
      catchError(error=>{if(attemptedToken&&this.session()?.accessToken===attemptedToken)this.clear();return throwError(()=>error);})
      );
    });
  }

  ownProfile() {return this.http.get<{email:string|null;phone:string|null;address:string|null;version:string}>(`${API_BASE_URL}/me/profile`);}

  updateProfile(contact: {email:string; phone:string; address:string;version:string;reason:string}) {
    return this.http.put<CurrentUser>(`${API_BASE_URL}/me/profile`, contact).pipe(
      tap(profile => this.currentUser.set({...this.currentUser(), ...profile}))
    );
  }

  logout() {
    return defer(() => {
      const session = this.session();
      return this.http.post<void>(`${API_BASE_URL}/auth/logout`, {}).pipe(
        timeout(3000),
        catchError(() => of(void 0)),
        map(() => {
          // Do not clear a newer session if this request finishes after another login.
          if (this.synchronizeSession() || this.session() !== session) return false;
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
    this.checkExpiration();
    return this.session()?.accessToken ?? null;
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
    const session = this.session();
    return forkJoin({user:this.http.get<CurrentUser>(`${API_BASE_URL}/auth/me`),
      capabilities:this.http.get<{departmentHead:boolean}>(`${API_BASE_URL}/employee-requests/capabilities`)}).pipe(
      timeout(15000),
      map(({user,capabilities})=>({...user,departmentHead:capabilities.departmentHead})),
      tap(user=>{
        // Ignore even successful responses from a previous login or a logged-out page.
        if (!session || this.session() !== session || !this.token()) {
          throw new Error('Phiên đăng nhập đã thay đổi.');
        }
        if(!['ADMIN','HR'].includes(user.role)&&!user.departmentHead){this.clear();throw new ManagementAccessDeniedError();}
        this.currentUser.set(user);
      })
    );
  }

  clear() {
    this.cancelExpiration();
    this.cancelRefresh();
    this.session.set(null);
    this.currentUser.set(null);
    this.storedSession = null;
    try { localStorage.removeItem(SESSION_KEY); } catch { this.storageAvailable = false; }
    try { sessionStorage.removeItem(SESSION_KEY); } catch { /* Remove legacy tab-only sessions. */ }
  }

  invalidateSession(token: string | null): void {
    this.synchronizeSession();
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
    this.synchronizeSession();
    const session = this.session();
    if (session && session.lastActivityAt + session.idleTimeoutMs <= Date.now()) {
      this.endInvalidSession('Phiên đăng nhập đã hết hạn do không hoạt động. Vui lòng đăng nhập lại.');
    } else if (session && session.expiresAt <= Date.now()) {
      this.endInvalidSession('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
    }
  }

  private recordActivity(): void {
    this.checkExpiration();
    const session = this.session();
    if (!session || (typeof document !== 'undefined' && document.hidden)) return;
    const now = Date.now();
    session.lastActivityAt = now;
    this.persistSession(session);
    this.scheduleExpiration();
    this.scheduleRefresh();
  }

  private scheduleRefresh(): void {
    const session = this.session();
    if (!session || this.refreshTimer !== null || this.refreshRequest) return;
    const refreshInterval = Math.min(REFRESH_INTERVAL_MS, session.idleTimeoutMs / 4);
    const expiryBuffer = Math.min(5000, session.idleTimeoutMs / 4);
    const delay = Math.max(0, Math.min(this.lastRefreshAt + refreshInterval, session.expiresAt - expiryBuffer) - Date.now());
    this.refreshTimer = setTimeout(() => {
      this.refreshTimer = null;
      this.refreshWebSession();
    }, delay);
  }

  private refreshWebSession(): void {
    this.checkExpiration();
    const session = this.session();
    if (!session) return;
    const startedAt = Date.now();
    this.lastRefreshAt = startedAt;
    this.refreshRequest = this.http.post<LoginResponse>(`${API_BASE_URL}/auth/web/refresh`, {}).pipe(timeout(15000)).subscribe({
      next: response => {
        this.refreshRequest = undefined;
        this.synchronizeSession();
        if (this.session() !== session) return;
        if (!response.accessToken || !managementRole(response.accessToken) ||
            response.tokenType?.toLowerCase() !== 'bearer' ||
            !Number.isFinite(response.expiresInSeconds) || response.expiresInSeconds <= 0) {
          this.endInvalidSession('Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại.');
          return;
        }
        session.accessToken = response.accessToken;
        session.expiresAt = startedAt + response.expiresInSeconds * 1000;
        session.idleTimeoutMs = response.expiresInSeconds * 1000;
        this.persistSession(session);
        this.scheduleExpiration();
        if (session.lastActivityAt > startedAt) this.scheduleRefresh();
      },
      error: () => { this.refreshRequest = undefined; }
    });
  }

  private cancelRefresh(): void {
    if (this.refreshTimer !== null) clearTimeout(this.refreshTimer);
    this.refreshTimer = null;
    this.refreshRequest?.unsubscribe();
    this.refreshRequest = undefined;
  }

  private scheduleExpiration(): void {
    this.cancelExpiration();
    const session = this.session();
    if (!session) return;
    const deadline = Math.min(session.expiresAt, session.lastActivityAt + session.idleTimeoutMs);
    const delay = Math.max(0, Math.min(deadline - Date.now(), 2147483647));
    this.expirationTimer = setTimeout(() => {
      this.expirationTimer = null;
      if (this.session() !== session) return;
      if (Math.min(session.expiresAt, session.lastActivityAt + session.idleTimeoutMs) <= Date.now()) this.checkExpiration();
      else this.scheduleExpiration();
    }, delay);
  }

  private cancelExpiration(): void {
    if (this.expirationTimer !== null) clearTimeout(this.expirationTimer);
    this.expirationTimer = null;
  }

  private restoreSession(): Session | null {
    // Retire the old tab-only copy so it cannot revive a signed-out session later.
    try { sessionStorage.removeItem(SESSION_KEY); } catch { /* Storage is optional. */ }
    try {
      this.storedSession = localStorage.getItem(SESSION_KEY);
      const session = this.parseSession(this.storedSession);
      if (this.storedSession && !session) {
        localStorage.removeItem(SESSION_KEY);
        this.storedSession = null;
      }
      return session;
    } catch { this.storageAvailable = false; return null; }
  }

  private persistSession(session: Session): void {
    try {
      const saved = JSON.stringify(session);
      localStorage.setItem(SESSION_KEY, saved);
      this.storedSession = saved;
      this.storageAvailable = true;
    } catch { this.storageAvailable = false; /* Keep the session in memory. */ }
  }

  private synchronizeSession(): boolean {
    if (!this.storageAvailable) return false;
    let saved: string | null;
    try { saved = localStorage.getItem(SESSION_KEY); } catch { return false; }
    if (saved === this.storedSession) return false;
    this.storedSession = saved;
    const shared = this.parseSession(saved);
    const current = this.session();
    if (shared && current && shared.sessionId === current.sessionId) {
      // Activity and token renewal in another tab belong to the same login.
      if (shared.accessToken !== current.accessToken) this.cancelRefresh();
      Object.assign(current, shared, { lastActivityAt: Math.max(current.lastActivityAt, shared.lastActivityAt) });
      this.scheduleExpiration();
      return false;
    }
    if (!shared && !current) return false;
    this.cancelExpiration();
    this.cancelRefresh();
    this.session.set(null);
    this.currentUser.set(null);
    this.sessionNotice.set('');
    // Recreate the page with the new identity and permissions, without adding history.
    // Do not let a form left open under the previous account use the new token.
    if (typeof window !== 'undefined') this.zone.run(() => window.location.reload());
    return true;
  }

  private parseSession(saved: string | null): Session | null {
    if (!saved) return null;
    try {
      const session: unknown = JSON.parse(saved);
      if (session && typeof session === 'object' &&
          'accessToken' in session && typeof session.accessToken === 'string' && session.accessToken &&
          'username' in session && typeof session.username === 'string' &&
          'expiresAt' in session && typeof session.expiresAt === 'number' && Number.isFinite(session.expiresAt) &&
          'lastActivityAt' in session && typeof session.lastActivityAt === 'number' && Number.isFinite(session.lastActivityAt) &&
          managementRole(session.accessToken)) {
        const restored = session as Session;
        if (typeof restored.sessionId !== 'string' || !restored.sessionId) restored.sessionId = restored.accessToken;
        if (!Number.isFinite(restored.idleTimeoutMs) || restored.idleTimeoutMs <= 0) restored.idleTimeoutMs = 600_000;
        return restored;
      }
    } catch { /* Ignore invalid stored values. */ }
    return null;
  }
}
