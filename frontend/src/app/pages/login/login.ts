import { Component, DestroyRef, ElementRef, ViewChild, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService, ManagementAccessDeniedError } from '../../services/auth.service';
import { LanguageService } from '../../services/language.service';
import { ThemeService } from '../../services/theme.service';

@Component({
  selector: 'app-login',
  standalone: true,
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class LoginPage {
  @ViewChild('username') private usernameInput?: ElementRef<HTMLInputElement>;
  @ViewChild('hintClose') private hintClose?: ElementRef<HTMLButtonElement>;
  private auth = inject(AuthService);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);
  private hintTimer?: ReturnType<typeof setTimeout>;
  language = inject(LanguageService);
  theme = inject(ThemeService);
  showPassword = false;
  capsLock = false;
  showLoginHint = false;
  loading = false;
  error = '';
  notice = this.router.getCurrentNavigation()?.extras.state?.['passwordChanged']
    ? 'Đổi mật khẩu thành công. Vui lòng đăng nhập lại bằng mật khẩu mới.' : '';

  constructor() {
    this.destroyRef.onDestroy(() => clearTimeout(this.hintTimer));
  }

  checkCapsLock(event: KeyboardEvent): void {
    this.capsLock = event.getModifierState('CapsLock');
  }

  guideToLogin(): void {
    clearTimeout(this.hintTimer);
    this.showLoginHint = true;
    this.usernameInput?.nativeElement.focus();
    this.hintTimer = setTimeout(() => {
      if (document.activeElement !== this.hintClose?.nativeElement) this.showLoginHint = false;
    }, 6000);
  }

  dismissLoginHint(): void {
    clearTimeout(this.hintTimer);
    this.showLoginHint = false;
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
      next: () => void this.router.navigateByUrl('/overview', { info: { showLoginSuccess: true } }),
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
