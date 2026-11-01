import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { AbstractControl, FormArray, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, finalize, forkJoin, map, of, throwError } from 'rxjs';
import { API_BASE_URL } from '../../services/api.config';

type Mode = 'view' | 'create' | 'edit' | 'deactivate';
interface ShiftInput {
  shiftCode: string; shiftName: string; startTime: string; endTime: string;
  endDayOffset: number; breakMinutes: number; active: boolean;
}
interface ShiftView extends ShiftInput { shiftId: number; }
interface OffsetBreak { startMinute: number; endMinute: number; }
interface ShiftPayload { shift: ShiftInput; breaks: OffsetBreak[]; }
type BreakForm = FormGroup<{ startMinute: FormControl<number>; endMinute: FormControl<number> }>;

function minutes(value: string): number | null {
  if (!/^\d{2}:\d{2}$/.test(value)) return null;
  const [h, m] = value.split(':').map(Number);
  return h < 24 && m < 60 ? h * 60 + m : null;
}
const shiftValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const start = minutes(control.get('startTime')?.value ?? '');
  const end = minutes(control.get('endTime')?.value ?? '');
  const offset = control.get('endDayOffset')?.value;
  if (start === null || end === null || ![0, 1].includes(offset)) return { timeInvalid: true };
  const duration = end - start + offset * 1440;
  if (duration <= 0 || duration > 1440) return { durationInvalid: true };
  const intervals = [...(control.get('breaks')?.value as OffsetBreak[] ?? [])].sort((a, b) => a.startMinute - b.startMinute);
  let previousEnd = 0;
  for (const item of intervals) {
    if (!Number.isInteger(item.startMinute) || !Number.isInteger(item.endMinute) || item.startMinute < 0 || item.endMinute <= item.startMinute || item.endMinute > duration) return { breakInvalid: true };
    if (item.startMinute < previousEnd) return { breakOverlap: true };
    previousEnd = item.endMinute;
  }
  return null;
};

@Component({
  selector: 'app-work-shifts', standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './work-shifts.html', styleUrl: '../work-module.css',
})
export class WorkShiftsPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly url = `${API_BASE_URL}/management/shifts`;
  records: ShiftView[] = [];
  selected: ShiftView | null = null;
  selectedBreaks: OffsetBreak[] = [];
  breaksLoaded = false;
  detailsWarning = '';
  mode: Mode = 'view';
  panelOpen = false; detailsReady = false;
  loading = false; detailLoading = false; saving = false;
  error = ''; panelError = ''; success = '';
  private unresolvedLegacyBreaks = false;
  private readonly legacyBreakValidator: ValidatorFn = control =>
    this.unresolvedLegacyBreaks && (control.get('breaks')?.value as OffsetBreak[]).length === 0
      ? { legacyBreakDetails: true } : null;
  readonly deactivateReason = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(1000)],
  });
  readonly form = new FormGroup({
    shiftCode: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(50), Validators.pattern(/^[A-Za-z0-9_-]+$/)] }),
    shiftName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(200)] }),
    startTime: new FormControl('08:00', { nonNullable: true, validators: [Validators.required] }),
    endTime: new FormControl('17:00', { nonNullable: true, validators: [Validators.required] }),
    endDayOffset: new FormControl(0, { nonNullable: true, validators: [Validators.required] }),
    active: new FormControl(true, { nonNullable: true }),
    breaks: new FormArray<BreakForm>([]),
  }, { validators: [shiftValidator, this.legacyBreakValidator] });
  readonly searchForm = new FormGroup({
    keyword: new FormControl('', { nonNullable: true }),
    startFrom: new FormControl('', { nonNullable: true }), startTo: new FormControl('', { nonNullable: true }),
    endFrom: new FormControl('', { nonNullable: true }), endTo: new FormControl('', { nonNullable: true }),
    status: new FormControl<'all' | 'active' | 'inactive'>('all', { nonNullable: true }),
  });
  private appliedFilters = this.searchForm.getRawValue();
  get breaks(): FormArray<BreakForm> { return this.form.controls.breaks; }
  get busy(): boolean { return this.loading || this.detailLoading || this.saving; }
  get totalBreakMinutes(): number { return this.breaks.getRawValue().reduce((sum, b) => sum + Math.max(0, b.endMinute - b.startMinute), 0); }
  get panelTitle(): string {
    return { view: 'Thông tin chi tiết', create: 'Thêm ca làm việc', edit: 'Chỉnh sửa ca làm việc', deactivate: 'Xác nhận khóa ca' }[this.mode];
  }
  private normalize(value: string): string {
    return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase().trim();
  }
  get filterError(): string {
    const f = this.searchForm.getRawValue();
    if (f.startFrom && f.startTo && f.startFrom > f.startTo) return 'Khoảng giờ bắt đầu không hợp lệ: Từ giờ phải nhỏ hơn hoặc bằng Đến giờ.';
    if (f.endFrom && f.endTo && f.endFrom > f.endTo) return 'Khoảng giờ kết thúc không hợp lệ: Từ giờ phải nhỏ hơn hoặc bằng Đến giờ.';
    return '';
  }
  get filteredRecords(): ShiftView[] {
    const f = this.appliedFilters, keyword = this.normalize(f.keyword);
    return this.records.filter(row => {
      const start = row.startTime.slice(0, 5), end = row.endTime.slice(0, 5);
      return (!keyword || this.normalize(`${row.shiftCode} ${row.shiftName}`).includes(keyword))
        && (!f.startFrom || start >= f.startFrom) && (!f.startTo || start <= f.startTo)
        && (!f.endFrom || end >= f.endFrom) && (!f.endTo || end <= f.endTo)
        && (f.status === 'all' || (f.status === 'active' ? row.active : !row.active));
    });
  }
  search(): void {
    if (this.filterError) return;
    this.appliedFilters = this.searchForm.getRawValue();
  }
  clearFilters(): void {
    this.searchForm.reset();
    this.appliedFilters = this.searchForm.getRawValue();
  }
  ngOnInit(): void { this.load(); }
  load(): void { if (!this.busy) this.fetchList(); }
  private fetchList(): void {
    this.loading = true; this.error = '';
    this.http.get<ShiftView[]>(this.url).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading = false)).subscribe({
      next: rows => this.records = rows,
      error: e => this.error = this.message(e),
    });
  }
  openCreate(): void {
    if (this.busy) return;
    this.mode = 'create'; this.panelOpen = true; this.selected = null; this.selectedBreaks = [];
    this.breaksLoaded = true; this.detailsWarning = '';
    this.unresolvedLegacyBreaks = false;
    this.detailsReady = true; this.panelError = ''; this.success = '';
    this.breaks.clear();
    this.form.reset({ shiftCode: '', shiftName: '', startTime: '08:00', endTime: '17:00', endDayOffset: 0, active: true });
    this.form.controls.shiftCode.enable();
  }
  view(row: ShiftView): void { this.openExisting(row, 'view'); }
  edit(row: ShiftView): void { this.openExisting(row, 'edit'); }
  private openExisting(row: ShiftView, mode: 'view' | 'edit'): void {
    if (this.busy) return;
    this.mode = mode; this.panelOpen = true; this.selected = row; this.selectedBreaks = [];
    this.breaksLoaded = false; this.detailsWarning = '';
    this.detailsReady = false; this.detailLoading = true; this.panelError = ''; this.success = '';
    // Hai chế độ cấu hình backend công bố API khoảng nghỉ ở hai URL khác nhau.
    // Chỉ thử URL thay thế khi URL đầu trả 404; không vượt qua lỗi phân quyền.
    const breakRequest = this.http.get<{ shift: ShiftView; breaks: OffsetBreak[] }>(
      `${this.url}/${row.shiftId}/details`,
    ).pipe(
      catchError((e: unknown) => e instanceof HttpErrorResponse && e.status === 404
        ? this.http.get<{ shiftId: number; breaks: OffsetBreak[] }>(`${API_BASE_URL}/management/work-time-details/shifts/${row.shiftId}/breaks`)
        : throwError(() => e)),
      map(detail => ({ breaks: detail.breaks as OffsetBreak[] | null, warning: '' })),
      catchError((e: unknown) => of({
        breaks: null as OffsetBreak[] | null,
        warning: `Đã tải thông tin ca nhưng chưa tải được khoảng nghỉ chi tiết. ${this.message(e)} Không thể sửa khi chưa tải đủ dữ liệu.`,
      })),
    );
    forkJoin({
      shift: this.http.get<ShiftView>(`${this.url}/${row.shiftId}`),
      detail: breakRequest,
    }).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.detailLoading = false)).subscribe({
      next: ({ shift, detail }) => {
        this.selected = shift;
        this.breaksLoaded = detail.breaks !== null;
        this.detailsWarning = detail.warning;
        this.selectedBreaks = (detail.breaks ?? []).map(b => ({ ...b }));
        this.unresolvedLegacyBreaks = this.breaksLoaded && shift.breakMinutes > 0 && this.selectedBreaks.length === 0;
        if (this.unresolvedLegacyBreaks) {
          this.detailsWarning = `Ca cũ có ${shift.breakMinutes} phút nghỉ tổng nhưng chưa có giờ nghỉ chi tiết. Hãy nhập các khoảng nghỉ thực tế trước khi lưu; hệ thống không tự suy đoán giờ nghỉ.`;
        }
        if (!this.breaksLoaded) this.mode = 'view';
        this.patchForm(shift, this.selectedBreaks); this.detailsReady = true;
      },
      error: e => this.panelError = this.message(e),
    });
  }
  retryDetails(): void { if (this.selected) this.openExisting(this.selected, this.mode === 'edit' ? 'edit' : 'view'); }
  private patchForm(shift: ShiftView, intervals: OffsetBreak[]): void {
    // FormArray cần tạo control trước khi bind dữ liệu.
    this.breaks.clear({ emitEvent: false });
    intervals.forEach(b => this.breaks.push(this.createBreak(b), { emitEvent: false }));
    this.form.patchValue({ shiftCode: shift.shiftCode, shiftName: shift.shiftName, startTime: shift.startTime.slice(0, 5), endTime: shift.endTime.slice(0, 5), endDayOffset: shift.endDayOffset, active: shift.active });
    this.form.controls.shiftCode.disable({ emitEvent: false });
    this.form.updateValueAndValidity(); this.form.markAsPristine(); this.form.markAsUntouched();
  }
  private createBreak(b: OffsetBreak = { startMinute: 0, endMinute: 1 }): BreakForm {
    return new FormGroup({
      startMinute: new FormControl(b.startMinute, { nonNullable: true, validators: [Validators.required, Validators.min(0)] }),
      endMinute: new FormControl(b.endMinute, { nonNullable: true, validators: [Validators.required, Validators.min(1)] }),
    });
  }
  addBreak(): void { if (!this.busy) { this.breaks.push(this.createBreak()); this.form.markAsDirty(); } }
  removeBreak(index: number): void { if (!this.busy) { this.breaks.removeAt(index); this.form.markAsDirty(); } }
  save(): void {
    if (this.busy || !this.detailsReady || !this.breaksLoaded || !['create', 'edit'].includes(this.mode)) return;
    this.form.markAllAsTouched();
    const v = this.form.getRawValue(), name = v.shiftName.trim();
    if (!name) this.form.controls.shiftName.setErrors({ required: true });
    if (this.form.invalid) return;
    const creating = this.mode === 'create', target = this.selected;
    if (!creating && !target) return;
    const intervals = v.breaks.map(b => ({ ...b })).sort((a, b) => a.startMinute - b.startMinute);
    const body: ShiftPayload = { shift: {
      shiftCode: creating ? v.shiftCode.trim() : target!.shiftCode, shiftName: name,
      startTime: `${v.startTime}:00`, endTime: `${v.endTime}:00`, endDayOffset: v.endDayOffset,
      breakMinutes: this.totalBreakMinutes, active: v.active,
    }, breaks: intervals };
    const endDay = v.endDayOffset === 1 ? ' (ngày hôm sau)' : '';
    const question = creating
      ? 'Bạn có chắc chắn muốn thêm ca làm việc này?'
      : 'Bạn có chắc chắn muốn lưu các thay đổi của ca làm việc này?';
    if (!window.confirm(`${question}\n\nMã ca: ${body.shift.shiftCode}\nTên ca: ${name}\nThời gian: ${v.startTime} – ${v.endTime}${endDay}\nTổng phút nghỉ: ${body.shift.breakMinutes}`)) return;
    const request = creating ? this.http.post<ShiftView>(this.url, body) : this.http.put<ShiftView>(`${this.url}/${target!.shiftId}`, body);
    this.saving = true; this.panelError = ''; this.success = '';
    request.pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving = false)).subscribe({
      next: saved => {
        this.selected = saved; this.selectedBreaks = intervals; this.patchForm(saved, intervals); this.mode = 'view';
        this.success = creating ? 'Đã thêm ca làm việc.' : 'Đã cập nhật ca làm việc.';
        this.fetchList();
      },
      error: e => this.panelError = this.message(e),
    });
  }
  cancel(): void {
    if (this.busy) return;
    if (this.mode === 'edit' && this.selected && this.detailsReady) {
      this.patchForm(this.selected, this.selectedBreaks); this.mode = 'view'; this.panelError = '';
    } else this.close();
  }
  askDeactivate(row: ShiftView): void {
    if (this.busy || !row.active) return;
    this.mode = 'deactivate'; this.panelOpen = true; this.selected = row; this.selectedBreaks = [];
    this.detailsReady = false; this.panelError = ''; this.success = '';
    this.deactivateReason.reset();
  }
  confirmDeactivate(): void {
    if (this.busy || this.mode !== 'deactivate' || !this.selected?.active) return;
    this.deactivateReason.markAsTouched();
    const reason = this.deactivateReason.value.trim();
    if (!reason) this.deactivateReason.setErrors({ required: true });
    if (this.deactivateReason.invalid) return;
    if (!window.confirm(`Bạn có chắc chắn muốn khóa ca làm việc này?\n\nMã ca: ${this.selected.shiftCode}\nTên ca: ${this.selected.shiftName}\nLý do: ${reason}\n\nCa sẽ chuyển sang trạng thái ngừng hoạt động.`)) return;
    this.saving = true; this.panelError = '';
    const id = this.selected.shiftId;
    // API khóa thống nhất ở cả hai chế độ backend, luôn lưu lý do vào audit.
    this.http.patch<ShiftView>(`${this.url}/${id}/deactivate`, { reason }).pipe(
      takeUntilDestroyed(this.destroyRef), finalize(() => this.saving = false),
    ).subscribe({
      next: () => {
        this.panelOpen = false; this.selected = null; this.mode = 'view';
        this.success = 'Đã khóa ca làm việc.'; this.fetchList();
      },
      error: e => this.panelError = this.message(e),
    });
  }
  close(): void {
    if (this.busy) return;
    this.panelOpen = false; this.selected = null; this.selectedBreaks = [];
    this.breaksLoaded = false; this.detailsWarning = '';
    this.detailsReady = false; this.mode = 'view'; this.panelError = '';
  }
  private message(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) return 'Không thể xử lý yêu cầu.';
    const body: unknown = error.error;
    let detail = '';
    if (body && typeof body === 'object') {
      const data = body as Record<string, unknown>;
      for (const key of ['detail', 'loi_chi_tiet', 'message']) {
        if (typeof data[key] === 'string') { detail = data[key] as string; break; }
      }
    }
    const fallback = error.status === 401 ? 'Phiên đăng nhập không hợp lệ.'
      : error.status === 403 ? 'Bạn không có quyền thực hiện thao tác này.'
      : error.status === 404 ? 'Không tìm thấy ca hoặc API.'
      : error.status === 409 ? 'Dữ liệu hoặc trạng thái ca không cho phép thao tác.'
      : `Yêu cầu thất bại (HTTP ${error.status || 'network'}).`;
    return detail ? `${fallback} ${detail}` : fallback;
  }
}
