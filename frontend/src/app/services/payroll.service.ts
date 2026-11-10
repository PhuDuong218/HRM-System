import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import {
  PayrollPeriod,
  PayrollBatch,
  PayrollDetailView,
  AdjustmentRequest
} from '../models/payroll.model';

@Injectable({
  providedIn: 'root'
})
export class PayrollService {
  private http = inject(HttpClient);

  // 1. Quản lý kỳ lương
  getPeriods(): Observable<PayrollPeriod[]> {
    return this.http.get<PayrollPeriod[]>(`${API_BASE_URL}/payroll-periods`);
  }

  // 2. Tra cứu đợt lương mới nhất của một kỳ
  getCurrentBatchByPeriod(periodId: number): Observable<PayrollBatch> {
    return this.http.get<PayrollBatch>(`${API_BASE_URL}/payroll-batches/period/${periodId}/current`);
  }

  // 3. Tạo đợt lương nháp (Draft)
  createDraftBatch(periodId: number): Observable<PayrollBatch> {
    return this.http.post<PayrollBatch>(`${API_BASE_URL}/payroll-batches/draft`, { periodId });
  }

  // 4. Chạy động cơ tính lương tự động
  calculatePayroll(batchId: number): Observable<string> {
    return this.http.post<string>(`${API_BASE_URL}/payroll-batches/${batchId}/calculate`, {}, { responseType: 'text' as 'json' });
  }

  // 5. Lấy bảng tổng hợp đối soát số liệu nhân viên
  getBatchDetails(batchId: number): Observable<PayrollDetailView[]> {
    return this.http.get<PayrollDetailView[]>(`${API_BASE_URL}/payroll-batches/${batchId}/details`);
  }

  // 6. Chốt bảng lương (Khóa bất biến)
  lockBatch(batchId: number): Observable<PayrollBatch> {
    return this.http.post<PayrollBatch>(`${API_BASE_URL}/payroll-batches/${batchId}/lock`, {});
  }

  // 7. Công bố bảng lương và phát hành phiếu
  publishBatch(batchId: number): Observable<PayrollBatch> {
    return this.http.post<PayrollBatch>(`${API_BASE_URL}/payroll-batches/${batchId}/publish`, {});
  }

  // 8. Tạo phiên bản điều chỉnh (Version 2+)
  createAdjustment(batchId: number, reason: string): Observable<PayrollBatch> {
    const body: AdjustmentRequest = { adjustmentReason: reason };
    return this.http.post<PayrollBatch>(`${API_BASE_URL}/payroll-batches/${batchId}/adjust`, body);
  }

  // 9. Danh sách phiếu lương cá nhân
  getMyPayslips(): Observable<any[]> {
    // The server resolves the employee from the authenticated session.
    return this.http.get<any[]>(`${API_BASE_URL}/payslips/my-payslips`);
  }

  // 10. Xem chi tiết 1 phiếu lương
  getPayslipDetail(payslipId: number): Observable<any> {
    return this.http.get<any>(`${API_BASE_URL}/payslips/${payslipId}`);
  }

  // 11. Tải HTML in ấn phiếu lương có bảo vệ bằng JWT token (Không hardcode cổng)
  getPayslipHtml(payslipId: number): Observable<string> {
    return this.http.get(`${API_BASE_URL}/payslips/${payslipId}/html`, { responseType: 'text' });
  }

  // 12. Tải HTML in ấn theo ID nhân viên
  getPreviewPayslipHtml(employeeId: number): Observable<string> {
    return this.http.get(`${API_BASE_URL}/payslips/preview/employee/${employeeId}/html`, { responseType: 'text' });
  }
}
