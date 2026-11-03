import '@angular/compiler';
import { Injector, PendingTasks, createEnvironmentInjector, runInInjectionContext } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AccountsPage } from '../src/app/pages/accounts/accounts';
import { AccountService } from '../src/app/services/account.service';
import { AuthService } from '../src/app/services/auth.service';

export function setup() {
  const visits: string[] = [];
  const auth = { username: () => 'admin', clear: () => { visits.push('cleared'); } };
  const injector = createEnvironmentInjector([
    provideHttpClient(), provideHttpClientTesting(),
    { provide: PendingTasks, useValue: { add: () => () => {} } }, AccountService,
    { provide: AuthService, useValue: auth },
    { provide: Router, useValue: { navigateByUrl: (url: string) => { visits.push(url); return Promise.resolve(true); } } }
  ], Injector.NULL);
  const page = runInInjectionContext(injector, () => new AccountsPage());
  page.roles = [{ roleId: 2, roleCode: 'HR', roleName: 'Nhân sự', permissions: [] }];
  return { injector, page, visits, http: injector.get(HttpTestingController) };
}
