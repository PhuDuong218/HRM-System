import '@angular/compiler';
import {Injector,PendingTasks,createEnvironmentInjector,runInInjectionContext} from '@angular/core';
import {provideHttpClient} from '@angular/common/http';
import {provideHttpClientTesting,HttpTestingController} from '@angular/common/http/testing';
import {EmployeesPage} from '../src/app/pages/employees/employees';
import {EmployeeService} from '../src/app/services/employee.service';
import {AuthService} from '../src/app/services/auth.service';
import {LanguageService} from '../src/app/services/language.service';
export {employeeFormErrors} from '../src/app/pages/employees/employee-form.validation';
export function setup(){
 const injector=createEnvironmentInjector([provideHttpClient(),provideHttpClientTesting(),EmployeeService,
  {provide:PendingTasks,useValue:{add:()=>()=>{}}},
  {provide:AuthService,useValue:{profile:()=>({role:'ADMIN',employeeCode:'ADMIN'})}},
  {provide:LanguageService,useValue:{t:(s:string)=>s}}],Injector.NULL as any);
 return {injector,http:injector.get(HttpTestingController),page:runInInjectionContext(injector,()=>new EmployeesPage())};
}
