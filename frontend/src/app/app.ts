import { Component, DestroyRef, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { SessionExpiredDialog } from './layout/session-expired-dialog';
import { AuthService } from './services/auth.service';
import {Confirmation} from './shared/ui/confirmation';
import {ActionDialog} from './shared/ui/action-dialog';

@Component({ selector: 'app-root', standalone: true, imports: [ActionDialog,Confirmation,RouterOutlet, SessionExpiredDialog], templateUrl: './app.html' })
export class App {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly currentUrl = signal(this.router.url);

  constructor() {
    this.router.events.pipe(filter(event => event instanceof NavigationEnd), takeUntilDestroyed(this.destroyRef))
      .subscribe(event => this.currentUrl.set((event as NavigationEnd).urlAfterRedirects));
  }
}
