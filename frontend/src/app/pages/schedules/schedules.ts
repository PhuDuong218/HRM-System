import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { WorkModuleApiService } from '../../services/work-module-api.service';
@Component({ selector: 'app-schedules', standalone: true, imports: [CommonModule, FormsModule], templateUrl: './schedules.html', styleUrl: '../work-module.css' })
export class SchedulesPage {
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
    load() { if (this.loading) return; this.loading = true; this.error = ''; this.selected = null; this.api.schedules(this.from, this.to, this.employeeId, this.departmentId, this.page).subscribe({ next: (value: unknown) => { this.raw = value; this.records = this.asRows(value); this.loading = false; }, error: e => this.fail(e) }); }
}
