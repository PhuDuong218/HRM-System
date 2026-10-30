import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, ViewChild, inject } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter, finalize } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { navigation, NavigationItem } from '../navigation';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [DatePipe, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './admin-layout.html',
  styleUrl: './admin-layout.css'
})
export class AdminLayout {
  @ViewChild('profileDialog') private profileDialog?: ElementRef<HTMLDialogElement>;
  @ViewChild('passwordDialog') private passwordDialog?: ElementRef<HTMLDialogElement>;
  @ViewChild('passwordForm') private passwordForm?: ElementRef<HTMLFormElement>;
  @ViewChild('logoutDialog') private logoutDialog?: ElementRef<HTMLDialogElement>;
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);
  auth = inject(AuthService);
  loggingOut = false;
  logoutError = '';
  profileLoading = false;
  profileError = '';
  passwordLoading = false;
  passwordError = '';
  showCurrentPassword = false;
  showNewPassword = false;
  showConfirmPassword = false;
  menu = navigation;
  menuOpen = false;
  sidebarCollapsed = false;
  expandedGroups = new Set<string>();

  constructor() {
    this.refreshProfile();
    this.expandCurrentGroup();
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.expandCurrentGroup();
      this.menuOpen = false;
    });
  }

  openProfile(): void {
    this.profileDialog?.nativeElement.showModal();
    this.refreshProfile();
  }

  closeProfile(): void {
    this.profileDialog?.nativeElement.close();
  }

  openPasswordDialog(): void {
    this.profileDialog?.nativeElement.close();
    this.passwordError = '';
    this.passwordDialog?.nativeElement.showModal();
  }

  backToProfile(): void {
    if (this.passwordLoading) return;
    this.passwordDialog?.nativeElement.close();
    this.profileDialog?.nativeElement.showModal();
  }

  resetPasswordForm(): void {
    this.passwordForm?.nativeElement.reset();
    this.passwordError = '';
    this.showCurrentPassword = this.showNewPassword = this.showConfirmPassword = false;
  }

  preventPasswordCloseWhileSaving(event: Event): void {
    if (this.passwordLoading) event.preventDefault();
  }

  changePassword(event: Event, currentPassword: string, newPassword: string, confirmPassword: string): void {
    event.preventDefault();
    if (this.passwordLoading) return;
    this.passwordError = '';
    if (!currentPassword.trim() || !newPassword.trim() || !confirmPassword.trim()) {
      this.passwordError = 'Vui lòng nhập đầy đủ mật khẩu.';
      return;
    }
    if (newPassword.length < 8 || newPassword.length > 72) {
      this.passwordError = 'Mật khẩu mới phải có từ 8 đến 72 ký tự.';
      return;
    }
    if (newPassword !== confirmPassword) {
      this.passwordError = 'Mật khẩu xác nhận không khớp.';
      return;
    }
    if (newPassword === currentPassword) {
      this.passwordError = 'Mật khẩu mới phải khác mật khẩu hiện tại.';
      return;
    }
    this.passwordLoading = true;
    this.auth.changePassword(currentPassword, newPassword, confirmPassword).pipe(
      takeUntilDestroyed(this.destroyRef), finalize(() => this.passwordLoading = false)
    ).subscribe({
      next: () => {
        this.passwordDialog?.nativeElement.close();
        void this.router.navigateByUrl('/login', { state: { passwordChanged: true } });
      },
      error: (error: unknown) => {
        this.passwordError = error instanceof HttpErrorResponse && error.status === 400 &&
          typeof error.error?.loi_chi_tiet === 'string'
          ? error.error.loi_chi_tiet
          : 'Không thể đổi mật khẩu. Vui lòng kiểm tra kết nối và thử lại.';
      }
    });
  }

  refreshProfile(): void {
    if (this.profileLoading) return;
    this.profileLoading = true;
    this.profileError = '';
    this.auth.loadCurrentUser().pipe(
      takeUntilDestroyed(this.destroyRef), finalize(() => this.profileLoading = false)
    ).subscribe({ error: () => this.profileError = 'Không tải được hồ sơ. Vui lòng thử lại.' });
  }

  toggleGroup(id: string): void {
    if (this.expandedGroups.has(id)) this.expandedGroups.delete(id); else this.expandedGroups.add(id);
  }

  isGroupActive(item: NavigationItem): boolean {
    return item.children?.some(child => this.router.isActive('/' + child.path, {
      paths: 'subset', queryParams: 'ignored', fragment: 'ignored', matrixParams: 'ignored'
    })) ?? false;
  }

  private expandCurrentGroup(): void {
    const current = this.menu.find(item => this.isGroupActive(item));
    if (current) this.expandedGroups.add(current.id);
  }

  confirmLogout(): void {
    if (this.loggingOut) return;
    this.logoutError = '';
    this.logoutDialog?.nativeElement.showModal();
  }

  cancelLogout(): void {
    if (!this.loggingOut) this.logoutDialog?.nativeElement.close();
  }

  preventLogoutCloseWhileSaving(event: Event): void {
    if (this.loggingOut) event.preventDefault();
  }

  logout(): void {
    if (this.loggingOut) return;
    this.loggingOut = true;
    this.logoutError = '';
    this.auth.logout().pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loggingOut = false)).subscribe({
      next: () => {
        this.logoutDialog?.nativeElement.close();
        void this.router.navigateByUrl('/login');
      },
      error: () => this.logoutError = 'Không thể đăng xuất. Vui lòng kiểm tra kết nối và thử lại.'
    });
  }
}
