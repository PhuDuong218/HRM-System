import '@angular/compiler';
import { Injector, NgZone, PendingTasks, createEnvironmentInjector } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from '../src/app/services/auth.service';
import { authInterceptor } from '../src/app/services/auth.interceptor';

export function setup() {
  const visits: string[] = [];
  const injector = createEnvironmentInjector([
    provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(),
    { provide: PendingTasks, useValue: { add: () => () => {} } },
    { provide: NgZone, useValue: { run: (fn: () => unknown) => fn() } },
    { provide: Router, useValue: { navigateByUrl: (url: string) => { visits.push(url); return Promise.resolve(true); } } },
    AuthService
  ], Injector.NULL);
  return { injector, visits, auth: injector.get(AuthService), http: injector.get(HttpTestingController) };
}
