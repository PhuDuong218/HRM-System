import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from './api.config';

export interface Department {
  departmentId: number; departmentCode: string; departmentName: string; isActive: boolean;
  headEmployeeId: number | null; headEmployeeCode: string | null;
  headFullName: string | null; headEffectiveFrom: string | null;
}
export interface Head {
  assignmentId: number; employeeId: number; employeeCode: string; fullName: string;
  effectiveFrom: string; effectiveTo: string | null;
}
export interface Assignment {
  employeeId: number; employeeCode: string; employeeName: string; effectiveFrom: string;
}
export interface Position { positionId: number; positionCode: string; positionName: string; isActive: boolean; }
export interface SpringPage<T> { content: T[]; totalPages: number; totalElements: number; }
export interface Page<T> { items: T[]; totalPages: number; totalElements: number; }

@Injectable({ providedIn: 'root' })
export class DepartmentService {
  private http = inject(HttpClient);
  list(page: number) { return this.http.get<SpringPage<Department>>(`${API_BASE_URL}/departments`, { params: { page, size: 10 } }); }
  positions(page: number) { return this.http.get<SpringPage<Position>>(`${API_BASE_URL}/positions`, { params: { page, size: 100 } }); }
  history(id: number, page: number) { return this.http.get<Page<Head>>(`${API_BASE_URL}/departments/${id}/head/history`, { params: { page, size: 10 } }); }
  candidates(id: number, page: number) { return this.http.get<Page<Assignment>>(`${API_BASE_URL}/departments/${id}/head/candidates`, { params: { page, size: 10 } }); }
  appoint(id: number, employeeId: number, effectiveFrom: string, previousHeadPositionId: number | null, reason: string) {
    return this.http.put<Head>(`${API_BASE_URL}/departments/${id}/head`, { employeeId, effectiveFrom, previousHeadPositionId, reason });
  }
  end(id: number, effectiveFrom: string, nextPositionId: number, reason: string) {
    return this.http.post<void>(`${API_BASE_URL}/departments/${id}/head/end`, { effectiveFrom, nextPositionId, reason });
  }
}
