import {HrmButton} from '../../shared/ui/button';
import {HrmIcon} from '../../shared/ui/icon';
import { Component, ElementRef, ViewChild, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService, ManagementAccessDeniedError } from '../../services/auth.service';
import { LanguageService } from '../../services/language.service';
import { ThemeService } from '../../services/theme.service';
import { navigation } from '../../navigation';

@Component({
  selector: 'app-login',
  standalone: true,
  imports:[HrmButton,HrmIcon],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class LoginPage {
  private readonly featuredPaths = new Set([
    'employees', 'contracts', 'schedules', 'attendance',
    'overtime', 'requests', 'payroll', 'reports'
  ]);
  readonly featuredLinks = navigation.flatMap(item =>
    item.children ?? (item.path ? [{ path: item.path, label: item.label }] : [])
  ).filter(link => this.featuredPaths.has(link.path));
  @ViewChild('username') private usernameInput?: ElementRef<HTMLInputElement>;
  selectedPath = '';
  selectedLabel = '';
  private auth = inject(AuthService);
  readonly sessionNotice = this.auth.sessionNotice;
  private router = inject(Router);
  language = inject(LanguageService);
  theme = inject(ThemeService);
  showPassword = false;
  showRecoveryPassword = false;
  showRecoveryConfirmation = false;
  showRecoveryHelp = false;
  recoveryStep: 'account' | 'code' | 'password' | 'complete' = 'account';
  recoveryUsername = '';
  recoveryCode = '';
  recoveryError = '';
  capsLock = false;
  loading = false;
  error = '';
  notice = this.router.getCurrentNavigation()?.extras.state?.['passwordChanged']
    ? 'Đổi mật khẩu thành công. Vui lòng đăng nhập lại bằng mật khẩu mới.'
    : this.router.getCurrentNavigation()?.extras.state?.['usernameChanged']
      ? 'Đã đổi tên đăng nhập. Vui lòng đăng nhập lại bằng tên mới.' : '';

  checkCapsLock(event: KeyboardEvent): void {
    this.capsLock = event.getModifierState('CapsLock');
  }

  dismissSessionNotice(): void {
    this.sessionNotice.set('');
  }

  selectDestination(path: string, label: string): void {
    this.selectedPath = path;
    this.selectedLabel = label;
    if (this.showRecoveryHelp) {
      this.closeRecovery();
      setTimeout(() => this.usernameInput?.nativeElement.focus());
    } else {
      this.usernameInput?.nativeElement.focus();
    }
  }

  openRecovery(): void {
    this.recoveryStep = 'account';
    this.recoveryUsername = '';
    this.recoveryCode = '';
    this.recoveryError = '';
    this.showRecoveryHelp = true;
  }

  closeRecovery(): void {
    this.showRecoveryHelp = false;
    this.recoveryStep = 'account';
    this.recoveryUsername = '';
    this.recoveryCode = '';
    this.recoveryError = '';
    this.showRecoveryPassword = false;
    this.showRecoveryConfirmation = false;
  }

  sendRecoveryCode(event: Event, username: string): void {
    event.preventDefault();
    if (this.loading) return;
    if (!username.trim()) {
      this.recoveryError = 'Vui lòng nhập tên đăng nhập.';
      return;
    }
    this.recoveryUsername = username.trim();
    this.recoveryError = '';
    this.loading = true;
    this.auth.requestRecovery(this.recoveryUsername).pipe(finalize(() => this.loading = false)).subscribe({
      next: () => this.recoveryStep = 'code',
      error: () => this.recoveryError = 'Không thể gửi yêu cầu lúc này. Vui lòng thử lại sau hoặc liên hệ quản trị viên.'
    });
  }

  verifyRecoveryCode(event: Event, code: string): void {
    event.preventDefault();
    if (this.loading) return;
    if (!/^[0-9]{8}$/.test(code.trim())) {
      this.recoveryError = 'Vui lòng nhập mã xác nhận gồm 8 chữ số.';
      return;
    }
    this.recoveryError = '';
    this.loading = true;
    this.auth.verifyRecovery(this.recoveryUsername, code.trim()).pipe(finalize(() => this.loading = false)).subscribe({
      next: () => { this.recoveryCode = code.trim(); this.recoveryStep = 'password'; },
      error: (error: unknown) => this.recoveryError = error instanceof HttpErrorResponse && error.status === 400
        ? 'Mã không hợp lệ, đã hết hạn hoặc đã vượt quá số lần thử. Vui lòng yêu cầu mã mới.'
        : 'Không thể kiểm tra mã lúc này. Vui lòng thử lại.'
    });
  }

  resetRecoveryPassword(event: Event, password: string, confirmation: string): void {
    event.preventDefault();
    if (this.loading) return;
    if (password.length < 8 || password.length > 72 || new TextEncoder().encode(password).length > 72) {
      this.recoveryError = 'Mật khẩu phải từ 8 đến 72 ký tự và không quá 72 byte UTF-8.';
      return;
    }
    if (password !== confirmation) {
      this.recoveryError = 'Mật khẩu xác nhận không khớp.';
      return;
    }
    this.recoveryError = '';
    this.loading = true;
    this.auth.resetForgottenPassword(this.recoveryUsername, this.recoveryCode, password, confirmation)
      .pipe(finalize(() => this.loading = false)).subscribe({
        next: () => { this.recoveryCode = ''; this.recoveryStep = 'complete'; },
        error: () => this.recoveryError = 'Không thể đổi mật khẩu. Mã có thể đã hết hạn; hãy yêu cầu mã mới và thử lại.'
      });
  }

  submit(event: Event, username: string, password: string) {
    event.preventDefault();
    if (this.loading) return;
    username = username.trim();
    if (!username || !password) {
      this.error = 'Vui lòng nhập tên đăng nhập và mật khẩu.';
      return;
    }

    this.loading = true;
    this.error = '';
    this.auth.login(username, password).pipe(finalize(() => this.loading = false)).subscribe({
      next: () => void this.router.navigateByUrl((['ADMIN','HR'].includes(this.auth.profile()?.role||'') ? '/' + (this.selectedPath || 'overview') : '/requests/head'), { replaceUrl: true, info: { showLoginSuccess: true } }),
      error: (error: unknown) => {
        if (error instanceof ManagementAccessDeniedError) {
          this.error = error.message;
        } else if (error instanceof HttpErrorResponse && error.status === 401) {
          this.error = 'Tên đăng nhập hoặc mật khẩu không đúng.';
        } else if (error instanceof HttpErrorResponse && error.status === 400) {
          this.error = 'Vui lòng kiểm tra lại thông tin đăng nhập.';
        } else {
          this.error = 'Không thể đăng nhập lúc này. Vui lòng kiểm tra kết nối và thử lại.';
        }
      }
    });
  }
}
