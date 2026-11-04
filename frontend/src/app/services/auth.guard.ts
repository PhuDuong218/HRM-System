import { inject } from '@angular/core';
import { CanActivateFn, RedirectCommand, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  return inject(AuthService).token() ? true : inject(Router).createUrlTree(['/login']);
};

export const guestGuard: CanActivateFn = () => {
  return inject(AuthService).token()
    ? new RedirectCommand(inject(Router).parseUrl('/overview'), { replaceUrl: true })
    : true;
};
