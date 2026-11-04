import {SpringPage} from '../models/page.model';
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, timeout } from 'rxjs';
import { API_BASE_URL } from './api.config';
export interface Employee {
    gender?:string|null;
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
export interface Page extends SpringPage<Employee> { number: number; }

export interface EmployeeForm {
    gender?:string|null;
    reason?:string;
    privateFieldsEditable?: boolean;
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
    list(admin: boolean, page: number, keyword: string, status: string, department: string, position = ""): Observable<Page | Employee[]> { return admin ? this.http.get<Page>(API_BASE_URL + '/employees/search', { params: { page, size: 10, ...(keyword ? { keyword } : {}), ...(status ? { status } : {}), ...(department ? { departmentId: department } : {}), ...(position ? { positionId: position } : {}) } }).pipe(timeout(15000)) : this.http.get<Employee[]>(API_BASE_URL + '/employees').pipe(timeout(15000)); }
    detail(id: number) { return this.http.get<Employee>(API_BASE_URL + `/employees/${id}/detail`).pipe(timeout(15000)); }
    audit(id:number,page=0){return this.http.get<{content:Record<string,unknown>[];number:number;totalPages:number}>(API_BASE_URL+'/g1/audit',{params:{targetTable:'employees',recordId:id,page,size:20}}).pipe(timeout(15000));}
    all() { return this.http.get<Employee[]>(API_BASE_URL+'/employees').pipe(timeout(15000)); }
    choices(path: string) { return this.http.get<Record<string, unknown>[]>(API_BASE_URL + '/' + path).pipe(timeout(15000)); }
    save(mode: string, id: number | undefined, f: EmployeeForm) { const common = { gender:f.gender||null, fullName: f.fullName.trim(), birthDate: f.birthDate || null, email: f.email || null, phone: f.phone || null, reason:f.reason?.trim()||null, ...(f.privateFieldsEditable?{identityNumber:f.identityNumber||null}:{}) }; return mode === 'create' ? this.http.post<Employee>(API_BASE_URL + '/employees', { employeeCode:f.employeeCode,hireDate:f.hireDate,departmentId:f.departmentId,positionId:f.positionId,...common }) : mode === 'contact' ? this.http.patch<Employee>(API_BASE_URL + `/employees/${id}/contact`, { email: f.email, phone: f.phone, reason:f.reason?.trim()||null }) : this.http.put<Employee>(API_BASE_URL + `/employees/${id}`, common); }
    rehire(id:number,request:{effectiveDate:string;departmentId:number;positionId:number;reason:string}) { return this.http.post<void>(API_BASE_URL+`/employees/${id}/rehire`,request).pipe(timeout(20000)); }
    terminate(id: number,decision:{reason:string;lastWorkingDate:string;decisionType:string;decisionNumber:string}) { return this.http.post<void>(API_BASE_URL + `/employees/${id}/termination-decisions`, decision).pipe(timeout(20000)); }
}
