import 'zone.js';
import '@angular/compiler';
import { Component, ErrorHandler, Injector, NgZone, PendingTasks, createEnvironmentInjector, ɵConsole, ɵINJECTOR_SCOPE } from '@angular/core';
import { provideLocationMocks, SpyLocation } from '@angular/common/testing';
import { DOCUMENT, Location } from '@angular/common';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../src/app/services/auth.service';
import { guestGuard } from '../src/app/services/auth.guard';

@Component({ standalone: true, template: '' })
class Page {}

export function setupRouter() {
  let token: string | null = null;
  const injector = createEnvironmentInjector([
    { provide: ɵINJECTOR_SCOPE, useValue: 'root' },
    { provide: ɵConsole, useValue: console },
    { provide: DOCUMENT, useValue: { title: '' } },
    provideRouter([
      { path: 'login', component: Page, canActivate: [guestGuard] },
      { path: 'overview', component: Page },
      { path: 'employees', component: Page }
    ]),
    provideLocationMocks(),
    { provide: AuthService, useValue: { token: () => token } },
    { provide: NgZone, useValue: new NgZone({ enableLongStackTrace: false }) },
    { provide: ErrorHandler, useValue: new ErrorHandler() },
    { provide: PendingTasks, useValue: { add: () => () => {} } }
  ], Injector.NULL);
  return { injector, router: injector.get(Router), location: injector.get(Location) as SpyLocation,
    setToken: (value: string | null) => { token = value; } };
}
