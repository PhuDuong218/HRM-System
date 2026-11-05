import '@angular/compiler';
import {Injector,PendingTasks,createEnvironmentInjector,runInInjectionContext} from '@angular/core';
import {provideHttpClient} from '@angular/common/http';
import {provideHttpClientTesting,HttpTestingController} from '@angular/common/http/testing';
import {HeadRequestsPage} from '../src/app/pages/head-requests/head-requests';
import {HeadRequestsService} from '../src/app/services/head-requests.service';
export function setup(){const injector=createEnvironmentInjector([provideHttpClient(),provideHttpClientTesting(),HeadRequestsService,
 {provide:PendingTasks,useValue:{add:()=>()=>{}}}],Injector.NULL as any);
return {injector,http:injector.get(HttpTestingController),page:runInInjectionContext(injector,()=>new HeadRequestsPage())};}
