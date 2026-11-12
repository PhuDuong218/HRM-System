import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { catchError, from, map, mergeMap, throwError, timeout } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { parsePayrollReport } from './report-format';

export type ReportType = 'employees' | 'contracts' | 'work' | 'payroll';
export interface ReportFilters {
  fromDate: string;
  toDate: string;
  reportDate: string;
  expiringWithinDays: number;
  departmentId: string;
}
export interface GroupRow { groupId: number | null; groupCode: string | null; groupName: string | null; employeeCount: number; }
export interface EmployeeReport { byDepartment: GroupRow[]; byPosition: GroupRow[]; byStatus: GroupRow[]; byQualification: GroupRow[]; }
export interface ContractReport { fromDate: string; toDate: string; reportDate: string; departmentId: number | null; expiringWithinDays: number; contractCount: number; effectiveCount: number; expiringCount: number; }
export interface WorkReport { fromDate: string; toDate: string; confirmedWorkDays: number; confirmedAbsentDays: number; approvedLeaveRequests: number; approvedLeaveHours: number; approvedOvertimeRequests: number; approvedOvertimeHours: number; }
export interface PayrollAmounts { grossPayroll: string; allowances: string; bonuses: string; deductions: string; netPayroll: string; }
export interface PayrollBatch { periodId: number; periodCode: string; periodStart: string; periodEnd: string; batchId: number; versionNo: number; employeeCount: number; totals: PayrollAmounts; }
export interface PayrollReport { fromDate: string; toDate: string; batchCount: number; payrollRecordCount: number; totals: PayrollAmounts; batches: PayrollBatch[]; }
export type Report = EmployeeReport | ContractReport | WorkReport | PayrollReport;
export interface HrOverview {
  employees: EmployeeReport;
  contracts: ContractReport;
  work: WorkReport;
}
export interface OverviewMonth extends EmployeeMovement { overtime: number; late: number; }
export interface PendingItem { count: number | null; state: 'READY' | 'DENIED' | 'ERROR'; }
export interface PendingOverview { updatedAt: string; attendance: PendingItem; leave: PendingItem; overtimeApproval: PendingItem; overtimeActual: PendingItem; }
export interface ReportDepartment { id: number; code: string; name: string; }
export interface EmployeeMovement { month: string; hired: number; terminated: number; }
export interface ContractDetail { contractId: number; employeeCode: string; employeeName: string; startDate: string; endDate: string; status: string; }
export interface ReportPage<T> { items: T[]; page: number; size: number; totalElements: number; totalPages: number; }
export type EmployeeGroupBy = 'department' | 'position' | 'status' | 'qualification';
export interface EmployeeReportDetail { employeeId: number; employeeCode: string; employeeName: string; departmentName: string | null; positionName: string | null; employmentStatus: string; hireDate: string; }
export interface PayrollEmployee { payrollDetailId: number; employeeId: number; employeeCode: string; employeeName: string; totals: PayrollAmounts; }
export interface PayrollLine { lineId: number; componentCode: string; componentName: string; direction: 'ADD' | 'DEDUCT'; amount: string; explanation: string | null; }

@Injectable({ providedIn: 'root' })
export class ReportService {
  private http = inject(HttpClient);

  getHrOverview() {
    return this.http.get<HrOverview>(`${API_BASE_URL}/overview/hr`).pipe(timeout(15000));
  }
  getAttendanceStatus(date?: string) {
    return this.http.get<{workDate: string; present: number; absent: number; leave: number; missing: number}>(
      `${API_BASE_URL}/overview/attendance-status`, { params: date ? { date } : {} }
    ).pipe(timeout(15000));
  }

  getOverviewTrends(date?: string) {
    return this.http.get<OverviewMonth[]>(`${API_BASE_URL}/overview/trends`, { params: date ? { date } : {} }).pipe(timeout(15000));
  }

  getPendingOverview() {
    return this.http.get<PendingOverview>(`${API_BASE_URL}/overview/pending`).pipe(timeout(15000));
  }

  private params(type: ReportType, filters: ReportFilters): HttpParams {
    let params = new HttpParams();
    if (filters.departmentId) params = params.set('departmentId', filters.departmentId);
    if (type === 'employees') return params;
    params = params.set('fromDate', filters.fromDate).set('toDate', filters.toDate);
    if (type === 'contracts') {
      params = params.set('expiringWithinDays', filters.expiringWithinDays);
      if (filters.reportDate) params = params.set('reportDate', filters.reportDate);
    }
    return params;
  }

  private reportUrl(type: ReportType, filters: ReportFilters): string {
    return `${API_BASE_URL}/reports/${filters.departmentId && type !== 'contracts' ? 'monitor/' : ''}${type}`;
  }

  departments() {
    return this.http.get<ReportDepartment[]>(`${API_BASE_URL}/reports/monitor/departments`).pipe(timeout(15000));
  }

  movements(filters: ReportFilters) {
    let params = new HttpParams().set('fromDate', filters.fromDate).set('toDate', filters.toDate);
    if (filters.departmentId) params = params.set('departmentId', filters.departmentId);
    return this.http.get<EmployeeMovement[]>(`${API_BASE_URL}/reports/monitor/movements`, { params }).pipe(timeout(15000));
  }

  employeeDetails(filters: ReportFilters, groupBy: EmployeeGroupBy, group: GroupRow, page: number) {
    let params = this.params('employees', filters).set('groupBy', groupBy).set('page', page).set('size', 10);
    if (group.groupId !== null) params = params.set('groupId', group.groupId);
    if (group.groupCode) params = params.set('groupCode', group.groupCode);
    return this.http.get<ReportPage<EmployeeReportDetail>>(`${API_BASE_URL}/reports/monitor/employees/details`, { params }).pipe(timeout(15000));
  }

  payrollEmployees(filters: ReportFilters, batchId: number, page: number) {
    const params = this.params('payroll', filters).set('page', page).set('size', 10);
    return this.http.get(`${API_BASE_URL}/reports/monitor/payroll/${batchId}/employees`, { params, responseType: 'text' })
      .pipe(timeout(15000), map(source => parsePayrollReport<ReportPage<PayrollEmployee>>(source)));
  }

  payrollLines(filters: ReportFilters, batchId: number, detailId: number) {
    const params = this.params('payroll', filters);
    return this.http.get(`${API_BASE_URL}/reports/monitor/payroll/${batchId}/employees/${detailId}/lines`, { params, responseType: 'text' })
      .pipe(timeout(15000), map(source => parsePayrollReport<PayrollLine[]>(source)));
  }

  contractDetails(filters: ReportFilters, kind: 'expiring' | 'expired', page: number) {
    const params = this.params('contracts', filters).set('kind', kind).set('page', page).set('size', 10);
    return this.http.get<ReportPage<ContractDetail>>(`${API_BASE_URL}/reports/monitor/contracts`, { params }).pipe(timeout(15000));
  }

  get(type: ReportType, filters: ReportFilters) {
    const url = this.reportUrl(type, filters);
    const params = this.params(type, filters);
    if (type === 'payroll') {
      return this.http.get(url, { params, responseType: 'text' }).pipe(timeout(15000), map(source => parsePayrollReport(source)));
    }
    return this.http.get<Report>(url, { params }).pipe(timeout(15000));
  }

  export(type: ReportType, filters: ReportFilters) {
    const params = type === 'employees'
      ? this.params(type, filters).set('fromDate', filters.fromDate).set('toDate', filters.toDate)
      : this.params(type, filters);
    const url = type === 'employees' ? `${API_BASE_URL}/reports/monitor/employees` : this.reportUrl(type, filters);
    return this.http.get(`${url}/export`, {
      params, responseType: 'blob'
    }).pipe(timeout(30000), catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || ![400, 404, 409].includes(error.status) || !(error.error instanceof Blob)) return throwError(() => error);
      // Blob downloads also carry JSON validation errors; keep the API's useful message.
      return from(error.error.text()).pipe(mergeMap(text => {
        let body: unknown;
        try { body = JSON.parse(text); } catch { body = null; }
        return throwError(() => new HttpErrorResponse({ error: body, headers: error.headers,
          status: error.status, statusText: error.statusText, url: error.url ?? undefined }));
      }));
    }));
  }
}
