import {TranslatePipe} from '../../shared/translate.pipe';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { WorkModuleApiService } from '../../services/work-module-api.service';

type ShiftRow = Record<string, unknown>;

@Component({ selector: 'app-work-shifts', standalone: true, imports: [CommonModule, FormsModule,TranslatePipe], templateUrl: './work-shifts.html', styleUrl: '../work-module.css' })
export class WorkShiftsPage implements OnInit {
    private readonly api = inject(WorkModuleApiService);
    readonly pageSize = 10;
    loading = false;
    error = '';
    records: ShiftRow[] = [];
    selected: ShiftRow | null = null;
    query = '';
    startFrom = '';
    startTo = '';
    endFrom = '';
    endTo = '';
    activeFilter = 'all';
    usedFrom = '';
    usedTo = '';
    page = 0;

    canManage = false;
    editor: 'create' | 'edit' | null = null;
    target: ShiftRow | null = null;
    shiftCode = '';
    shiftName = '';
    startTime = '08:00';
    endTime = '17:00';
    endDayOffset = 0;
    breaks: Array<{ startMinute: number; endMinute: number }> = [];
    actionReason = '';
    actionError = '';
    saving = false;

    ngOnInit(): void {
        this.load();
        this.api.shiftCapabilities().subscribe({ next: r => this.canManage = r.canManage, error: () => this.canManage = false });
    }

    openCreate(): void {
        this.editor = 'create'; this.target = null;
        this.shiftCode = ''; this.shiftName = ''; this.startTime = '08:00';
        this.endTime = '17:00'; this.endDayOffset = 0; this.breaks = []; this.actionError = '';
    }
    openEdit(row: ShiftRow): void {
        if (!row['active']) return;
        this.editor = 'edit'; this.target = row;
        this.shiftCode = String(row['shiftCode']); this.shiftName = String(row['shiftName']);
        this.startTime = String(row['startTime']).slice(0, 5);
        this.endTime = String(row['endTime']).slice(0, 5);
        this.endDayOffset = Number(row['endDayOffset'] ?? 0);
        // Fetching current active detailed breaks is required before saving an existing shift.
        this.breaks = [];
        this.actionError = 'Chưa tải được khoảng nghỉ chi tiết hiện hành. Không lưu sửa ca nếu chưa xác minh dữ liệu này.';
    }
    addBreak(): void { this.breaks.push({ startMinute: 0, endMinute: 0 }); }
    removeBreak(index: number): void { this.breaks.splice(index, 1); }
    save(): void {
        if (!this.canManage || !this.editor || this.saving) return;
        if (this.editor === 'edit') { this.actionError = 'Sửa ca tạm khóa cho đến khi tải được khoảng nghỉ chi tiết. Không được ghi đè dữ liệu nghỉ.'; return; }
        const total = this.breaks.reduce((a, b) => a + Number(b.endMinute) - Number(b.startMinute), 0);
        if (this.breaks.some(b => b.startMinute < 0 || b.endMinute <= b.startMinute)) { this.actionError = 'Khoảng nghỉ không hợp lệ.'; return; }
        const shift = { shiftCode: this.shiftCode, shiftName: this.shiftName, startTime: this.startTime, endTime: this.endTime, endDayOffset: Number(this.endDayOffset), breakMinutes: total, active: true };
        this.saving = true; this.actionError = '';
        this.api.createShift({ shift, breaks: this.breaks }).subscribe({ next: () => { this.saving = false; this.editor = null; this.load(); }, error: e => { this.saving = false; this.actionError = `Không lưu được ca (HTTP ${e.status || 'network'}).`; } });
    }
    changeActive(row: ShiftRow): void {
        if (!this.canManage || this.saving) return;
        const reason = this.actionReason.trim();
        if (!reason) { this.actionError = 'Bắt buộc nhập lý do khóa/mở khóa.'; return; }
        const id = Number(row['shiftId']); const active = !Boolean(row['active']);
        this.saving = true; this.actionError = '';
        this.api.setShiftActive(id, active, reason).subscribe({ next: () => { this.saving = false; this.actionReason = ''; this.load(); }, error: e => { this.saving = false; this.actionError = `Thao tác không thành công (HTTP ${e.status || 'network'}).`; } });
    }

    load(): void {
        if (this.loading) return;
        if (this.usedFrom && this.usedTo && this.usedFrom > this.usedTo) {
            this.error = 'Khoảng ngày sử dụng không hợp lệ.';
            return;
        }
        this.loading = true;
        this.error = '';
        this.selected = null;
        this.api.shifts(this.usedFrom, this.usedTo).subscribe({
            next: value => {
                this.records = this.asRows(value);
                this.page = 0;
                this.loading = false;
            },
            error: error => this.fail(error)
        });
    }

    onSearchChange(): void { this.page = 0; }
    onDateChange(): void { this.load(); }
    clearFilters(): void {
        this.query = ''; this.startFrom = ''; this.startTo = '';
        this.endFrom = ''; this.endTo = ''; this.activeFilter = 'all';
        this.usedFrom = ''; this.usedTo = '';
        this.page = 0;
        this.load();
    }

    get filtered(): ShiftRow[] {
        const q = this.query.trim().toLocaleLowerCase('vi');
        return this.records.filter(row => {
            const code = String(row['shiftCode'] ?? '');
            const name = String(row['shiftName'] ?? '');
            const start = String(row['startTime'] ?? '').slice(0, 5);
            const end = String(row['endTime'] ?? '').slice(0, 5);
            const active = row['active'] === true || row['active'] === 1;
            return (!q || `${code} ${name}`.toLocaleLowerCase('vi').includes(q))
                && (!this.startFrom || start >= this.startFrom)
                && (!this.startTo || start <= this.startTo)
                && (!this.endFrom || end >= this.endFrom)
                && (!this.endTo || end <= this.endTo)
                && (this.activeFilter === 'all' || (this.activeFilter === 'active' ? active : !active));
        });
    }

    get totalPages(): number { return Math.ceil(this.filtered.length / this.pageSize); }
    get pageRows(): ShiftRow[] { return this.filtered.slice(this.page * this.pageSize, (this.page + 1) * this.pageSize); }
    get pageNumbers(): number[] { return Array.from({ length: this.totalPages }, (_, index) => index); }
    goPage(next: number): void { if (next >= 0 && next < this.totalPages) this.page = next; }
    v(row: ShiftRow, key: string): string { const value = row[key]; return value === null || value === undefined || value === '' ? '—' : String(value); }
    private asRows(value: unknown): ShiftRow[] {
        const obj = value && typeof value === 'object' ? value as Record<string, unknown> : {};
        const rows = Array.isArray(value) ? value : (obj['items'] ?? obj['content']);
        return Array.isArray(rows) ? rows.filter(item => item && typeof item === 'object') as ShiftRow[] : [];
    }
    private fail(error: unknown): void {
        this.loading = false;
        if (error instanceof HttpErrorResponse) {
            this.error = error.status === 403 ? 'Không có quyền xem danh mục ca (403).'
                : error.status === 401 ? 'Phiên đăng nhập không hợp lệ (401).'
                    : `Không tải được danh mục ca (HTTP ${error.status || 'network'}).`;
        } else this.error = 'Không thể tải danh mục ca.';
    }
}
