import {TranslatePipe} from '../shared/translate.pipe';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, HostListener, DestroyRef, ElementRef, ViewChild, inject } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter, finalize } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { navigation, NavigationItem } from '../navigation';
import { LanguageService } from '../services/language.service';
import { ThemeService } from '../services/theme.service';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [DatePipe, RouterLink, RouterLinkActive, RouterOutlet,TranslatePipe],
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
  language = inject(LanguageService);
  private theme = inject(ThemeService);
  showLoginSuccess = (this.router.getCurrentNavigation()?.extras.info as { showLoginSuccess?: boolean } | undefined)?.showLoginSuccess === true;
  loggingOut = false;
  logoutError = '';
  profileLoading = false;
  profileError = '';
  passwordLoading = false;
  passwordError = '';
  showCurrentPassword = false;
  showNewPassword = false;
  showConfirmPassword = false;
  get darkMode(): boolean { return this.theme.darkMode(); }
  toggleTheme(): void { this.theme.toggle(); }

  get menu():NavigationItem[]{
    const role=this.auth.profile()?.role||'';
    return navigation.filter(item=>!item.roles||item.roles.includes(role)).map(item=>item.children?{...item,children:item.children.filter(child=>!child.roles||child.roles.includes(role))}:item);
  }
  @ViewChild('mobilePanel') private mobilePanel?: ElementRef<HTMLElement>;
  @ViewChild('menuTrigger') private menuTrigger?: ElementRef<HTMLButtonElement>;
  menuOpen = false;
  toggleMobileMenu(): void {
    this.menuOpen = !this.menuOpen;
    if(this.menuOpen) requestAnimationFrame(()=>this.mobilePanel?.nativeElement.querySelector<HTMLButtonElement>('.mobile-close')?.focus());
  }
  closeMobileMenu(): void { this.menuOpen=false;this.menuTrigger?.nativeElement.focus(); }
  @HostListener('document:keydown.escape') closeMenuOnEscape(): void { this.closeFlyout(); if(this.menuOpen)this.closeMobileMenu(); }
  menuKeydown(event: KeyboardEvent): void {
    if(!this.menuOpen||event.key!=='Tab'||!window.matchMedia('(max-width:800px)').matches)return;
    const controls=Array.from(this.mobilePanel?.nativeElement.querySelectorAll<HTMLElement>('a[href],button:not(:disabled)')||[]).filter(x=>x.getClientRects().length>0);
    const first=controls[0],last=controls[controls.length-1];
    if(event.shiftKey&&document.activeElement===first){event.preventDefault();last?.focus();}
    else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first?.focus();}
  }
  sidebarCollapsed = false;
  flyout: NavigationItem | null = null;
  flyoutTop = 12;
  readonly menuIcons: Record<string, string> = {
    overview: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
    personnel: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M22 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75',
    time: 'M12 8v4l3 2 M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0',
    pay: 'M3 6h18v14H3z M3 6l14-3v3 M16 11h5v5h-5z M17 13h1',
    organization: 'M9 3h6v5H9z M3 16h6v5H3z M15 16h6v5h-6z M12 8v4 M6 16v-4h12v4',
    reports: 'M4 3h12l4 4v14H4z M15 3v5h5 M8 17v-3 M12 17v-6 M16 17v-4',
    system: 'M12 3v3 M12 18v3 M3 12h3 M18 12h3 M5.6 5.6l2.1 2.1 M16.3 16.3l2.1 2.1 M5.6 18.4l2.1-2.1 M16.3 7.7l2.1-2.1 M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0'
  };

  toggleSidebar(): void {
    this.sidebarCollapsed = !this.sidebarCollapsed;
    this.closeFlyout();
  }

  previewGroup(item: NavigationItem, event?: Event): void {
    if (!this.sidebarCollapsed || !window.matchMedia('(min-width:801px)').matches) return;
    const rect = (event?.currentTarget as HTMLElement | null)?.getBoundingClientRect();
    this.flyoutTop = Math.max(12, Math.min(rect?.top ?? 100, window.innerHeight - 360));
    this.flyout = item;
  }

  closeFlyout(): void { this.flyout = null; }

  railFocusOut(event: FocusEvent): void {
    if (!(event.currentTarget as HTMLElement).contains(event.relatedTarget as Node | null)) this.closeFlyout();
  }

  expandedGroups = new Set<string>();

  constructor() {
    this.refreshProfile();
    this.expandCurrentGroup();
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.expandCurrentGroup();
      this.closeFlyout();
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

  toggleGroup(id: string, event?: Event): void {
    if (this.sidebarCollapsed && window.matchMedia('(min-width:801px)').matches) {
      const group = this.menu.find(item => item.id === id);
      if (group) this.previewGroup(group, event);
      return;
    }
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
