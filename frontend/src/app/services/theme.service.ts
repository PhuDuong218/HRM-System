import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly darkMode = signal(false);

  constructor() {
    try { this.darkMode.set(localStorage.getItem('hrm.theme') === 'dark'); } catch { /* Storage is optional. */ }
    this.apply();
  }

  toggle(): void {
    this.darkMode.update(value => !value);
    this.apply();
    try { localStorage.setItem('hrm.theme', this.darkMode() ? 'dark' : 'light'); } catch { /* Keep the selection in memory. */ }
  }

  private apply(): void {
    document.documentElement.setAttribute('data-bs-theme', this.darkMode() ? 'dark' : 'light');
  }
}
