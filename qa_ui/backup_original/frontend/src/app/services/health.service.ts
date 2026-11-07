import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { timeout } from 'rxjs';
import { API_BASE_URL } from './api.config';
@Injectable({ providedIn: 'root' })
export class HealthService {
  private http = inject(HttpClient);
  check() {
    // Đọc dạng text để không giả định backend trả JSON hay chuỗi OK.
    return this.http.get(`${API_BASE_URL}/health`, { responseType: 'text' }).pipe(timeout(10000));
  }
}
