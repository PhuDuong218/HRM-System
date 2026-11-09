import 'zone.js';
import '@angular/compiler';
import { Component, ErrorHandler, Injector, NgZone, PendingTasks, createEnvironmentInjector, ɵConsole, ɵINJECTOR_SCOPE } from '@angular/core';
import { provideLocationMocks, SpyLocation } from '@angular/common/testing';
import { DOCUMENT, Location } from '@angular/common';
import { Router, provideRouter, withRouterConfig } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../src/app/services/auth.service';
import { authGuard, guestGuard } from '../src/app/services/auth.guard';
import { personnelPortalGuard } from '../src/app/services/personnel-portal.guard';
import { adminGuard } from '../src/app/services/admin.guard';

@Component({ standalone: true, template: '' })
class Page {}

export function setupRouter() {
  let token: string | null = null;
  let role = 'ADMIN';
  const profileChecks: string[] = [];
  const injector = createEnvironmentInjector([
    { provide: ɵINJECTOR_SCOPE, useValue: 'root' },
    { provide: ɵConsole, useValue: console },
    { provide: DOCUMENT, useValue: { title: '' } },
    provideRouter([
      { path: 'before', component: Page },
      { path: 'login', component: Page, canActivate: [guestGuard] },
      { path: '', canActivate: [authGuard], canActivateChild: [authGuard, personnelPortalGuard], children:
        ['overview', 'employees', 'contracts', 'requests/head', 'accounts'].map(path => ({
          path, component: Page, runGuardsAndResolvers: 'always',
          canActivate: path === 'accounts' ? [adminGuard] : []
        }))
      }
    ], withRouterConfig({ canceledNavigationResolution: 'computed' })),
    provideLocationMocks(),
    { provide: AuthService, useValue: { token: () => token,
      profile: () => ({ role, departmentHead: role === 'EMPLOYEE' }),
      loadCurrentUser: () => {
        profileChecks.push(token!);
        return of({ role, departmentHead: role === 'EMPLOYEE' });
      }
    } },
    { provide: NgZone, useValue: new NgZone({ enableLongStackTrace: false }) },
    { provide: ErrorHandler, useValue: new ErrorHandler() },
    { provide: PendingTasks, useValue: { add: () => () => {} } }
  ], Injector.NULL);
  return { injector, router: injector.get(Router), location: injector.get(Location) as SpyLocation,
    profileChecks,
    setToken: (value: string | null) => { token = value; },
    setSession: (value: string | null, userRole = 'ADMIN') => { token = value; role = userRole; } };
}
