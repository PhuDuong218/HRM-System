import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API_BASE_URL } from './api.config';
import {
  AllowanceCalculationResult,
  AllowanceChangeRequest,
  AllowanceEndRequest,
  AllowanceTypeRequest,
  AllowanceTypeResponse,
  EmployeeAllowanceRequest,
  EmployeeAllowanceResponse,
  PayrollPeriod,
} from '../models/allowance.model';

@Injectable({ providedIn: 'root' })
export class AllowanceService {
  private http = inject(HttpClient);

  // ---- Loại phụ cấp (D-09) ----
  types() {
    return this.http.get<AllowanceTypeResponse[]>(`${API_BASE_URL}/allowance-types`);
  }
  createType(body: AllowanceTypeRequest) {
    return this.http.post<AllowanceTypeResponse>(`${API_BASE_URL}/allowance-types`, body);
  }
  updateType(id: number, body: AllowanceTypeRequest) {
    return this.http.put<AllowanceTypeResponse>(`${API_BASE_URL}/allowance-types/${id}`, body);
  }
  deactivateType(id: number, reason: string) {
    return this.http.delete<void>(`${API_BASE_URL}/allowance-types/${id}`, {
      params: new HttpParams().set('reason', reason),
    });
  }

  // ---- Phụ cấp của nhân viên (D-10) ----
  list(employeeId: number) {
    return this.http.get<EmployeeAllowanceResponse[]>(`${API_BASE_URL}/employees/${employeeId}/allowances`);
  }
  create(employeeId: number, body: EmployeeAllowanceRequest) {
    return this.http.post<EmployeeAllowanceResponse>(`${API_BASE_URL}/employees/${employeeId}/allowances`, body);
  }
  update(employeeId: number, id: number, body: EmployeeAllowanceRequest) {
    return this.http.put<EmployeeAllowanceResponse>(`${API_BASE_URL}/employees/${employeeId}/allowances/${id}`, body);
  }
  remove(employeeId: number, id: number, reason: string) {
    return this.http.delete<void>(`${API_BASE_URL}/employees/${employeeId}/allowances/${id}`, {
      params: new HttpParams().set('reason', reason),
    });
  }
  changeFrom(employeeId: number, id: number, body: AllowanceChangeRequest) {
    return this.http.post<EmployeeAllowanceResponse>(
      `${API_BASE_URL}/employees/${employeeId}/allowances/${id}/change-from`, body);
  }
  endFrom(employeeId: number, id: number, body: AllowanceEndRequest) {
    return this.http.post<EmployeeAllowanceResponse>(
      `${API_BASE_URL}/employees/${employeeId}/allowances/${id}/end-from`, body);
  }

  // ---- Xem trước phụ cấp theo kỳ (D-11) ----
  calculate(employeeId: number, periodId: number) {
    return this.http.get<AllowanceCalculationResult>(
      `${API_BASE_URL}/employees/${employeeId}/allowance-calculations`,
      { params: new HttpParams().set('periodId', periodId) });
  }

  // Kỳ lương do module của Phương cung cấp.
  periods() {
    return this.http.get<PayrollPeriod[]>(`${API_BASE_URL}/payroll-periods`);
  }
}
