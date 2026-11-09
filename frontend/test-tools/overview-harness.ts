import '@angular/compiler';
import { ElementRef, Injector, PendingTasks, createEnvironmentInjector, runInInjectionContext } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { AuthService } from '../src/app/services/auth.service';
import { authInterceptor } from '../src/app/services/auth.interceptor';
import { ReportService } from '../src/app/services/report.service';
import { LanguageService } from '../src/app/services/language.service';
import { OverviewPage } from '../src/app/pages/overview/overview';
import { AttendancePage } from '../src/app/pages/attendance/attendance';
import { AttendanceApiService } from '../src/app/pages/attendance/attendance-api.service';
import { OvertimePage } from '../src/app/pages/overtime/overtime';
import { OvertimeApiService } from '../src/app/pages/overtime/overtime-api.service';
import { ActionDialogService } from '../src/app/shared/ui/action-dialog';

export function setup() {
  const profile = new Subject<{role: string}>();
  const auth = { token: () => 'test-token', clear: () => {}, loadCurrentUser: () => profile.asObservable() };
  const injector = createEnvironmentInjector([
    provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(),
    { provide: PendingTasks, useValue: { add: () => () => {} } },
    { provide: AuthService, useValue: auth },
    { provide: Router, useValue: { navigateByUrl: () => Promise.resolve(true) } },
    ReportService, LanguageService
  ], Injector.NULL);
  return { injector, profile, page: runInInjectionContext(injector, () => new OverviewPage()), http: injector.get(HttpTestingController) };
}

export function queues() {
  const params=new BehaviorSubject(convertToParamMap({}));const navigations:unknown[]=[];
  const injector=createEnvironmentInjector([
    provideHttpClient(withInterceptors([authInterceptor])),provideHttpClientTesting(),
    {provide:PendingTasks,useValue:{add:()=>()=>{}}},
    {provide:AuthService,useValue:{token:()=>'test-token',clear:()=>{}}},
    {provide:ActivatedRoute,useValue:{queryParamMap:params}},
    {provide:Router,useValue:{navigate:(_paths:unknown,options:unknown)=>{navigations.push(options);return Promise.resolve(true);},navigateByUrl:()=>Promise.resolve(true)}},
    {provide:ElementRef,useValue:new ElementRef({querySelector:()=>null})},
    {provide:ActionDialogService,useValue:{}},AttendanceApiService,OvertimeApiService
  ],Injector.NULL);
  return {injector,http:injector.get(HttpTestingController),params:(value:Record<string,string>)=>params.next(convertToParamMap(value)),navigations,
    attendance:()=>runInInjectionContext(injector,()=>new AttendancePage()),overtime:()=>runInInjectionContext(injector,()=>new OvertimePage())};
}
