import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import {
  PayrollPeriod,
  PayrollBatch,
  PayrollDetailView,
  EmployeePayrollWorkDetail,
  PeriodAllowanceOverview,
  EmployeePayrollHistoryItem,
  DepartmentPayrollCost,
  PayrollReadinessReport
} from '../models/payroll.model';

@Injectable({ providedIn: 'root' })
export class PayrollService {
  private http = inject(HttpClient);
  private periodUrl = `${API_BASE_URL}/payroll-periods`;
  private batchUrl = `${API_BASE_URL}/payroll-batches`;

  getPeriods(): Observable<PayrollPeriod[]> {
    return this.http.get<PayrollPeriod[]>(this.periodUrl);
  }

  getBatchesByPeriod(periodId: number): Observable<PayrollBatch[]> {
    return this.http.get<PayrollBatch[]>(`${this.batchUrl}/periods/${periodId}/batches`);
  }

  createDraftBatch(periodId: number): Observable<PayrollBatch> {
    return this.http.post<PayrollBatch>(`${this.batchUrl}/draft`, { periodId });
  }

  calculateBatch(batchId: number): Observable<string> {
    return this.http.post(`${this.batchUrl}/${batchId}/calculate`, {}, { responseType: 'text' });
  }

  getBatchDetails(batchId: number): Observable<PayrollDetailView[]> {
    return this.http.get<PayrollDetailView[]>(`${this.batchUrl}/${batchId}/details`);
  }

  lockBatch(batchId: number): Observable<PayrollBatch> {
    return this.http.post<PayrollBatch>(`${this.batchUrl}/${batchId}/lock`, {});
  }

  publishBatch(batchId: number): Observable<PayrollBatch> {
    return this.http.post<PayrollBatch>(`${this.batchUrl}/${batchId}/publish`, {});
  }

  createAdjustmentBatch(batchId: number, adjustmentReason: string): Observable<PayrollBatch> {
    return this.http.post<PayrollBatch>(`${this.batchUrl}/${batchId}/adjust`, { adjustmentReason });
  }

  // 1. Minh chứng ngày công chi tiết của nhân viên
  getEmployeeWorkDetails(batchId: number, employeeId: number): Observable<EmployeePayrollWorkDetail> {
    return this.http.get<EmployeePayrollWorkDetail>(`${this.batchUrl}/${batchId}/employees/${employeeId}/work-details`);
  }

  // 2. Danh sách phụ cấp của tháng & phòng ban
  getPeriodAllowances(periodId: number, departmentId?: number | null): Observable<PeriodAllowanceOverview> {
    let params = new HttpParams();
    if (departmentId) {
      params = params.set('departmentId', String(departmentId));
    }
    return this.http.get<PeriodAllowanceOverview>(`${this.batchUrl}/periods/${periodId}/allowances`, { params });
  }

  // 4A. Bộ lọc & lịch sử lương nhân viên
  getEmployeePayrollHistory(filters: {
    employeeId?: number | null;
    departmentId?: number | null;
    keyword?: string;
    status?: string;
  }): Observable<EmployeePayrollHistoryItem[]> {
    let params = new HttpParams();
    if (filters.employeeId) params = params.set('employeeId', String(filters.employeeId));
    if (filters.departmentId) params = params.set('departmentId', String(filters.departmentId));
    if (filters.keyword?.trim()) params = params.set('keyword', filters.keyword.trim());
    if (filters.status) params = params.set('status', filters.status);
    return this.http.get<EmployeePayrollHistoryItem[]>(`${this.batchUrl}/history/employees`, { params });
  }

  // 4B. Chi phí lương theo phòng ban
  getDepartmentCostSummary(periodId: number): Observable<DepartmentPayrollCost[]> {
    return this.http.get<DepartmentPayrollCost[]>(`${this.batchUrl}/periods/${periodId}/department-costs`);
  }

  // 5. Kiểm tra điều kiện tính/chốt lương chi tiết tới từng nhân viên
  checkBatchReadiness(batchId: number): Observable<PayrollReadinessReport> {
    return this.http.get<PayrollReadinessReport>(`${this.batchUrl}/${batchId}/readiness`);
  }

  getDepartments(): Observable<Array<{ departmentId: number; departmentCode: string; departmentName: string }>> {
    return this.http.get<Array<{ departmentId: number; departmentCode: string; departmentName: string }>>(`${API_BASE_URL}/departments`);
  }

  // Giữ lại 2 phương thức cho trang Phiếu lương cá nhân (PayslipsPage)
  getMyPayslips(): Observable<any[]> {
    return this.http.get<any[]>(`${API_BASE_URL}/me/payslips`);
  }

  getPayslipHtml(payslipId: number): Observable<string> {
    return this.http.get(`${API_BASE_URL}/payslips/${payslipId}/html`, { responseType: 'text' });
  }
}