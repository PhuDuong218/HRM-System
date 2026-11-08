import '@angular/compiler';
import {Injector,PendingTasks,createEnvironmentInjector,runInInjectionContext} from '@angular/core';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController,provideHttpClientTesting} from '@angular/common/http/testing';
import {AccountService} from '../src/app/services/account.service';
import {EmployeeService} from '../src/app/services/employee.service';
import {AccountsPage} from '../src/app/pages/accounts/accounts';
export {toListPage} from '../src/app/models/page.model';

export function setup(){
  const injector=createEnvironmentInjector([provideHttpClient(),provideHttpClientTesting(),
    {provide:PendingTasks,useValue:{add:()=>()=>{}}},AccountService,EmployeeService],Injector.NULL as any);
  const page=runInInjectionContext(injector,()=>new AccountsPage());
  let closed=0;
  (page as any).actionDialog={nativeElement:{showModal(){},close(){closed++;page.clearDialog();}}};
  return {page,http:injector.get(HttpTestingController),injector,closed:()=>closed};
}
