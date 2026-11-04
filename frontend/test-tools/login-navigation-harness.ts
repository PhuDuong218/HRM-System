import '@angular/compiler';
import { Injector, createEnvironmentInjector, runInInjectionContext } from '@angular/core';
import { Subject } from 'rxjs';
import { Router } from '@angular/router';
import { AuthService } from '../src/app/services/auth.service';
import { LanguageService } from '../src/app/services/language.service';
import { ThemeService } from '../src/app/services/theme.service';
import { LoginPage } from '../src/app/pages/login/login';

export function setup(role = 'HR') {
  const login = new Subject<unknown>();
  const visits: string[] = [];
  const injector = createEnvironmentInjector([
    { provide: AuthService, useValue: { login: () => login.asObservable(), profile: () => ({role, departmentHead: role === 'EMPLOYEE'}) } },
    { provide: Router, useValue: { getCurrentNavigation: () => null, navigateByUrl: (url: string) => { visits.push(url); return Promise.resolve(true); } } },
    { provide: LanguageService, useValue: {} },
    { provide: ThemeService, useValue: {} }
  ], Injector.NULL);
  return { injector, login, visits, page: runInInjectionContext(injector, () => new LoginPage()) };
}
