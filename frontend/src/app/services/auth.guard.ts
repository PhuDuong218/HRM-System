import { inject } from '@angular/core';
import { CanActivateFn, RedirectCommand, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  return inject(AuthService).token()
    ? true
    : new RedirectCommand(inject(Router).parseUrl('/login'), { replaceUrl: true });
};

export const guestGuard: CanActivateFn = () => {
  const auth=inject(AuthService);
  const profile=auth.profile?.();
  const home=profile?.role==='EMPLOYEE' && profile.departmentHead ? '/requests/head' : '/overview';
  return auth.token()
    ? new RedirectCommand(inject(Router).parseUrl(home), { replaceUrl: true })
    : true;
};
