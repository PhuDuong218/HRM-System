import '@angular/compiler';
import { Injector, PendingTasks, createEnvironmentInjector, runInInjectionContext } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from '../src/app/services/auth.service';
import { authInterceptor } from '../src/app/services/auth.interceptor';
import { ReportService } from '../src/app/services/report.service';
import { ReportsPage } from '../src/app/pages/reports/reports';

export function setup() {
  const auth = { token: () => 'test-token', clear: () => {} };
  const injector = createEnvironmentInjector([
    provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(),
    { provide: PendingTasks, useValue: { add: () => () => {} } },
    { provide: AuthService, useValue: auth },
    { provide: Router, useValue: { navigateByUrl: () => Promise.resolve(true) } },
    ReportService
  ], Injector.NULL);
  return { injector, service: runInInjectionContext(injector, () => injector.get(ReportService)),
    page: () => runInInjectionContext(injector, () => new ReportsPage()), http: injector.get(HttpTestingController) };
}
