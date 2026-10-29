import { Component, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class LoginPage {
  private auth = inject(AuthService);
  private router = inject(Router);
  showPassword = false;
  loading = false;
  error = '';

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
      next: () => void this.router.navigateByUrl('/overview'),
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 401) {
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
