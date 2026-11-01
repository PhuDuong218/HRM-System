import {TranslatePipe} from '../../shared/translate.pipe';
import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { WorkModuleApiService } from '../../services/work-module-api.service';
@Component({ selector: 'app-overtime', standalone: true, imports: [CommonModule, FormsModule,TranslatePipe], templateUrl: './overtime.html', styleUrl: '../work-module.css' })
export class OvertimePage {
    private api = inject(WorkModuleApiService);
    private localDate(y: number, m: number, d: number): string { return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`; }
    monthStart(): string { const d = new Date(); return this.localDate(d.getFullYear(), d.getMonth() + 1, 1); }
    monthEnd(): string { const d = new Date(); return this.localDate(d.getFullYear(), d.getMonth() + 1, new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate()); }
    loading = false; error = ''; records: Record<string, unknown>[] = []; selected: Record<string, unknown> | null = null; raw: unknown = null;
    asRows(value: unknown): Record<string, unknown>[] { const data = Array.isArray(value) ? value : this.obj(value)['items'] ?? this.obj(value)['content']; return Array.isArray(data) ? data.filter(x => typeof x === 'object' && x !== null) as Record<string, unknown>[] : []; }
    obj(value: unknown): Record<string, unknown> { return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}; }
    v(row: Record<string, unknown>, key: string): string { const x = row[key]; return x === null || x === undefined || x === '' ? '—' : String(x); }
    fail(error: unknown) { this.loading = false; if (error instanceof HttpErrorResponse) { this.error = error.status === 404 ? 'API chưa được bật hoặc đường dẫn chưa khả dụng (404).' : error.status === 403 ? 'Tài khoản không có quyền xem dữ liệu (403).' : error.status === 401 ? 'Phiên đăng nhập không hợp lệ (401).' : `Không tải được dữ liệu (HTTP ${error.status || 'network'}).`; } else { this.error = 'Không thể tải dữ liệu.'; } }
    from = this.monthStart(); to = this.monthEnd(); employeeId = ''; departmentId = ''; status = ''; page = 0;
    load() { if (this.loading) return; this.loading = true; this.error = ''; this.selected = null; this.api.overtime(this.from, this.to, this.status, this.page).subscribe({ next: (value: unknown) => { this.raw = value; this.records = this.asRows(value); this.loading = false; }, error: e => this.fail(e) }); }
    tab: 'mine' | 'approve' | 'actual' | 'other' = 'mine';
    switchTab(next: 'mine' | 'approve' | 'actual' | 'other') { this.tab = next; this.action = next === 'actual' ? 'confirm' : next === 'other' ? 'cancel' : 'approve'; this.feedback = ''; }
    busy = false; feedback = ''; targetId = ''; actionReason = ''; requestedStart = ''; requestedEnd = '';
    actualStart = ''; actualEnd = ''; actualBreakMinutes = 0; actualReason = '';
    action: 'approve' | 'reject' | 'cancel' | 'confirm' | 'reopen-confirmation' | 'overflow-review' = 'approve';
    validId() { const n = Number(this.targetId); return /^\d+$/.test(this.targetId) && Number.isSafeInteger(n) && n > 0 ? n : null; }
    private execute(call: import('rxjs').Observable<unknown>) { this.busy = true; this.feedback = ''; call.subscribe({ next: () => { this.busy = false; this.feedback = 'Backend đã xử lý thao tác thành công.'; this.load(); }, error: e => { this.busy = false; this.fail(e); this.feedback = this.error; } }); }
    register() { if (!this.requestedStart || !this.requestedEnd || this.requestedEnd <= this.requestedStart || !this.actionReason.trim()) { this.feedback = 'Cần thời gian bắt đầu/kết thúc hợp lệ và lý do.'; return; } this.execute(this.api.registerOvertime({ requestedStart: this.requestedStart + ':00', requestedEnd: this.requestedEnd + ':00', reason: this.actionReason.trim() })); }
    process() {
        const id = this.validId(); if (id === null) { this.feedback = 'Cần ID OT hợp lệ từ backend.'; return; }
        if (['reject', 'cancel', 'reopen-confirmation', 'overflow-review'].includes(this.action) && !this.actionReason.trim()) { this.feedback = 'Thao tác này bắt buộc nhập lý do hoặc ghi chú.'; return; }
        const body = this.action === 'overflow-review' ? { note: this.actionReason.trim() } : ['reject', 'cancel', 'reopen-confirmation'].includes(this.action) ? { reason: this.actionReason.trim() } : {};
        this.execute(this.api.overtimeAction(id, this.action, body));
    }
    recordActual() {
        const id = this.validId(); if (id === null || !this.actualStart || !this.actualEnd || this.actualEnd <= this.actualStart || !Number.isInteger(this.actualBreakMinutes) || this.actualBreakMinutes < 0) { this.feedback = 'Kiểm tra ID, khoảng giờ thực tế và phút nghỉ.'; return; }
        this.execute(this.api.overtimeActual(id, { actualStart: this.actualStart + ':00', actualEnd: this.actualEnd + ':00', actualBreakMinutes: this.actualBreakMinutes, breaks: [], reason: this.actualReason.trim() }));
    }
}
