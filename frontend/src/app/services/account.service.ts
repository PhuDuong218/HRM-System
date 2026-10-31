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

@Injectable({ providedIn: 'root' })
export class AccountService {
  private http = inject(HttpClient);

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
