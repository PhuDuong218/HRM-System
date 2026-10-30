import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, finalize, timeout } from 'rxjs';
import { EmployeeService, Employee, EmployeeForm, errorMessage } from '../../services/employee.service';
import { AuthService } from '../../services/auth.service';
import { RecordWorkspace } from '../../shared/record-workspace';
const blank = (): EmployeeForm => ({ employeeCode: '', fullName: '', birthDate: '', email: '', phone: '', identityNumber: '', hireDate: '', address: '', departmentId: null, positionId: null });
@Component({ selector: 'app-employees', standalone: true, imports: [FormsModule, DatePipe, RecordWorkspace], templateUrl: './employees.html', styleUrl: './employees.css' })
export class EmployeesPage implements OnInit {
    private api = inject(EmployeeService);
    private auth = inject(AuthService);
    private destroy = inject(DestroyRef);
    private detailRequest?: Subscription;
    rows: Employee[] = [];
    selected: Employee | null = null;
    page = 0;
    pages = 0;
    total = 0;
    keyword = '';
    status = '';
    department = '';
    departments: {
        id: number;
        name: string;
    }[] = [];
    positions: {
        id: number;
        name: string;
    }[] = [];
    loading = false;
    detailLoading = false;
    saving = false;
    error = '';
    detailError = '';
    message = '';
    choicesError = '';
    mode: string | null = null;
    form = blank();
    confirmTermination = false;
    rehireDate = '';
    rehireReason = '';
    get admin() { return this.auth.profile()?.role === 'ADMIN'; }
    ngOnInit() { this.auth.loadCurrentUser().pipe(takeUntilDestroyed(this.destroy)).subscribe({ next: () => this.load(), error: () => this.load() }); this.loadChoices(); }
    loadChoices() { this.choicesError = ''; for (const path of ['departments', 'positions'])
        this.api.choices(path).pipe(takeUntilDestroyed(this.destroy)).subscribe({ next: r => { const k = path === 'departments' ? 'department' : 'position'; const c = r.filter(x => x['isActive'] !== false).map(x => ({ id: Number(x[k + 'Id']), name: String(x[k + 'Name']) })); if (path === 'departments')
                this.departments = c;
            else
                this.positions = c; }, error: e => this.choicesError = (path === 'positions' ? 'Danh mục chức vụ: ' : 'Danh mục phòng ban: ') + errorMessage(e) }); }
    load(page = 0) { if (this.loading || this.saving)
        return; this.loading = true; this.error = ''; this.api.list(this.admin, page, this.keyword.trim(), this.status, this.department).pipe(takeUntilDestroyed(this.destroy), finalize(() => this.loading = false)).subscribe({ next: r => { if (Array.isArray(r)) {
            this.rows = r;
            this.page = 0;
            this.pages = r.length ? 1 : 0;
            this.total = r.length;
        }
        else {
            this.rows = r.content;
            this.page = r.number;
            this.pages = r.totalPages;
            this.total = r.totalElements;
        } }, error: e => this.error = errorMessage(e) }); }
    reset() { this.keyword = ''; this.status = ''; this.department = ''; this.load(); }
    select(row: Employee) { if (this.saving)
        return; this.detailRequest?.unsubscribe(); this.mode = 'detail'; this.selected = row; this.detailLoading = true; this.detailError = ''; this.detailRequest = this.api.detail(row.employeeId).pipe(takeUntilDestroyed(this.destroy)).subscribe({ next: r => { this.selected = r; this.detailLoading = false; }, error: e => { this.detailError = errorMessage(e); this.detailLoading = false; } }); }
    close() { if (this.saving)
        return; this.detailRequest?.unsubscribe(); this.mode = null; this.selected = null; this.detailLoading = false; this.detailError = ''; }
    edit(mode: string) { if (this.saving || this.detailLoading)
        return; this.mode = mode; this.detailError = ''; this.message = ''; this.confirmTermination = false; const p = this.selected; if (mode === 'create') this.selected = null; this.form = mode === 'create' || !p ? blank() : { ...blank(), employeeCode: p.employeeCode, fullName: p.fullName, birthDate: p.birthDate || '', email: p.email || '', phone: p.phone || '', identityNumber: p.identityNumber || '', hireDate: p.hireDate, address: p.address || '' }; }
    save(f: NgForm) { if (this.saving)
        return; if (f.invalid || !this.form.fullName.trim()) {
        f.control.markAllAsTouched();
        return;
    } this.saving = true; this.message = ''; this.detailError = ''; this.api.save(this.mode!, this.selected?.employeeId, this.form).pipe(timeout(20000), takeUntilDestroyed(this.destroy), finalize(() => this.saving = false)).subscribe({ next: r => { this.saving = false; this.message = 'Đã lưu hồ sơ thành công.'; this.load(this.page); this.select(r); }, error: e => this.detailError = errorMessage(e) }); }
    terminate() { if (this.saving || !this.confirmTermination || !this.selected)
        return; const row = this.selected; this.message = ''; this.detailError = ''; this.saving = true; this.api.terminate(row.employeeId).pipe(takeUntilDestroyed(this.destroy), finalize(() => this.saving = false)).subscribe({ next: () => { this.saving = false; this.message = 'Đã xử lý thôi việc, giữ lại lịch sử hồ sơ.'; this.load(this.page); this.select(row); }, error: e => this.detailError = errorMessage(e) }); }
    rehire() {
        if (this.saving || !this.selected || !this.admin || !this.rehireDate || !this.rehireReason.trim() || !this.form.departmentId || !this.form.positionId) return;
        const row = this.selected;
        this.saving = true; this.detailError = ''; this.message = '';
        this.api.rehire(row.employeeId, {effectiveDate:this.rehireDate,departmentId:this.form.departmentId,positionId:this.form.positionId,reason:this.rehireReason.trim()})
            .pipe(takeUntilDestroyed(this.destroy), finalize(()=>this.saving=false)).subscribe({
                next:()=>{this.saving=false;this.message='Đã tiếp nhận lại. Cần lập hợp đồng và kiểm tra quyền đăng nhập riêng.';this.load(this.page);this.select(row);},
                error:e=>this.detailError=errorMessage(e)
            });
    }

}
