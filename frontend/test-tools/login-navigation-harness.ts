import '@angular/compiler';
import { Injector, createEnvironmentInjector, runInInjectionContext } from '@angular/core';
import { Subject } from 'rxjs';
import { Router } from '@angular/router';
import { AuthService } from '../src/app/services/auth.service';
import { LanguageService } from '../src/app/services/language.service';
import { ThemeService } from '../src/app/services/theme.service';
import { LoginPage } from '../src/app/pages/login/login';
import { guestGuard } from '../src/app/services/auth.guard';

export function setup(role = 'HR') {
  const login = new Subject<unknown>();
  const visits: string[] = [];
  const navigationOptions: unknown[] = [];
  let token: string | null = null;
  const injector = createEnvironmentInjector([
    { provide: AuthService, useValue: { login: () => login.asObservable(), token: () => token, profile: () => ({role, departmentHead: role === 'EMPLOYEE'}) } },
    { provide: Router, useValue: { getCurrentNavigation: () => null, parseUrl: (url: string) => ({ url }), navigateByUrl: (url: string, options: unknown) => { visits.push(url); navigationOptions.push(options); return Promise.resolve(true); } } },
    { provide: LanguageService, useValue: {} },
    { provide: ThemeService, useValue: {} }
  ], Injector.NULL);
  return { injector, login, visits, navigationOptions, setToken: (value: string | null) => { token = value; },
    guardLogin: () => runInInjectionContext(injector, () => guestGuard({} as never, {} as never)),
    page: runInInjectionContext(injector, () => new LoginPage()) };
}
