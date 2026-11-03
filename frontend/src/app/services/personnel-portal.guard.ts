import {inject} from '@angular/core';
import {CanActivateChildFn,Router} from '@angular/router';
import {catchError,map,of} from 'rxjs';
import {AuthService,CurrentUser} from './auth.service';

export const personnelPortalGuard:CanActivateChildFn=(_route,state)=>{
 const auth=inject(AuthService),router=inject(Router);
 const decide=(user:CurrentUser)=>['ADMIN','HR'].includes(user.role)?true:
   user.departmentHead?(state.url.split('?')[0]==='/requests/head'?true:router.createUrlTree(['/requests/head'])):router.createUrlTree(['/login']);
 return auth.loadCurrentUser().pipe(map(decide),catchError(()=>of(router.createUrlTree(['/login']))));
};
