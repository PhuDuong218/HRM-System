import '@angular/compiler';
import {Injector,NgZone,PendingTasks,createEnvironmentInjector,runInInjectionContext} from '@angular/core';
import {provideHttpClient,withInterceptors} from '@angular/common/http';
import {HttpTestingController,provideHttpClientTesting} from '@angular/common/http/testing';
import {Router} from '@angular/router';
import {AuthService} from '../src/app/services/auth.service';
import {authInterceptor} from '../src/app/services/auth.interceptor';
import {personnelPortalGuard} from '../src/app/services/personnel-portal.guard';
export function setup(){
 const injector=createEnvironmentInjector([provideHttpClient(withInterceptors([authInterceptor])),provideHttpClientTesting(),
  {provide:PendingTasks,useValue:{add:()=>()=>{}}},
  {provide:NgZone,useValue:{run:(fn:()=>unknown)=>fn()}},
  {provide:Router,useValue:{createUrlTree:(paths:string[])=>paths.join('/'),navigateByUrl:()=>Promise.resolve(true)}},AuthService],Injector.NULL as any);
 return {injector,auth:injector.get(AuthService),http:injector.get(HttpTestingController),guard:(url:string)=>runInInjectionContext(injector,()=>personnelPortalGuard({} as any,{url} as any))};
}
