import {TranslatePipe} from '../../shared/translate.pipe';
import { Component, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { HealthService } from '../../services/health.service';
@Component({imports:[TranslatePipe], selector: 'app-connection', standalone: true, templateUrl: './connection.html' })
export class ConnectionPage {
  private healthService = inject(HealthService);
  loading = false;
  response = '';
  error = '';
  success = false;
  checkConnection() {
    if (this.loading) return;
    this.loading = true;
    this.response = '';
    this.error = '';
    this.success = false;
    this.healthService.check().subscribe({
      next: text => {
        this.response = text;
        this.success = true;
        this.loading = false;
      },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 401) {
          this.error = 'Backend yêu cầu xác thực (401). Cần đối chiếu chính sách đăng nhập.';
        } else if (error instanceof HttpErrorResponse && error.status === 403) {
          this.error = 'Backend từ chối truy cập (403). Cần kiểm tra quyền tại backend.';
        } else {
          this.error = 'Không nhận được phản hồi thành công. Kiểm tra backend, cổng trong proxy.conf.json và tab Network của trình duyệt.';
        }
        this.loading = false;
      }
    });
  }
}
