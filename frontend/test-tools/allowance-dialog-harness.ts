import '@angular/compiler';
import {Injector,createEnvironmentInjector,runInInjectionContext} from '@angular/core';
import {NEVER} from 'rxjs';
import {AllowancesPage} from '../src/app/pages/allowances/allowances';
import {AllowanceService} from '../src/app/services/allowance.service';
import {EmployeeService} from '../src/app/services/employee.service';
import {ActionDialogService} from '../src/app/shared/ui/action-dialog';
export function setup(){
 let resolvePrompt:(value:string|null)=>void=()=>{};let calls=0;
 const injector=createEnvironmentInjector([
  {provide:AllowanceService,useValue:{deactivateType:()=>{calls++;return NEVER;}}},
  {provide:EmployeeService,useValue:{}},
  {provide:ActionDialogService,useValue:{prompt:()=>new Promise<string|null>(resolve=>resolvePrompt=resolve)}}
 ],Injector.NULL as any);
 return {injector,page:runInInjectionContext(injector,()=>new AllowancesPage()),resolve:(value:string|null)=>resolvePrompt(value),calls:()=>calls};
}
