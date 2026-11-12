import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError } from 'rxjs';
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

export interface PayslipDirectoryRow {
  payslipId: number;
  payslipNumber: string;
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  periodCode: string;
  periodStart: string;
  periodEnd: string;
  versionNo: number;
}

export interface PayslipDirectory {
  canSelectEmployee: boolean;
  rows: PayslipDirectoryRow[];
}

@Injectable({
  providedIn: 'root'
})
export class PayrollService {
  private http = inject(HttpClient);
  private periodUrl = `${API_BASE_URL}/payroll-periods`;
  private batchUrl = `${API_BASE_URL}/payroll-batches`;

  getPeriods(): Observable<PayrollPeriod[]> {
    return this.http.get<PayrollPeriod[]>(this.periodUrl);
  }

  getBatchesByPeriod(periodId: number): Observable<PayrollBatch[]> {
    return this.http
      .get<PayrollBatch[]>(`${this.batchUrl}/periods/${periodId}/batches`)
      .pipe(catchError(() => this.http.get<PayrollBatch[]>(`${this.batchUrl}/period/${periodId}`)));
  }

  // 1. Tạo đợt lương nháp (Draft)
  createDraftBatch(periodId: number): Observable<PayrollBatch> {
    return this.http.post<PayrollBatch>(`${this.batchUrl}/draft`, { periodId });
  }

  // 2. Chạy tính lương tự động
  calculateBatch(batchId: number): Observable<string> {
    return this.http.post(`${this.batchUrl}/${batchId}/calculate`, {}, { responseType: 'text' });
  }

  calculatePayroll(batchId: number): Observable<string> {
    return this.calculateBatch(batchId);
  }

  // 3. Chi tiết bảng lương theo đợt
  getBatchDetails(batchId: number): Observable<PayrollDetailView[]> {
    return this.http.get<PayrollDetailView[]>(`${this.batchUrl}/${batchId}/details`);
  }

  // 4. Khóa/Chốt bảng lương
  lockBatch(batchId: number): Observable<PayrollBatch> {
    return this.http.post<PayrollBatch>(`${this.batchUrl}/${batchId}/lock`, {});
  }

  // 5. Công bố bảng lương
  publishBatch(batchId: number): Observable<PayrollBatch> {
    return this.http.post<PayrollBatch>(`${this.batchUrl}/${batchId}/publish`, {});
  }

  // 6. Tạo đợt điều chỉnh (V2+)
  createAdjustmentBatch(batchId: number, adjustmentReason: string): Observable<PayrollBatch> {
    return this.http.post<PayrollBatch>(`${this.batchUrl}/${batchId}/adjust`, { adjustmentReason });
  }

  createAdjustment(batchId: number, adjustmentReason: string): Observable<PayrollBatch> {
    return this.createAdjustmentBatch(batchId, adjustmentReason);
  }

  // 7. Minh chứng ngày công chi tiết của nhân viên (Check-in, Check-out, OT, Đi trễ)
  getEmployeeWorkDetails(batchId: number, employeeId: number): Observable<EmployeePayrollWorkDetail> {
    return this.http.get<EmployeePayrollWorkDetail>(`${this.batchUrl}/${batchId}/employees/${employeeId}/work-details`);
  }

  // 8. Danh sách phụ cấp của tháng & phòng ban
  getPeriodAllowances(periodId: number, departmentId?: number | null): Observable<PeriodAllowanceOverview> {
    let params = new HttpParams();
    if (departmentId) {
      params = params.set('departmentId', String(departmentId));
    }
    return this.http.get<PeriodAllowanceOverview>(`${this.batchUrl}/periods/${periodId}/allowances`, { params });
  }

  // 9. Bộ lọc & lịch sử lương nhân viên qua các kỳ
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

  // 10. Chi phí lương theo phòng ban
  getDepartmentCostSummary(periodId: number): Observable<DepartmentPayrollCost[]> {
    return this.http.get<DepartmentPayrollCost[]>(`${this.batchUrl}/periods/${periodId}/department-costs`);
  }

  // 11. Kiểm tra điều kiện tính/chốt lương chi tiết tới từng nhân viên
  checkBatchReadiness(batchId: number): Observable<PayrollReadinessReport> {
    return this.http.get<PayrollReadinessReport>(`${this.batchUrl}/${batchId}/readiness`);
  }

  // 12. Danh sách phòng ban
  getDepartments(): Observable<Array<{ departmentId: number; departmentCode: string; departmentName: string }>> {
    return this.http.get<Array<{ departmentId: number; departmentCode: string; departmentName: string }>>(`${API_BASE_URL}/departments`);
  }

  // 13. Danh mục phiếu lương & cá nhân
  getPayslipDirectory(): Observable<PayslipDirectory> {
    return this.http.get<PayslipDirectory>(`${API_BASE_URL}/payslips/directory`);
  }

  getMyPayslips(): Observable<any[]> {
    return this.http.get<any[]>(`${API_BASE_URL}/me/payslips`);
  }

  getPayslipHtml(payslipId: number): Observable<string> {
    return this.http.get(`${API_BASE_URL}/payslips/${payslipId}/html`, { responseType: 'text' });
  }

  getPayslipHtmlByPayrollDetail(detailId: number): Observable<string> {
    return this.http.get(`${API_BASE_URL}/payslips/payroll-detail/${detailId}/html`, { responseType: 'text' });
  }

  getPreviewPayslipHtml(employeeId: number): Observable<string> {
    return this.http.get(`${API_BASE_URL}/payslips/preview/employee/${employeeId}/html`, { responseType: 'text' });
  }
}