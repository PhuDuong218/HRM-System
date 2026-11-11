import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { API_BASE_URL } from '../../services/api.config';

export interface AttendanceContext { role: string; mine: boolean; manage: boolean; serverNow: string; checkoutRequestDays: number; correctionRequestDays: number; }
export interface AttendanceRow {
  scheduleId: number; attendanceId: number | null; employeeId: number; employeeLabel: string; department: string | null; shift: string;
  workDate: string; plannedStart: string; plannedEnd: string; plannedBreakMinutes: number; breakDetailStatus: string;
  actualStart: string | null; actualEnd: string | null; actualBreakMinutes: number; attendanceResult: string | null;
  verificationStatus: string | null; createdBy: number | null; updatedBy: number | null; confirmedBy: number | null;
  confirmedAt: string | null; correctionReason: string | null; source: string; anomalies: string[];
  checkInReviewed: boolean; checkOutReviewed: boolean; canReviewCheckIn: boolean; canReviewCheckOut: boolean; checkInReviewer: string | null; checkInReviewedAt: string | null; checkOutReviewer: string | null; checkOutReviewedAt: string | null; entryRequestId: number | null; canMarkAbsent: boolean; canEdit: boolean; canConfirm: boolean; canUnconfirm: boolean; canRequest: boolean; missingClockToRequest: 'IN' | 'OUT' | 'BOTH' | null; canCheckIn: boolean; canCheckOut: boolean; version: string | null; enteredByLabel: string | null; updatedByLabel: string | null; confirmedByLabel: string | null;
}
export interface Stats { waiting: number; confirmed: number; missing: number; review: number; openRequests: number; }
export interface Audit { action: string; actor: string; occurredAt: string; reason: string | null; }
export interface AttendanceDetail { row: AttendanceRow; breaks: Array<{ start: string; end: string }>; audit: Audit[]; periodLocked: boolean; requests?: AttendanceRequest[]; }
export interface Page<T> { items: T[]; page: number; size: number; totalElements: number; totalPages: number; }
export interface AttendanceSearch extends Page<AttendanceRow> { stats: Stats | null; }
export interface AttendanceRequest {
  requestId: number; requestCode: string; requestType: string; status: string; attendanceId: number; scheduleId: number;
  employeeLabel: string; workDate: string; originalStart: string | null; originalEnd: string | null; originalBreakMinutes: number;
  originalResult: string; originalVerification: string; originalConfirmer: string | null;
  proposedStart: string | null; proposedEnd: string | null; proposedBreakMinutes: number; proposedResult: string; reason: string;
  sender: string; submittedAt: string; submissionDeadline: string; receiver: string | null; receivedAt: string | null;
  processor: string | null; processedAt: string | null; decisionReason: string | null; acceptedEnd: string | null; acceptedStart: string | null; adjustmentReason: string | null;
  canReceive: boolean; canDecide: boolean; processingDeadline: string | null;
}
export interface RequestDetail { request: AttendanceRequest; current: AttendanceDetail; audit: Audit[]; }
export interface Correction { actualStart: string | null; actualEnd: string | null; actualBreakMinutes: number; attendanceResult: string; correctionReason: string; }
export interface RecordFilter { mine: boolean; from: string; to: string; q: string; departmentId: string; verification: string; anomaly: string; page: number; }

@Injectable({ providedIn: 'root' })
export class AttendanceApiService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/attendance-workspace`;
  private params(values: object): HttpParams { let params = new HttpParams(); for (const [key, value] of Object.entries(values)) if (value !== '' && value != null) params = params.set(key, String(value)); return params; }
  context() { return this.http.get<AttendanceContext>(`${this.url}/context`); }
  departments() { return this.http.get<Array<{departmentId: number; departmentName: string}>>(`${this.url}/departments`); }
  records(filter: RecordFilter) { return this.http.get<AttendanceSearch>(`${this.url}/records`, { params: this.params(filter) }); }
  pendingRecords(page: number) { return this.http.get<AttendanceSearch>(`${this.url}/pending`, { params: this.params({ page }) }); }
  detail(id: number, mine: boolean) { return this.http.get<AttendanceDetail>(`${this.url}/schedules/${id}`, { params: this.params({mine}) }); }
  manual(row: AttendanceRow, data: Correction) { return this.http.post<unknown>(`${this.url}/manual`, { ...data, employeeId: row.employeeId, scheduleId: row.scheduleId, workDate: row.workDate }); }
  correct(id: number, data: Correction, expectedVersion: string | null) { return this.http.put<unknown>(`${this.url}/records/${id}`, {correction:data, expectedVersion}); }
  verify(id: number, confirm: boolean, reason: string, expectedVersion: string | null) { return this.http.post<unknown>(`${this.url}/records/${id}/${confirm ? 'confirm' : 'unconfirm'}`, { reason, expectedVersion }); }
  clock(id: number, checkIn: boolean) { return this.http.post<unknown>(`${this.url}/schedules/${id}/${checkIn ? 'check-in' : 'check-out'}`, {}); }
  requests(mine: boolean, from: string, to: string, type: string, status: string, page: number) { return this.http.get<Page<AttendanceRequest>>(`${this.url}/requests`, { params: this.params({mine, from, to, type, status, page}) }); }
  request(id: number, mine: boolean) { return this.http.get<RequestDetail>(`${this.url}/requests/${id}`, { params: this.params({mine}) }); }
  submitRequest(row: AttendanceRow, data: Correction) { return this.http.post<{requestId: number}>(`${this.url}/requests`, { scheduleId: row.scheduleId, type: row.attendanceId == null ? 'MISSING_ATTENDANCE' : row.verificationStatus === 'DA_XAC_NHAN' ? 'CONFIRMED_CORRECTION' : row.actualStart && !row.actualEnd ? 'MISSING_CHECKOUT' : 'UNCONFIRMED_CORRECTION', attendanceId: row.attendanceId, actualStart: data.actualStart, actualEnd: data.actualEnd, actualBreakMinutes: data.actualBreakMinutes, attendanceResult: data.attendanceResult, reason: data.correctionReason }); }
  receive(id: number) { return this.http.post<void>(`${this.url}/requests/${id}/receive`, {}); }
  remindMissingClock(scheduleId: number) {
    return this.http.post<void>(`${this.url}/schedules/${scheduleId}/remind-missing-clock`, {});
  }
  reviewClock(row: AttendanceRow, side: 'in' | 'out', reason: string) { return this.http.post(`${this.url}/records/${row.attendanceId}/review-check-${side}`, {expectedVersion: row.version, reason}); }
  markAbsent(row: AttendanceRow, reason: string) { return this.http.post(`${this.url}/schedules/${row.scheduleId}/absence`, {reason}); }
  decide(id: number, approve: boolean, acceptedEnd: string | null, reason: string, acceptedStart: string | null, adjustmentReason: string) { return this.http.post<void>(`${this.url}/requests/${id}/decision`, {approve, acceptedEnd, reason, acceptedStart, adjustmentReason}); }
  message(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) return 'Không thể thực hiện yêu cầu.';
    const prefix: Record<number, string> = {0:'Không kết nối được máy chủ.', 400:'Dữ liệu không hợp lệ.', 401:'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.', 403:'Không đủ quyền hoặc ngoài phạm vi quản lý.', 404:'Không tìm thấy dữ liệu trong phạm vi được xem.', 409:'Trạng thái đã thay đổi, kỳ đã khóa hoặc dữ liệu xung đột. Hãy tải lại.', 503:'Chức năng tạm thời chưa sẵn sàng.'};
    const detail = error.error?.loi_chi_tiet ?? error.error?.detail ?? error.error?.message;
    return `${prefix[error.status] ?? 'Máy chủ chưa xử lý được yêu cầu.'}${typeof detail === 'string' ? ' ' + detail : ''}`;
  }
}
