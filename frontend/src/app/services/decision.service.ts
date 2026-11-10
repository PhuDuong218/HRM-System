import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API_BASE_URL } from './api.config';
import {
  DecisionActionRequest,
  DecisionApprovalStatus,
  DecisionPageResponse,
  DecisionPayrollResult,
  DecisionRequest,
  DecisionResponse,
} from '../models/decision.model';

@Injectable({ providedIn: 'root' })
export class DecisionService {
  private http = inject(HttpClient);

  search(page = 0, employeeId?: number, status: DecisionApprovalStatus | '' = '', size = 10) {
    let params = new HttpParams().set('page', page).set('size', size);
    if (employeeId !== undefined && employeeId > 0) params = params.set('employeeId', employeeId);
    if (status) params = params.set('status', status);
    return this.http.get<DecisionPageResponse<DecisionResponse>>(
      API_BASE_URL + '/employee-decisions',
      { params },
    );
  }

  get(id: number) {
    return this.http.get<DecisionResponse>(API_BASE_URL + '/employee-decisions/' + id);
  }

  rewardLevels() {
    return this.http.get<{ code: string; label: string; amount: number }[]>(
      API_BASE_URL + '/employee-decisions/reward-levels',
    );
  }

  create(body: DecisionRequest) {
    return this.http.post<DecisionResponse>(API_BASE_URL + '/employee-decisions', body);
  }

  update(id: number, body: DecisionRequest) {
    return this.http.put<DecisionResponse>(API_BASE_URL + '/employee-decisions/' + id, body);
  }

  remove(id: number, reason: string) {
    return this.http.delete<void>(API_BASE_URL + '/employee-decisions/' + id, {
      params: new HttpParams().set('reason', reason),
    });
  }

  approve(id: number, body: DecisionActionRequest) {
    return this.http.post<DecisionResponse>(API_BASE_URL + '/employee-decisions/' + id + '/approve', body);
  }

  reject(id: number, body: DecisionActionRequest) {
    return this.http.post<DecisionResponse>(API_BASE_URL + '/employee-decisions/' + id + '/reject', body);
  }

  payrollItems(employeeId: number, periodId: number) {
    return this.http.get<DecisionPayrollResult>(
      API_BASE_URL + '/employees/' + employeeId + '/decision-payroll-items',
      { params: new HttpParams().set('periodId', periodId) },
    );
  }
}
