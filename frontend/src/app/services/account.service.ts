import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { timeout } from 'rxjs';
import { API_BASE_URL } from './api.config';

export interface Account {
  accountId: number;
  employeeId: number;
  employeeCode: string | null;
  fullName: string | null;
  roleId: number;
  roleCode: string;
  roleName: string;
  username: string;
  active: boolean;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface AccountPage {
  items: Account[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AccountRole {
  roleId: number;
  roleCode: string;
  roleName: string;
  permissions: AccessPermission[];
}

export interface AccessPermission {
  permissionId: number;
  permissionCode: string;
  permissionName: string;
  description: string | null;
}

export interface AccountFilters {
  keyword: string;
  active: '' | 'true' | 'false';
  roleId: string;
}
export interface CreateAccount {
  employeeId: number;
  roleId: number;
  username: string;
  password: string;
  confirmPassword: string;
  active: boolean;
}
export interface ActiveDepartment { departmentId: number; departmentName: string; }
export interface ScopeDepartmentOption extends ActiveDepartment { active: boolean; assigned: boolean; }
export interface AccountContact { email: string | null; phone: string | null; }
export interface EligibleEmployee { employeeId: number; employeeCode: string; fullName: string; }
export interface EligibleEmployeePage { items: EligibleEmployee[]; page: number; totalPages: number; totalElements: number; }

@Injectable({ providedIn: 'root' })
export class AccountService {
  private http = inject(HttpClient);

  create(account: CreateAccount) {
    return this.http.post<Account>(`${API_BASE_URL}/admin/accounts`, account).pipe(timeout(15000));
  }

  resetPassword(accountId: number, newPassword: string, confirmPassword: string) {
    return this.http.post<void>(`${API_BASE_URL}/admin/accounts/${accountId}/reset-password`, { newPassword, confirmPassword }).pipe(timeout(15000));
  }

  changeStatus(accountId: number, expectedActive: boolean, active: boolean) {
    return this.http.patch<Account>(`${API_BASE_URL}/admin/accounts/${accountId}/status`, { expectedActive, active }).pipe(timeout(15000));
  }

  editIdentity(accountId: number, change: { expectedUsername: string; expectedRoleId: number; username: string; roleId: number; expectedDepartmentIds?: number[]; departmentIds?: number[] }) {
    return this.http.patch<Account>(`${API_BASE_URL}/admin/accounts/${accountId}/identity`, change).pipe(timeout(15000));
  }

  editPermission(permissionId: number, change: { expectedName: string; expectedDescription: string | null; permissionName: string; description: string | null }) {
    return this.http.patch<AccessPermission>(`${API_BASE_URL}/admin/permissions/${permissionId}/details`, change).pipe(timeout(15000));
  }

  get(accountId: number) {
    return this.http.get<Account>(`${API_BASE_URL}/admin/accounts/${accountId}`).pipe(timeout(15000));
  }

  contact(accountId: number) {
    return this.http.get<AccountContact>(`${API_BASE_URL}/admin/accounts/${accountId}/contact`).pipe(timeout(15000));
  }

  activeHrDepartments(accountId: number) {
    return this.http.get<ActiveDepartment[]>(`${API_BASE_URL}/admin/hr-scopes/account/${accountId}/active-departments`).pipe(timeout(15000));
  }

  hrDepartmentOptions(accountId: number) {
    return this.http.get<ScopeDepartmentOption[]>(`${API_BASE_URL}/admin/hr-scopes/account/${accountId}/department-options`).pipe(timeout(15000));
  }

  eligibleEmployees(keyword: string, page: number) {
    const params = new HttpParams().set('keyword', keyword).set('page', page).set('size', 10);
    return this.http.get<EligibleEmployeePage>(`${API_BASE_URL}/admin/accounts/eligible-employees`, { params }).pipe(timeout(15000));
  }

  list(page: number, filters: AccountFilters) {
    let params = new HttpParams().set('page', page).set('size', 10);
    if (filters.keyword) params = params.set('keyword', filters.keyword);
    if (filters.active) params = params.set('active', filters.active);
    if (filters.roleId) params = params.set('roleId', filters.roleId);
    return this.http.get<AccountPage>(`${API_BASE_URL}/admin/accounts`, { params }).pipe(timeout(15000));
  }

  roles() {
    return this.http.get<AccountRole[]>(`${API_BASE_URL}/admin/roles`).pipe(timeout(15000));
  }

  permissions() {
    return this.http.get<AccessPermission[]>(`${API_BASE_URL}/admin/permissions`).pipe(timeout(15000));
  }
}
