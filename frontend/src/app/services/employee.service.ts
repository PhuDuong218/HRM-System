import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, timeout } from 'rxjs';
import { API_BASE_URL } from './api.config';
export interface Employee {
    employeeId: number;
    employeeCode: string;
    fullName: string;
    birthDate: string | null;
    email: string | null;
    phone: string | null;
    hireDate: string;
    employmentStatus: string;
    currentDepartmentName: string | null;
    currentDepartmentId: number | null;
    currentPositionName: string | null;
    currentPositionId: number | null;
    address?: string | null;
    identityNumber?: string | null;
    terminationDate?: string | null;
}
export interface Page {
    content: Employee[];
    number: number;
    totalPages: number;
    totalElements: number;
}
export interface EmployeeForm {
    employeeCode: string;
    fullName: string;
    birthDate: string;
    email: string;
    phone: string;
    identityNumber: string;
    hireDate: string;
    address: string;
    departmentId: number | null;
    positionId: number | null;
}
export function errorMessage(e: unknown) { if (e instanceof HttpErrorResponse) {
    if (e.status === 0)
        return 'Không thể kết nối Backend localhost:2005. Vui lòng kiểm tra Spring Boot.';
    const d = e.error?.loi_chi_tiet ?? e.error?.message;
    if (typeof d === 'string')
        return d;
    if (d && typeof d === 'object')
        return Object.values(d).join(' · ');
    if (e.status === 403)
        return 'Bạn không có quyền thực hiện thao tác này.';
    if (e.status === 404)
        return 'Không tìm thấy hồ sơ. Hãy đóng chi tiết và tải lại.';
} return 'Chưa thể xử lý yêu cầu. Vui lòng thử lại.'; }
@Injectable({ providedIn: 'root' })
export class EmployeeService {
    private http = inject(HttpClient);
    list(admin: boolean, page: number, keyword: string, status: string, department: string): Observable<Page | Employee[]> { return admin ? this.http.get<Page>(API_BASE_URL + '/employees/search', { params: { page, size: 10, ...(keyword ? { keyword } : {}), ...(status ? { status } : {}), ...(department ? { departmentId: department } : {}) } }).pipe(timeout(15000)) : this.http.get<Employee[]>(API_BASE_URL + '/employees').pipe(timeout(15000)); }
    detail(id: number) { return this.http.get<Employee>(API_BASE_URL + `/employees/${id}/detail`).pipe(timeout(15000)); }
    choices(path: string) { return this.http.get<Record<string, unknown>[]>(API_BASE_URL + '/' + path).pipe(timeout(15000)); }
    save(mode: string, id: number | undefined, f: EmployeeForm) { const common = { fullName: f.fullName.trim(), birthDate: f.birthDate || null, email: f.email || null, phone: f.phone || null, identityNumber: f.identityNumber || null }; return mode === 'create' ? this.http.post<Employee>(API_BASE_URL + '/employees', { ...f, ...common, address: f.address || null }) : mode === 'contact' ? this.http.patch<Employee>(API_BASE_URL + `/employees/${id}/contact`, { email: f.email, phone: f.phone, address: f.address, identityNumber: f.identityNumber }) : this.http.put<Employee>(API_BASE_URL + `/employees/${id}`, common); }
    rehire(id:number,request:{effectiveDate:string;departmentId:number;positionId:number;reason:string}) { return this.http.post<void>(API_BASE_URL+`/employees/${id}/rehire`,request).pipe(timeout(20000)); }
    terminate(id: number) { return this.http.post<void>(API_BASE_URL + `/employees/${id}/terminate`, {}).pipe(timeout(20000)); }
}
