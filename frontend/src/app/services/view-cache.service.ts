import { Injectable, inject } from '@angular/core';
import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { Subject, tap } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { AuthService } from './auth.service';

export interface CachedEntry<T> {
  data: T;
  updatedAt: number;
  remainingMs: number;
}

const MUTATION_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);
const IGNORED_MUTATION_PATHS = [
  '/auth/login',
  '/auth/web/login',
  '/auth/refresh',
  '/auth/logout',
  '/auth/forgot-password',
  '/auth/verify-recovery-code',
  '/auth/reset-forgotten-password',
];

@Injectable({
  providedIn: 'root'
})
export class ViewCacheService {
  static readonly DEFAULT_TTL_MS = 300_000; // 5 phút

  private auth = inject(AuthService, { optional: true });
  private store = new Map<string, { data: unknown; updatedAt: number; scope: string }>();
  private lastScope = '';
  private dataChangedSubject = new Subject<string>();
  readonly dataChanged$ = this.dataChangedSubject.asObservable();

  private currentScope(): string {
    const scope = this.auth?.token() ? (this.auth.username() || 'authenticated') : 'anonymous';
    if (this.lastScope && this.lastScope !== scope) {
      this.store.clear();
    }
    this.lastScope = scope;
    return scope;
  }

  get<T>(key: string, maxAgeMs = ViewCacheService.DEFAULT_TTL_MS): CachedEntry<T> | null {
    const scope = this.currentScope();
    const item = this.store.get(key);
    if (!item || item.scope !== scope) {
      return null;
    }
    const age = Date.now() - item.updatedAt;
    if (age >= maxAgeMs) {
      this.store.delete(key);
      return null;
    }
    return {
      data: item.data as T,
      updatedAt: item.updatedAt,
      remainingMs: Math.max(1000, maxAgeMs - age)
    };
  }

  set<T>(key: string, data: T): number {
    const scope = this.currentScope();
    const updatedAt = Date.now();
    this.store.set(key, { data, updatedAt, scope });
    return updatedAt;
  }

  invalidate(prefix: string): void {
    for (const key of Array.from(this.store.keys())) {
      if (key.startsWith(prefix)) {
        this.store.delete(key);
      }
    }
  }

  notifyDataChanged(sourceUrl = ''): void {
    for (const key of Array.from(this.store.keys())) {
      if (key !== 'schedules:ui-state') {
        this.store.delete(key);
      }
    }
    this.dataChangedSubject.next(sourceUrl);
  }

  clear(): void {
    this.store.clear();
  }
}

export const viewCacheInvalidationInterceptor: HttpInterceptorFn = (request, next) => {
  if (!MUTATION_METHODS.has(request.method) || !request.url.startsWith(`${API_BASE_URL}/`)) {
    return next(request);
  }
  const path = request.url.slice(API_BASE_URL.length);
  if (IGNORED_MUTATION_PATHS.some(ignored => path.startsWith(ignored)) || path.includes('/export')) {
    return next(request);
  }
  const cache = inject(ViewCacheService);
  return next(request).pipe(
    tap(event => {
      if (event instanceof HttpResponse && event.status >= 200 && event.status < 300) {
        cache.notifyDataChanged(path);
      }
    })
  );
};
