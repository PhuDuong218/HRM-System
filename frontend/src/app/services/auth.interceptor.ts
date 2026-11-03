import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { API_BASE_URL } from './api.config';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const isApiRequest = request.url.startsWith(`${API_BASE_URL}/`);
  const isPublic = [`${API_BASE_URL}/auth/login`, `${API_BASE_URL}/health`,
    `${API_BASE_URL}/auth/forgot-password`, `${API_BASE_URL}/auth/verify-recovery-code`,
    `${API_BASE_URL}/auth/reset-forgotten-password`].includes(request.url);
  if (!isApiRequest || isPublic) return next(request);

  const auth = inject(AuthService);
  const token = auth.token();
  const authenticatedRequest = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;

  return next(authenticatedRequest).pipe(catchError((error: unknown) => {
    if (error instanceof HttpErrorResponse && error.status === 401 && request.url !== `${API_BASE_URL}/auth/logout`) {
      // A late response from an earlier session must not sign out a newer login.
      auth.invalidateSession(token);
    }
    return throwError(() => error);
  }));
};
