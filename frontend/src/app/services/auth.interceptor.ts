import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { API_BASE_URL } from './api.config';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const isApiRequest = request.url.startsWith(`${API_BASE_URL}/`);
  const isPublic = request.url === `${API_BASE_URL}/auth/login` || request.url === `${API_BASE_URL}/health`;
  if (!isApiRequest || isPublic) return next(request);

  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token();
  let authenticatedRequest = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;
  if(['POST','PUT','PATCH','DELETE'].includes(request.method)&&request.body&&typeof request.body==='object'&&'reason' in request.body&&typeof request.body.reason==='string'&&request.body.reason.trim()){
    authenticatedRequest=authenticatedRequest.clone({setHeaders:changeReasonHeaders(request.body.reason)});
  }

  return next(authenticatedRequest).pipe(catchError((error: unknown) => {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      // A late response from an earlier session must not sign out a newer login.
      const currentToken = auth.token();
      if (token === currentToken) {
        auth.clear();
        void router.navigateByUrl('/login');
      }
    }
    return throwError(() => error);
  }));
};

/** UTF-8 base64 keeps Vietnamese reasons valid in HTTP headers. It never carries identity or permissions. */
export function changeReasonHeaders(reason:string):Record<string,string>{
 return reason.trim()?{'X-Change-Reason':btoa(String.fromCharCode(...new TextEncoder().encode(reason.trim())))}:{};
}
