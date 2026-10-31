import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API_BASE_URL } from './api.config';
@Injectable({ providedIn: 'root' })
export class WorkModuleApiService {
    private http = inject(HttpClient);
    shifts(from = '', to = '') {
        let params = new HttpParams();
        if (from) params = params.set('usedFrom', from);
        if (to) params = params.set('usedTo', to);
        return this.http.get<unknown>(`${API_BASE_URL}/management/shifts`, { params });
    }
    shiftCapabilities() { return this.http.get<{ canManage: boolean }>(`${API_BASE_URL}/management/shifts/capabilities`); }
    createShift(body: unknown) {
        return this.http.post<unknown>(`${API_BASE_URL}/management/shifts`, body);
    }
    updateShift(id: number, body: unknown) {
        return this.http.put<unknown>(`${API_BASE_URL}/management/shifts/${id}`, body);
    }
    setShiftActive(id: number, active: boolean, reason: string) {
        return this.http.patch<unknown>(`${API_BASE_URL}/management/shifts/${id}/${active ? 'activate' : 'deactivate'}`, { reason });
    }
    schedules(from: string, to: string, employeeId: string, departmentId: string, page: number) {
        let p = new HttpParams().set('from', from).set('to', to).set('page', page);
        if (employeeId) p = p.set('employeeId', employeeId);
        if (departmentId) p = p.set('departmentId', departmentId);
        return this.http.get<unknown>(`${API_BASE_URL}/management/schedules/search`, { params: p });
    }
    overtime(from: string, to: string, status: string, page: number) {
        let p = new HttpParams().set('from', from).set('to', to).set('page', page).set('size', 50);
        if (status) p = p.set('status', status);
        return this.http.get<unknown>(`${API_BASE_URL}/overtime/requests`, { params: p });
    }
    overtimeDetail(id: number) {
        return this.http.get<unknown>(`${API_BASE_URL}/overtime/requests/${id}`);
    }
    confirmed(from: string, to: string, employeeId: string) {
        let p = new HttpParams().set('from', from).set('to', to);
        if (employeeId) p = p.set('employeeId', employeeId);
        return this.http.get<unknown>(`${API_BASE_URL}/management/attendance/confirmed-summary`, { params: p });
    }
}
