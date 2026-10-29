import { Component, DestroyRef, inject } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter, finalize } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { navigation, NavigationItem } from '../navigation';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './admin-layout.html',
  styleUrl: './admin-layout.css'
})
export class AdminLayout {
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);
  auth = inject(AuthService);
  loggingOut = false;
  logoutError = '';
  menu = navigation;
  menuOpen = false;
  expandedGroup: string | null = null;

  constructor() {
    this.expandCurrentGroup();
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.expandCurrentGroup();
      this.menuOpen = false;
    });
  }

  toggleGroup(id: string): void {
    this.expandedGroup = this.expandedGroup === id ? null : id;
  }

  isGroupActive(item: NavigationItem): boolean {
    return item.children?.some(child => this.router.isActive('/' + child.path, {
      paths: 'subset', queryParams: 'ignored', fragment: 'ignored', matrixParams: 'ignored'
    })) ?? false;
  }

  private expandCurrentGroup(): void {
    this.expandedGroup = this.menu.find(item => this.isGroupActive(item))?.id ?? null;
  }

  logout(): void {
    if (this.loggingOut) return;
    this.loggingOut = true;
    this.logoutError = '';
    this.auth.logout().pipe(finalize(() => this.loggingOut = false)).subscribe({
      next: () => void this.router.navigateByUrl('/login'),
      error: () => this.logoutError = 'Không thể đăng xuất. Vui lòng kiểm tra kết nối và thử lại.'
    });
  }
}
