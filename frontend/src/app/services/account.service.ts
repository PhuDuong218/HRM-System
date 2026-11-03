import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { timeout } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { EmployeeService } from './employee.service';

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

export type AccountPage = import('../models/page.model').ItemPage<Account>;

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

export interface AccountUpdate { roleId: number; username: string; active: boolean; }
export interface AccountCreate extends AccountUpdate { employeeId: number; password: string; confirmPassword: string; }

@Injectable({ providedIn: 'root' })
export class AccountService {
  private http = inject(HttpClient);
  private employeeApi = inject(EmployeeService);

  employees() { return this.employeeApi.all(); }
  create(body: AccountCreate) { return this.http.post<Account>(`${API_BASE_URL}/admin/accounts`,body).pipe(timeout(20000)); }
  update(id: number,body: AccountUpdate) { return this.http.put<Account>(`${API_BASE_URL}/admin/accounts/${id}`,body).pipe(timeout(20000)); }
  resetPassword(id: number,newPassword: string,confirmPassword: string) { return this.http.post<void>(`${API_BASE_URL}/admin/accounts/${id}/reset-password`,{newPassword,confirmPassword}).pipe(timeout(20000)); }
  createRole(roleCode: string,roleName: string,permissionIds: number[]) { return this.http.post<AccountRole>(`${API_BASE_URL}/admin/roles`,{roleCode,roleName,permissionIds}).pipe(timeout(20000)); }
  updateRole(id: number,roleName: string) { return this.http.put<AccountRole>(`${API_BASE_URL}/admin/roles/${id}`,{roleName}).pipe(timeout(20000)); }
  replacePermissions(id: number,permissionIds: number[]) { return this.http.put<AccountRole>(`${API_BASE_URL}/admin/roles/${id}/permissions`,{permissionIds}).pipe(timeout(20000)); }
  deleteRole(id: number) { return this.http.delete<void>(`${API_BASE_URL}/admin/roles/${id}`).pipe(timeout(20000)); }
  createPermission(permissionCode: string,permissionName: string,description: string) { return this.http.post<AccessPermission>(`${API_BASE_URL}/admin/permissions`,{permissionCode,permissionName,description}).pipe(timeout(20000)); }
  updatePermission(id: number,permissionName: string,description: string) { return this.http.put<AccessPermission>(`${API_BASE_URL}/admin/permissions/${id}`,{permissionName,description}).pipe(timeout(20000)); }
  deletePermission(id: number) { return this.http.delete<void>(`${API_BASE_URL}/admin/permissions/${id}`).pipe(timeout(20000)); }

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
