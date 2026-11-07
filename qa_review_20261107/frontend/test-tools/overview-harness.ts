import '@angular/compiler';
import { Injector, PendingTasks, createEnvironmentInjector, runInInjectionContext } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { AuthService } from '../src/app/services/auth.service';
import { authInterceptor } from '../src/app/services/auth.interceptor';
import { ReportService } from '../src/app/services/report.service';
import { LanguageService } from '../src/app/services/language.service';
import { OverviewPage } from '../src/app/pages/overview/overview';

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
