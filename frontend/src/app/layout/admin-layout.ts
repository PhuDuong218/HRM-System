import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { navigation } from '../navigation';
@Component({ selector: 'app-admin-layout', standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet], templateUrl: './admin-layout.html', styleUrl: './admin-layout.css' })
export class AdminLayout {
  auth = inject(AuthService);
  private router = inject(Router);
  menu = navigation;
  menuOpen = false;
  loggingOut = false;
  logoutError = '';

  logout() {
    if (this.loggingOut) return;
    this.loggingOut = true;
    this.logoutError = '';
    this.auth.logout().pipe(finalize(() => this.loggingOut = false)).subscribe({
      next: () => void this.router.navigateByUrl('/login'),
      error: () => this.logoutError = 'Không thể đăng xuất. Vui lòng kiểm tra kết nối và thử lại.'
    });
  }
}
