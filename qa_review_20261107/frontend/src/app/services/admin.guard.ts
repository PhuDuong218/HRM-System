import {inject} from '@angular/core';
import {Router,CanActivateFn} from '@angular/router';
import {catchError,map,of} from 'rxjs';
import {AuthService} from './auth.service';
/** UI gate only; every protected API retains its backend authorization. */
export const adminGuard:CanActivateFn=()=>{
 const auth=inject(AuthService),router=inject(Router);
 const decide=(role:string)=>role==='ADMIN'?true:router.createUrlTree(['/forbidden']);
 const profile=auth.profile();if(profile)return decide(profile.role);
 return auth.loadCurrentUser().pipe(map(profile=>decide(profile.role)),catchError(()=>of(router.createUrlTree(['/login']))));
};
