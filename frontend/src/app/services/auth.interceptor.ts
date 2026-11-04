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
  let authenticatedRequest = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;
  if(['POST','PUT','PATCH','DELETE'].includes(request.method)&&request.body&&typeof request.body==='object'&&'reason' in request.body&&typeof request.body.reason==='string'&&request.body.reason.trim()){
    authenticatedRequest=authenticatedRequest.clone({setHeaders:changeReasonHeaders(request.body.reason)});
  }

  return next(authenticatedRequest).pipe(catchError((error: unknown) => {
    if (error instanceof HttpErrorResponse && error.status === 401 && request.url !== `${API_BASE_URL}/auth/logout`) {
      // A late response from an earlier session must not sign out a newer login.
      auth.invalidateSession(token);
    }
    return throwError(() => error);
  }));
};

/** UTF-8 base64 keeps Vietnamese reasons valid in HTTP headers. It never carries identity or permissions. */
export function changeReasonHeaders(reason:string):Record<string,string>{
 return reason.trim()?{'X-Change-Reason':btoa(String.fromCharCode(...new TextEncoder().encode(reason.trim())))}:{};
}
