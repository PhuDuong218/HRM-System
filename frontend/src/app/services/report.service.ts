import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map, timeout } from 'rxjs';
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
export interface PayrollBatch { periodId: number; periodCode: string; periodStart: string; periodEndExclusive: string; batchId: number; versionNo: number; employeeCount: number; totals: PayrollAmounts; }
export interface PayrollReport { fromDate: string; toDate: string; batchCount: number; payrollRecordCount: number; totals: PayrollAmounts; batches: PayrollBatch[]; }
export type Report = EmployeeReport | ContractReport | WorkReport | PayrollReport;

@Injectable({ providedIn: 'root' })
export class ReportService {
  private http = inject(HttpClient);

  private params(type: ReportType, filters: ReportFilters): HttpParams {
    let params = new HttpParams();
    if (type === 'employees') return params;
    params = params.set('fromDate', filters.fromDate).set('toDate', filters.toDate);
    if (type === 'contracts') {
      params = params.set('expiringWithinDays', filters.expiringWithinDays);
      if (filters.reportDate) params = params.set('reportDate', filters.reportDate);
      if (filters.departmentId) params = params.set('departmentId', filters.departmentId);
    }
    return params;
  }

  get(type: ReportType, filters: ReportFilters) {
    const url = `${API_BASE_URL}/reports/${type}`;
    const params = this.params(type, filters);
    if (type === 'payroll') {
      return this.http.get(url, { params, responseType: 'text' }).pipe(timeout(15000), map(parsePayrollReport));
    }
    return this.http.get<Report>(url, { params }).pipe(timeout(15000));
  }

  export(type: ReportType, filters: ReportFilters) {
    return this.http.get(`${API_BASE_URL}/reports/${type}/export`, {
      params: this.params(type, filters), responseType: 'blob'
    }).pipe(timeout(30000));
  }
}
