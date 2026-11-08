import {SearchAssistDirective} from '../../shared/search-assist.directive';
import {ActionDialogService} from '../../shared/ui/action-dialog';
import {EditFormDirective} from '../../shared/edit-form.directive';
import {HrmNotice} from '../../shared/ui/notice';
import {Breadcrumb} from '../../shared/breadcrumb';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { employmentApiError } from '../../shared/employment-api-error';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '../../shared/translate.pipe';
import { RecordWorkspace } from '../../shared/record-workspace';
import { SelectedRowScrollDirective } from '../../shared/selected-row-scroll.directive';
import { AllowanceService } from '../../services/allowance.service';
import { Employee, EmployeeService, errorMessage } from '../../services/employee.service';
import {
  AllowanceBasis,
  AllowanceCalculationResult,
  AllowanceTypeRequest,
  AllowanceTypeResponse,
  EmployeeAllowanceRequest,
  EmployeeAllowanceResponse,
  PayrollPeriod,
} from '../../models/allowance.model';

type Tab = 'types' | 'assigned' | 'preview';
type AssignMode = 'create' | 'edit' | 'change' | 'end';

@Component({
  selector: 'app-allowances',
  standalone: true,
  imports:[SearchAssistDirective,EditFormDirective,HrmNotice,Breadcrumb,FormsModule, TranslatePipe, RecordWorkspace, SelectedRowScrollDirective],
  templateUrl: './allowances.html',
  styleUrls: ['../work-module.css', '../module-list.css'],
})
export class AllowancesPage implements OnInit {
 private actionDialogInPage=inject(ActionDialogService);

  private api = inject(AllowanceService);
  private personnel = inject(EmployeeService);
  private destroyRef = inject(DestroyRef);

  tab: Tab = 'types';
  saving = false;
  error = '';
  message = '';

  readonly basisLabels: Record<AllowanceBasis, string> = {
    THEO_KY: 'Theo kỳ (cố định mỗi kỳ)',
    THEO_NGAY_CONG: 'Theo ngày công',
    THEO_GIO: 'Theo giờ',
  };
  readonly bases = Object.keys(this.basisLabels) as AllowanceBasis[];

  // ===== Loại phụ cấp =====
  types: AllowanceTypeResponse[] = [];
  typesLoading = false;
  showTypeForm = false;
  editingType: AllowanceTypeResponse | null = null;
  typeForm: AllowanceTypeRequest = this.emptyType();

  // ===== Nhân viên dùng chung cho tab 2 & 3 =====
  employees: Employee[] = [];
  employeesLoading = false;
  employeesError = '';
  employeeId: number | null = null;
  private rowsRequest?: Subscription;
  private previewRequest?: Subscription;
  private previewRequestVersion = 0;
  private rowsLoadedEmployeeId: number | null = null;

  // ===== Phụ cấp của nhân viên =====
  rows: EmployeeAllowanceResponse[] = [];
  rowsLoading = false;
  showAssignForm = false;
  assignMode: AssignMode = 'create';
  target: EmployeeAllowanceResponse | null = null;
  assignForm: EmployeeAllowanceRequest = this.emptyAssign();
  stopFrom = '';
  stopReason = '';

  // ===== Xem trước theo kỳ =====
  periods: PayrollPeriod[] = [];
  periodsLoading = false;
  periodsLoaded = false;
  periodsError = '';
  periodId: number | null = null;
  preview: AllowanceCalculationResult | null = null;
  previewLoading = false;
  previewError = '';

  ngOnInit(): void {
    this.loadTypes();
    this.loadEmployees();
  }

  private loadEmployees(): void {
    this.employeesLoading = true;
    this.employeesError = '';
    this.personnel.list(false, 0, '', '', '')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          this.employees = Array.isArray(result) ? result : result.content;
          this.employeesLoading = false;
        },
        error: error => {
          this.employeesLoading = false;
          this.employeesError = errorMessage(error);
        },
      });
  }

  setTab(tab: Tab): void {
    if (this.saving) return;
    if (tab !== this.tab) {
      this.showTypeForm = false;
      this.showAssignForm = false;
      this.editingType = null;
      this.target = null;
    }
    this.tab = tab;
    this.error = '';
    this.message = '';
    if (tab === 'assigned' && this.employeeId && this.rowsLoadedEmployeeId !== this.employeeId) {
      this.loadRows();
    }
    if (tab === 'preview' && !this.periodsLoaded && !this.periodsLoading) {
      this.loadPeriods();
    }
  }

  closeWorkspace(): void {
    if (this.saving) return;
    this.showTypeForm = false;
    this.showAssignForm = false;
    this.editingType = null;
    this.target = null;
    this.error = '';
  }

  private fail(e: HttpErrorResponse, fallback: string): string {
    return employmentApiError(e, fallback);
  }

  // ================= LOẠI PHỤ CẤP =================
  private emptyType(): AllowanceTypeRequest {
    return { typeCode: '', typeName: '', description: '', isActive: true, isTaxable: true, countsForSocialInsurance: false, reason: '' };
  }

  loadTypes(): void {
    if (this.typesLoading) return;
    this.typesLoading = true;
    this.error = '';
    this.api.types().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: rows => { this.types = rows; this.typesLoading = false; },
      error: (e: HttpErrorResponse) => {
        this.typesLoading = false;
        this.error = this.fail(e, 'Không tải được danh mục loại phụ cấp.');
      },
    });
  }

  openCreateType(): void {
    if (this.saving) return;
    this.editingType = null;
    this.typeForm = this.emptyType();
    this.error = ''; this.message = '';
    this.showTypeForm = true;
  }

  openEditType(row: AllowanceTypeResponse): void {
    if (this.saving) return;
    this.editingType = row;
    this.typeForm = {
      typeCode: row.typeCode, typeName: row.typeName,
      description: row.description ?? '', isActive: row.isActive,
      isTaxable: row.isTaxable, countsForSocialInsurance: row.countsForSocialInsurance, reason: '',
    };
    this.error = ''; this.message = '';
    this.showTypeForm = true;
  }

  cancelType(): void {
    if (this.saving) return;
    this.showTypeForm = false;
    this.editingType = null;
  }

  saveType(): void {
    if (this.saving) return;
    const req: AllowanceTypeRequest = {
      typeCode: this.typeForm.typeCode.trim(),
      typeName: this.typeForm.typeName.trim(),
      description: (this.typeForm.description ?? '').trim() || null,
      isActive: this.typeForm.isActive,
      isTaxable: this.typeForm.isTaxable,
      countsForSocialInsurance: this.typeForm.countsForSocialInsurance,
      reason: this.typeForm.reason.trim(),
    };
    this.error = ''; this.message = '';
    if (!req.typeCode || !req.typeName) { this.error = 'Hãy nhập mã và tên loại phụ cấp.'; return; }
    if (!/^[A-Za-z0-9_-]+$/.test(req.typeCode)) { this.error = 'Mã chỉ gồm chữ không dấu, số, dấu _ hoặc dấu -.'; return; }
    if (req.typeCode.length > 50 || req.typeName.length > 200) { this.error = 'Mã tối đa 50 ký tự; tên tối đa 200 ký tự.'; return; }
    if ((req.description ?? '').length > 1000) { this.error = 'Mô tả tối đa 1000 ký tự.'; return; }
    if (!req.reason) { this.error = 'Vui lòng nhập lý do.'; return; }

    const editing = this.editingType;
    if (editing) req.typeCode = editing.typeCode; // mã không đổi sau khi tạo
    this.saving = true;
    const call$ = editing ? this.api.updateType(editing.allowanceTypeId, req) : this.api.createType(req);
    call$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving = false; this.showTypeForm = false; this.editingType = null;
        this.message = editing ? 'Đã cập nhật loại phụ cấp.' : 'Đã tạo loại phụ cấp.';
        this.loadTypes();
      },
      error: (e: HttpErrorResponse) => { this.saving = false; this.error = this.fail(e, 'Không lưu được loại phụ cấp.'); },
    });
  }

  async deactivateType(row: AllowanceTypeResponse): Promise<void> {
    if (this.saving || !row.isActive) return;
    const reason = (await this.actionDialogInPage.prompt(`Ngừng sử dụng loại "${row.typeName}"?\nCác khoản phụ cấp đã gán trong lịch sử vẫn được giữ.\nNhập lý do:`));
    if (reason === null) return;
    if (!reason.trim()) { this.error = 'Vui lòng nhập lý do.'; return; }
    if (reason.trim().length > 500) { this.error = 'Lý do không được vượt quá 500 ký tự.'; return; }
    this.saving = true; this.error = ''; this.message = '';
    this.api.deactivateType(row.allowanceTypeId, reason.trim()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.saving = false; this.message = 'Đã ngừng sử dụng loại phụ cấp.'; this.loadTypes(); },
      error: (e: HttpErrorResponse) => { this.saving = false; this.error = this.fail(e, 'Không ngừng sử dụng được loại phụ cấp.'); },
    });
  }

  // ================= PHỤ CẤP CỦA NHÂN VIÊN =================
  private emptyAssign(): EmployeeAllowanceRequest {
    return { allowanceTypeId: 0, effectiveFrom: '', effectiveTo: null, calculationBasis: 'THEO_KY', amountType: 'FIXED', amount: 0, conditionNote: '', reason: '' };
  }

  onEmployeeChange(value: string): void {
    if (this.saving) return;
    const nextId = value ? Number(value) : null;
    this.employeeId = nextId !== null && Number.isSafeInteger(nextId) && nextId > 0 ? nextId : null;

    this.rowsRequest?.unsubscribe();
    this.rowsLoading = false;
    this.rowsLoadedEmployeeId = null;
    this.rows = [];
    this.showAssignForm = false;
    this.target = null;
    this.error = '';
    this.message = '';

    this.previewRequest?.unsubscribe();
    this.previewRequestVersion++;
    this.previewLoading = false;
    this.preview = null;
    this.previewError = '';

    if (this.employeeId && this.tab === 'assigned') this.loadRows();
  }

  onPeriodChange(): void {
    this.previewRequest?.unsubscribe();
    this.previewRequestVersion++;
    this.previewLoading = false;
    this.preview = null;
    this.previewError = '';
  }

  loadRows(): void {
    if (!this.employeeId || this.rowsLoading) return;
    const id = this.employeeId;
    this.rowsRequest?.unsubscribe();
    this.rowsLoading = true;
    this.rows = [];
    this.error = '';
    this.rowsRequest = this.api.list(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: rows => {
          if (this.employeeId !== id) return;
          this.rows = rows;
          this.rowsLoading = false;
          this.rowsLoadedEmployeeId = id;
        },
        error: (error: HttpErrorResponse) => {
          if (this.employeeId !== id) return;
          this.rowsLoading = false;
          this.error = this.fail(error, 'Không tải được phụ cấp của nhân viên.');
        },
      });
  }

  openAssign(mode: AssignMode, row?: EmployeeAllowanceResponse): void {
    if (this.saving || this.typesLoading || !this.employeeId) return;
    if (mode === 'create' && !this.canCreateForSelectedEmployee()) {
      this.error = 'Chỉ gán phụ cấp mới cho nhân viên đang làm việc.';
      return;
    }
    if (mode === 'edit' && row && !this.canEditAllowance(row)) {
      this.error = 'Chỉ sửa được khoản chưa có hiệu lực và loại phụ cấp vẫn đang hoạt động.';
      return;
    }
    if (mode === 'change' && row && !this.canChangeAllowance(row)) {
      this.error = 'Không thể đổi mức cho khoảng này; hãy kiểm tra ngày bắt đầu và ngày kết thúc cũ.';
      return;
    }
    if (mode === 'end' && row && !this.canEndAllowance(row)) {
      this.error = 'Khoản này không còn khoảng ngày hợp lệ để kết thúc.';
      return;
    }
    if (mode !== 'create' && !row) return;

    this.assignMode = mode;
    this.target = row ?? null;
    this.error = '';
    this.message = '';
    this.stopFrom = '';
    this.stopReason = '';
    if (mode === 'create') {
      this.assignForm = this.emptyAssign();
    } else if (row) {
      this.assignForm = {
        allowanceTypeId: row.allowanceTypeId,
        effectiveFrom: mode === 'edit' ? row.effectiveFrom : '',
        effectiveTo: mode === 'edit' || mode === 'change' ? row.effectiveTo : null,
        calculationBasis: row.calculationBasis,
        amountType: row.amountType,
        amount: row.amount,
        conditionNote: row.conditionNote ?? '',
        reason: '',
      };
    }
    this.showAssignForm = true;
  }

  cancelAssign(): void {
    if (this.saving) return;
    this.showAssignForm = false; this.target = null;
  }

  activeTypes(): AllowanceTypeResponse[] { return this.types.filter(t => t.isActive); }

  private localToday(): string {
    const today = new Date();
    return today.getFullYear() + '-' + String(today.getMonth() + 1).padStart(2, '0') + '-' + String(today.getDate()).padStart(2, '0');
  }

  private addDays(value: string, days: number): string {
    const date = new Date(value + 'T00:00:00');
    date.setDate(date.getDate() + days);
    return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0');
  }

  private maxDate(values: string[]): string {
    return values.reduce((max, value) => value > max ? value : max);
  }

  canCreateForSelectedEmployee(): boolean {
    return Boolean(this.employeeId && this.employees.some(e => e.employeeId === this.employeeId && e.employmentStatus === 'DANG_LAM'));
  }

  canEditAllowance(row: EmployeeAllowanceResponse): boolean {
    return row.effectiveFrom > this.localToday() &&
      this.types.some(t => t.allowanceTypeId === row.allowanceTypeId && t.isActive);
  }

  canRemoveAllowance(row: EmployeeAllowanceResponse): boolean {
    return row.effectiveFrom > this.localToday();
  }

  minAssignmentStart(): string | null {
    if (this.assignMode === 'edit') return this.addDays(this.localToday(), 1);
    if (this.assignMode === 'change' && this.target) {
      return this.maxDate([this.localToday(), this.addDays(this.target.effectiveFrom, 1)]);
    }
    return null;
  }

  canChangeAllowance(row: EmployeeAllowanceResponse): boolean {
    if (!this.types.some(t => t.allowanceTypeId === row.allowanceTypeId && t.isActive)) return false;
    const earliest = this.maxDate([this.localToday(), this.addDays(row.effectiveFrom, 1)]);
    return !row.effectiveTo || earliest <= row.effectiveTo;
  }

  minStopFrom(): string {
    if (!this.target) return this.localToday();
    return this.maxDate([this.localToday(), this.addDays(this.target.effectiveFrom, 1)]);
  }

  canEndAllowance(row: EmployeeAllowanceResponse): boolean {
    const earliest = this.maxDate([this.localToday(), this.addDays(row.effectiveFrom, 1)]);
    return !row.effectiveTo || earliest <= row.effectiveTo;
  }

  saveAssign(): void {
    if (this.saving || !this.employeeId) return;
    this.error = '';
    this.message = '';
    const employeeId = this.employeeId;

    if (this.assignMode === 'end') {
      if (!this.target) return;
      if (!this.stopFrom) { this.error = 'Vui lòng chọn ngày dừng.'; return; }
      if (this.stopFrom < this.localToday()) { this.error = 'Không thể kết thúc hồi tố.'; return; }
      if (this.stopFrom < this.minStopFrom()) { this.error = 'Ngày dừng phải để lại ít nhất một ngày hiệu lực cho khoản cũ.'; return; }
      if (this.target.effectiveTo && this.stopFrom > this.target.effectiveTo) { this.error = 'Ngày dừng phải trước hoặc bằng ngày cuối hiện tại.'; return; }
      if (!this.stopReason.trim()) { this.error = 'Vui lòng nhập lý do.'; return; }
      if (this.stopReason.trim().length > 500) { this.error = 'Lý do không được vượt quá 500 ký tự.'; return; }
      this.run(
        this.api.endFrom(employeeId, this.target.employeeAllowanceId, { stopFrom: this.stopFrom, reason: this.stopReason.trim() }),
        'Đã kết thúc khoản phụ cấp từ ngày chọn.',
        'Không kết thúc được khoản phụ cấp.',
      );
      return;
    }

    const f = this.assignForm;
    const amountText = String(f.amount ?? '');
    const body: EmployeeAllowanceRequest = {
      allowanceTypeId: Number(f.allowanceTypeId),
      effectiveFrom: f.effectiveFrom,
      effectiveTo: f.effectiveTo || null,
      calculationBasis: f.calculationBasis,
      amountType: f.amountType,
      amount: Number(f.amount),
      conditionNote: (f.conditionNote ?? '').trim() || null,
      reason: f.reason.trim(),
    };
    if (!body.allowanceTypeId) { this.error = 'Vui lòng chọn loại phụ cấp.'; return; }
    if (!this.activeTypes().some(type => type.allowanceTypeId === body.allowanceTypeId)) { this.error = 'Loại phụ cấp đã ngừng dùng; hãy chọn loại đang hoạt động.'; return; }
    if (!body.effectiveFrom) { this.error = 'Vui lòng chọn ngày bắt đầu hiệu lực.'; return; }
    if (body.effectiveTo && body.effectiveTo < body.effectiveFrom) { this.error = 'Ngày kết thúc phải từ ngày bắt đầu trở đi.'; return; }
    if (!/^\d{1,15}(\.\d{1,4})?$/.test(amountText)) { this.error = 'Đơn giá phải không âm, tối đa 15 chữ số nguyên và 4 chữ số thập phân.'; return; }
    if (body.amountType === 'PERCENT_BASE_SALARY' && body.amount > 100) {
      this.error = 'Tỷ lệ phụ cấp phải từ 0 đến 100%.';
      return;
    }
    if ((body.conditionNote ?? '').length > 1000) { this.error = 'Điều kiện / ghi chú tối đa 1000 ký tự.'; return; }
    if (!body.reason) { this.error = 'Vui lòng nhập lý do.'; return; }
    if (body.reason.length > 500) { this.error = 'Lý do không được vượt quá 500 ký tự.'; return; }

    if (this.assignMode === 'edit') {
      if (!this.target || !this.canEditAllowance(this.target)) { this.error = 'Chỉ sửa được khoản chưa có hiệu lực.'; return; }
      if (body.effectiveFrom <= this.localToday()) { this.error = 'Bản sửa phải bắt đầu từ ngày mai trở đi.'; return; }
      if (body.allowanceTypeId !== this.target.allowanceTypeId) { this.error = 'Không thể đổi loại của khoản đã gán.'; return; }
      this.run(this.api.update(employeeId, this.target.employeeAllowanceId, body), 'Đã cập nhật khoản phụ cấp.', 'Không cập nhật được khoản phụ cấp.');
    } else if (this.assignMode === 'change') {
      if (!this.target || !this.canChangeAllowance(this.target)) { this.error = 'Không thể đổi mức cho khoảng phụ cấp này.'; return; }
      const minimumStart = this.minAssignmentStart();
      if (body.effectiveFrom < this.localToday() || (minimumStart !== null && body.effectiveFrom < minimumStart)) { this.error = 'Ngày đổi phải từ hôm nay và sau ngày bắt đầu cũ.'; return; }
      if (body.allowanceTypeId !== this.target.allowanceTypeId) { this.error = 'Không thể đổi loại phụ cấp trong thao tác đổi mức.'; return; }
      if (body.effectiveTo !== this.target.effectiveTo) { this.error = 'Đổi mức phải giữ nguyên ngày cuối của khoảng cũ.'; return; }
      this.run(this.api.changeFrom(employeeId, this.target.employeeAllowanceId, { newAllowance: body }),
        'Đã tạo khoản phụ cấp mới; khoản cũ được giữ trong lịch sử.',
        'Không đổi được mức phụ cấp.');
    } else if (this.assignMode === 'create') {
      if (!this.canCreateForSelectedEmployee()) { this.error = 'Chỉ gán phụ cấp mới cho nhân viên đang làm việc.'; return; }
      this.run(this.api.create(employeeId, body), 'Đã gán phụ cấp.', 'Không gán được phụ cấp.');
    }
  }

  async removeFuture(row: EmployeeAllowanceResponse): Promise<void> {
    if (this.saving || !this.employeeId) return;
    if (!this.canRemoveAllowance(row)) {
      this.error = 'Chỉ xóa được khoản phụ cấp chưa có hiệu lực.';
      return;
    }
    const reason = (await this.actionDialogInPage.prompt('Nhập lý do xóa khoản phụ cấp chưa có hiệu lực (tối đa 500 ký tự):'));
    if (reason === null) return;
    if (!reason.trim()) { this.error = 'Vui lòng nhập lý do.'; return; }
    if (reason.trim().length > 500) { this.error = 'Lý do không được vượt quá 500 ký tự.'; return; }
    if (!(await this.actionDialogInPage.confirm('Xóa khoản phụ cấp từ ngày ' + row.effectiveFrom + '?'))) return;
    this.run(this.api.remove(this.employeeId, row.employeeAllowanceId, reason.trim()),
      'Đã xóa khoản phụ cấp chưa hiệu lực.', 'Không xóa được khoản phụ cấp.');
  }

  private run(call$: import('rxjs').Observable<unknown>, ok: string, failText: string): void {
    this.saving = true;
    call$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.saving = false; this.showAssignForm = false; this.target = null; this.message = ok; this.loadRows(); },
      error: (e: HttpErrorResponse) => { this.saving = false; this.error = this.fail(e, failText); }, // 409 trùng loại/giao nhau hiển thị nguyên văn từ backend
    });
  }

  // ================= XEM TRƯỚC THEO KỲ =================
  loadPeriods(): void {
    if (this.periodsLoading || this.periodsLoaded) return;
    this.periodsLoading = true;
    this.periodsError = '';
    this.api.periods().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: periods => {
        this.periods = periods;
        this.periodsLoading = false;
        this.periodsLoaded = true;
      },
      error: (error: HttpErrorResponse) => {
        this.periodsLoading = false;
        this.periodsError = this.fail(error, 'Không tải được danh sách kỳ lương.');
      },
    });
  }

  runPreview(): void {
    this.previewError = '';
    this.preview = null;
    if (!this.employeeId) { this.previewError = 'Hãy chọn nhân viên.'; return; }
    if (!this.periodId) { this.previewError = 'Hãy chọn kỳ lương.'; return; }

    this.previewRequest?.unsubscribe();
    const requestVersion = ++this.previewRequestVersion;
    const employeeId = this.employeeId;
    const periodId = this.periodId;
    this.previewLoading = true;
    this.previewRequest = this.api.calculate(employeeId, periodId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          if (requestVersion !== this.previewRequestVersion || employeeId !== this.employeeId || periodId !== this.periodId) return;
          this.preview = result;
          this.previewLoading = false;
        },
        // A backend 409/503 remains an error; it is never converted to a zero result.
        error: (error: HttpErrorResponse) => {
          if (requestVersion !== this.previewRequestVersion || employeeId !== this.employeeId || periodId !== this.periodId) return;
          this.previewLoading = false;
          this.previewError = this.fail(error, 'Backend chưa tính được phụ cấp cho kỳ này.');
        },
      });
  }

  money(v: number | null | undefined): string {
    return v === null || v === undefined ? '—' : new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 4 }).format(v);
  }
  employeeLabel(e: Employee): string { return `${e.employeeCode} — ${e.fullName}`; }
}
