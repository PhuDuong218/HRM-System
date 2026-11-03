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
    shiftDetails(id: number) {
        return this.http.get<{
            shift: Record<string, unknown>;
            breaks: Array<{ startMinute: number; endMinute: number }>;
        }>(`${API_BASE_URL}/management/shifts/${id}/details`);
    }
    shiftCapabilities() { return this.http.get<{ canManage: boolean }>(`${API_BASE_URL}/management/shifts/capabilities`); }
    createShift(body: unknown) {
        return this.http.post<unknown>(`${API_BASE_URL}/management/shifts`, body);
    }
    updateShift(id: number, body: unknown) {
        return this.http.put<unknown>(`${API_BASE_URL}/management/shifts/${id}`, body);
    }
    updateShiftBasicInfo(id: number, shiftName: string) {
        return this.http.patch<unknown>(`${API_BASE_URL}/management/shifts/${id}/basic-info`, { shiftName });
    }
    setShiftActive(id: number, active: boolean, reason: string) {
        return this.http.patch<unknown>(`${API_BASE_URL}/management/shifts/${id}/${active ? 'activate' : 'deactivate'}`, { reason });
    }
    scheduleDepartments() {
        return this.http.get<Array<{ departmentId: number; departmentName: string }>>(`${API_BASE_URL}/management/schedules/filter-departments`);
    }
    resolveScheduleEmployee(code: string) {
        return this.http.get<{ employeeId: number }>(`${API_BASE_URL}/management/schedules/resolve-employee`, { params: new HttpParams().set('code', code) });
    }
    scheduleState(id: number) {
        return this.http.get<{ scheduleId: number; state: string }>(`${API_BASE_URL}/management/schedules/${id}/state`);
    }
    lockScheduleEdit(id: number, reason: string) {
        return this.http.post<{ scheduleId: number; state: string }>(`${API_BASE_URL}/management/schedules/${id}/lock-edit`, { reason });
    }
    unlockScheduleEdit(id: number, reason: string) {
        return this.http.post<{ scheduleId: number; state: string }>(`${API_BASE_URL}/management/schedules/${id}/unlock-edit`, { reason });
    }
    scheduleCapabilities() {
        return this.http.get<{ canManage: boolean }>(`${API_BASE_URL}/management/schedules/capabilities`);
    }
    createScheduleZeroBreak(body: unknown) {
        return this.http.post<unknown>(`${API_BASE_URL}/management/schedules`, body);
    }
    updateScheduleZeroBreak(id: number, body: unknown) {
        return this.http.put<unknown>(`${API_BASE_URL}/management/schedules/${id}`, body);
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
    attendanceManagement(from: string, to: string, employeeId: string, verificationStatus: string, page: number) {
        let p = new HttpParams().set('from', from).set('to', to).set('page', page).set('size', 20);
        if (employeeId) p = p.set('employeeId', employeeId);
        if (verificationStatus) p = p.set('verificationStatus', verificationStatus);
        return this.http.get<unknown>(`${API_BASE_URL}/management/attendance`, { params: p });
    }
    submitScheduleChange(body: { scheduleId: number; proposedShiftId: number | null; proposedStart: string; proposedEnd: string; proposedBreaks: unknown[]; reason: string }) {
        return this.http.post<{ requestId: number }>(`${API_BASE_URL}/management/schedule-change-requests`, body);
    }
    decideScheduleChange(id: number, approve: boolean, rejectionReason: string) {
        return this.http.post<void>(`${API_BASE_URL}/management/schedule-change-requests/${id}/decision`, { approve, rejectionReason });
    }
    submitAttendanceCorrection(attendanceId: number, correction: unknown) {
        return this.http.post<{ requestId: number }>(`${API_BASE_URL}/management/attendance-correction-requests`, { attendanceId, correction });
    }
    decideAttendanceCorrection(id: number, approve: boolean, rejectionReason: string) {
        return this.http.post<void>(`${API_BASE_URL}/management/attendance-correction-requests/${id}/decision`, { approve, rejectionReason });
    }
    registerOvertime(body: { requestedStart: string; requestedEnd: string; reason: string }) {
        return this.http.post<unknown>(`${API_BASE_URL}/overtime/requests`, body);
    }
    overtimeAction(id: number, action: 'approve' | 'reject' | 'cancel' | 'confirm' | 'reopen-confirmation' | 'overflow-review', body: unknown = {}) {
        return this.http.post<unknown>(`${API_BASE_URL}/overtime/requests/${id}/${action}`, body);
    }
    overtimeActual(id: number, body: { actualStart: string; actualEnd: string; actualBreakMinutes: number; breaks: unknown[]; reason: string }) {
        return this.http.put<unknown>(`${API_BASE_URL}/overtime/requests/${id}/actual`, body);
    }
}