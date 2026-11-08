import '@angular/compiler';
import {Injector,PendingTasks,createEnvironmentInjector,runInInjectionContext} from '@angular/core';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController,provideHttpClientTesting} from '@angular/common/http/testing';
import {HrScopeService} from '../src/app/services/hr-scope.service';
import {HrScopes} from '../src/app/pages/accounts/hr-scopes';
import {LanguageService} from '../src/app/services/language.service';
export {scopeInstant,scopeLocalInput,scopeUtcInput} from '../src/app/services/hr-scope.service';
export function setup(){const injector=createEnvironmentInjector([provideHttpClient(),provideHttpClientTesting(),HrScopeService,{provide:PendingTasks,useValue:{add:()=>()=>{}}},{provide:LanguageService,useValue:{t:(s:string)=>s,current:()=> 'vi'}}],Injector.NULL as any);const page=runInInjectionContext(injector,()=>new HrScopes());page.accountId=34;return {page,http:injector.get(HttpTestingController),injector};}
