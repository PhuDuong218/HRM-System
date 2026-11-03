import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { TranslatePipe } from '../../shared/translate.pipe';
import { Pagination } from '../../shared/pagination';
import { RecordWorkspace } from '../../shared/record-workspace';
import { DecisionService } from '../../services/decision.service';
import { AllowanceService } from '../../services/allowance.service';
import { Employee, EmployeeService, errorMessage } from '../../services/employee.service';
import { PayrollPeriod } from '../../models/allowance.model';
import {
  DecisionApprovalStatus,
  DecisionPayrollEffect,
  DecisionPayrollResult,
  DecisionRequest,
  DecisionResponse,
  DecisionType,
} from '../../models/decision.model';

type Tab = 'list' | 'payroll';
type Action = 'approve' | 'reject';

@Component({
  selector: 'app-decisions',
  standalone: true,
  imports: [FormsModule, TranslatePipe, Pagination, RecordWorkspace],
  templateUrl: './decisions.html',
  styleUrl: '../work-module.css',
})
export class DecisionsPage implements OnInit {
  private api = inject(DecisionService);
  private periodsApi = inject(AllowanceService);
  private personnel = inject(EmployeeService);
  private destroyRef = inject(DestroyRef);

  tab: Tab = 'list';
  saving = false;
  error = '';
  message = '';
  readonly maxIssuedDate = this.localToday();
  readonly pageSize = 10;

  readonly typeLabels: Record<DecisionType, string> = {
    KHEN_THUONG: 'Khen thưởng',
    KY_LUAT: 'Kỷ luật',
  };
  readonly statusLabels: Record<DecisionApprovalStatus, string> = {
    CHO_DUYET: 'Chờ duyệt',
    DA_DUYET: 'Đã duyệt',
    TU_CHOI: 'Từ chối',
  };
  readonly effectLabels: Record<DecisionPayrollEffect, string> = {
    NONE: 'Không ảnh hưởng lương',
    ADD: 'Cộng vào lương',
    DEDUCT: 'Trừ vào lương',
  };
  readonly types = Object.keys(this.typeLabels) as DecisionType[];
  readonly statuses = Object.keys(this.statusLabels) as DecisionApprovalStatus[];
  readonly effects = Object.keys(this.effectLabels) as DecisionPayrollEffect[];

  employees: Employee[] = [];
  employeesLoading = false;
  employeesError = '';

  rows: DecisionResponse[] = [];
  loading = false;
  listError = '';
  page = 0;
  totalPages = 0;
  filterEmployee: number | null = null;
  filterStatus: DecisionApprovalStatus | '' = '';
  private listRequestVersion = 0;

  showForm = false;
  editing: DecisionResponse | null = null;
  form: DecisionRequest = this.emptyForm();

  actionTarget: DecisionResponse | null = null;
  actionMode: Action = 'approve';
  actionReason = '';

  selectedDecision: DecisionResponse | null = null;
  detailLoading = false;
  detailError = '';
  private detailRequestVersion = 0;

  periods: PayrollPeriod[] = [];
  periodsLoading = false;
  periodsLoaded = false;
  periodsError = '';
  payEmployee: number | null = null;
  payPeriod: number | null = null;
  payroll: DecisionPayrollResult | null = null;
  payrollLoading = false;
  payrollError = '';
  private payrollRequestVersion = 0;

  get panelOpen(): boolean {
    return this.showForm || !!this.actionTarget || this.detailLoading || !!this.selectedDecision;
  }

  ngOnInit(): void {
    this.loadEmployees();
    this.load(0);
  }

  loadEmployees(): void {
    if (this.employeesLoading) return;
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
      this.closeWorkspace();
      this.resetPayroll();
    }
    this.tab = tab;
    this.error = '';
    this.message = '';
    if (tab === 'payroll') this.loadPeriods();
  }

  private fail(error: HttpErrorResponse, fallback: string): string {
    if (error.status === 0) return 'Không kết nối được backend. Hãy kiểm tra kết nối.';
    if (error.status === 401) return 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn.';
    if (error.status === 403) return 'Tài khoản không có quyền thực hiện thao tác này.';
    const detail = error.error?.loi_chi_tiet ?? error.error?.message;
    if (typeof detail === 'string' && detail.trim()) return detail;
    if (detail && typeof detail === 'object') {
      const messages = Object.values(detail).filter(value => typeof value === 'string' && value.trim());
      if (messages.length) return messages.join(' · ');
    }
    if (error.status === 404) return 'Không tìm thấy dữ liệu yêu cầu.';
    if (error.status === 409) return 'Dữ liệu đã thay đổi hoặc chưa đủ điều kiện nghiệp vụ.';
    return fallback;
  }

  load(page = this.page): void {
    const requestedPage = Math.max(0, Math.floor(page));
    const requestVersion = ++this.listRequestVersion;
    this.loading = true;
    this.listError = '';
    this.api.search(requestedPage, this.filterEmployee ?? undefined, this.filterStatus, this.pageSize)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          if (requestVersion !== this.listRequestVersion) return;
          if (requestedPage > 0 && (result.totalPages === 0 || requestedPage >= result.totalPages)) {
            this.load(result.totalPages > 0 ? result.totalPages - 1 : 0);
            return;
          }
          this.rows = result.items;
          this.page = result.page;
          this.totalPages = result.totalPages;
          this.loading = false;
        },
        error: error => {
          if (requestVersion !== this.listRequestVersion) return;
          this.loading = false;
          this.rows = [];
          this.listError = this.fail(error, 'Không tải được danh sách quyết định.');
        },
      });
  }

  applyFilter(employee: string, status: string): void {
    const employeeId = Number(employee);
    this.filterEmployee = employee && Number.isInteger(employeeId) && employeeId > 0 ? employeeId : null;
    this.filterStatus = status as DecisionApprovalStatus | '';
    this.load(0);
  }

  clearFilters(): void {
    this.filterEmployee = null;
    this.filterStatus = '';
    this.load(0);
  }

  employeeName(id: number): string {
    const employee = this.employees.find(item => item.employeeId === id);
    return employee ? employee.employeeCode + ' — ' + employee.fullName : '#' + id;
  }

  private emptyForm(): DecisionRequest {
    return {
      employeeId: 0,
      decisionNumber: '',
      decisionType: 'KHEN_THUONG',
      content: '',
      issuedDate: '',
      payrollEffect: 'NONE',
      amount: null,
      applyDate: null,
      reason: '',
    };
  }

  openCreate(): void {
    if (this.saving) return;
    this.closeWorkspace();
    this.form = this.emptyForm();
    this.error = '';
    this.message = '';
    this.showForm = true;
  }

  openEdit(row: DecisionResponse): void {
    if (this.saving || row.approvalStatus !== 'CHO_DUYET') return;
    this.closeWorkspace();
    this.editing = row;
    this.selectedDecision = row;
    this.form = {
      employeeId: row.employeeId,
      decisionNumber: row.decisionNumber,
      decisionType: row.decisionType,
      content: row.content,
      issuedDate: row.issuedDate,
      payrollEffect: row.payrollEffect,
      amount: row.amount,
      applyDate: row.applyDate,
      reason: '',
    };
    this.error = '';
    this.message = '';
    this.showForm = true;
  }

  cancelForm(): void {
    if (this.saving) return;
    this.showForm = false;
    this.editing = null;
    this.error = '';
  }

  onEffectChange(): void {
    if (this.form.payrollEffect === 'NONE') {
      this.form.amount = null;
      this.form.applyDate = null;
    }
  }

  save(): void {
    if (this.saving) return;
    const form = this.form;
    const amountText = form.amount === null || form.amount === undefined ? '' : String(form.amount);
    const body: DecisionRequest = {
      employeeId: Number(form.employeeId),
      decisionNumber: form.decisionNumber.trim(),
      decisionType: form.decisionType,
      content: form.content.trim(),
      issuedDate: form.issuedDate,
      payrollEffect: form.payrollEffect,
      amount: form.payrollEffect === 'NONE' ? null : (form.amount === null ? null : Number(form.amount)),
      applyDate: form.payrollEffect === 'NONE' ? null : (form.applyDate || null),
      reason: form.reason.trim(),
    };

    this.error = '';
    this.message = '';
    if (!body.employeeId) {
      this.error = 'Vui lòng chọn nhân viên.';
      return;
    }
    if (!body.decisionNumber || !/^[A-Za-z0-9_-]+$/.test(body.decisionNumber) || body.decisionNumber.length > 50) {
      this.error = 'Số quyết định chỉ gồm chữ không dấu, số, _ hoặc -, tối đa 50 ký tự.';
      return;
    }
    if (!body.content || body.content.length > 2000) {
      this.error = 'Nội dung bắt buộc, tối đa 2000 ký tự.';
      return;
    }
    if (!body.issuedDate) {
      this.error = 'Vui lòng chọn ngày ban hành.';
      return;
    }
    if (body.issuedDate > this.maxIssuedDate) {
      this.error = 'Ngày ban hành không được ở tương lai.';
      return;
    }
    if (body.payrollEffect !== 'NONE') {
      if (!/^\d{1,15}(\.\d{1,4})?$/.test(amountText) || body.amount === null || !(body.amount > 0)) {
        this.error = 'Số tiền phải lớn hơn 0, tối đa 15 chữ số nguyên và 4 chữ số thập phân.';
        return;
      }
      if (!body.applyDate) {
        this.error = 'Vui lòng chọn ngày áp dụng vào lương.';
        return;
      }
      if (body.applyDate < body.issuedDate) {
        this.error = 'Ngày áp dụng vào lương không được trước ngày ban hành.';
        return;
      }
    }
    if (!body.reason) {
      this.error = 'Vui lòng nhập lý do.';
      return;
    }
    if (body.reason.length > 500) {
      this.error = 'Lý do không được vượt quá 500 ký tự.';
      return;
    }

    const editing = this.editing;
    this.run(
      editing ? this.api.update(editing.decisionId, body) : this.api.create(body),
      editing ? 'Đã cập nhật quyết định.' : 'Đã tạo quyết định (chờ duyệt).',
      'Không lưu được quyết định.',
      () => {
        this.showForm = false;
        this.editing = null;
        this.selectedDecision = null;
      },
    );
  }

  remove(row: DecisionResponse): void {
    if (this.saving || row.approvalStatus !== 'CHO_DUYET') return;
    const reason = window.prompt('Nhập lý do xóa quyết định ' + row.decisionNumber + ' (tối đa 500 ký tự):');
    if (reason === null) return;
    if (!reason.trim()) {
      this.error = 'Vui lòng nhập lý do.';
      return;
    }
    if (reason.trim().length > 500) {
      this.error = 'Lý do không được vượt quá 500 ký tự.';
      return;
    }
    if (!window.confirm('Xóa quyết định ' + row.decisionNumber + '?')) return;
    this.run(
      this.api.remove(row.decisionId, reason.trim()),
      'Đã xóa quyết định chờ duyệt.',
      'Không xóa được quyết định.',
      () => { this.selectedDecision = null; },
    );
  }

  openAction(row: DecisionResponse, mode: Action): void {
    if (this.saving || row.approvalStatus !== 'CHO_DUYET') return;
    this.actionTarget = row;
    this.actionMode = mode;
    this.actionReason = '';
    this.error = '';
    this.message = '';
  }

  cancelAction(): void {
    if (this.saving) return;
    this.actionTarget = null;
    this.actionReason = '';
    this.error = '';
  }

  submitAction(): void {
    const target = this.actionTarget;
    if (!target || this.saving) return;
    const reason = this.actionReason.trim();
    if (!reason) {
      this.error = 'Vui lòng nhập lý do.';
      return;
    }
    if (reason.length > 500) {
      this.error = 'Lý do không được vượt quá 500 ký tự.';
      return;
    }

    // Backend tự lấy người duyệt từ token, client không gửi approvedBy.
    const call = this.actionMode === 'approve'
      ? this.api.approve(target.decisionId, { reason })
      : this.api.reject(target.decisionId, { reason });
    this.run(
      call,
      this.actionMode === 'approve' ? 'Đã duyệt quyết định.' : 'Đã từ chối quyết định.',
      'Không xử lý được quyết định.',
      () => {
        this.actionTarget = null;
        this.actionReason = '';
        this.selectedDecision = null;
      },
    );
  }

  private run(call: Observable<unknown>, success: string, fallback: string, after?: () => void): void {
    this.saving = true;
    this.error = '';
    this.message = '';
    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving = false;
        this.message = success;
        after?.();
        this.load(this.page);
      },
      error: (error: HttpErrorResponse) => {
        this.saving = false;
        this.error = this.fail(error, fallback);
      },
    });
  }

  closeWorkspace(): void {
    this.showForm = false;
    this.editing = null;
    this.actionTarget = null;
    this.actionReason = '';
    this.selectedDecision = null;
    this.detailLoading = false;
    this.detailError = '';
    this.detailRequestVersion++;
  }

  openDetail(row: DecisionResponse): void {
    if (this.saving) return;
    this.selectedDecision = row;
    this.detailLoading = true;
    this.detailError = '';
    this.error = '';
    const requestVersion = ++this.detailRequestVersion;
    this.api.get(row.decisionId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => {
        if (requestVersion !== this.detailRequestVersion) return;
        this.selectedDecision = result;
        this.detailLoading = false;
      },
      error: (error: HttpErrorResponse) => {
        if (requestVersion !== this.detailRequestVersion) return;
        this.detailLoading = false;
        this.detailError = this.fail(error, 'Không tải được chi tiết quyết định.');
      },
    });
  }

  retryDetail(): void {
    if (this.selectedDecision) this.openDetail(this.selectedDecision);
  }

  loadPeriods(): void {
    if (this.periodsLoading || this.periodsLoaded) return;
    this.periodsLoading = true;
    this.periodsError = '';
    this.periodsApi.periods().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
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

  reloadPeriods(): void {
    if (this.periodsLoading) return;
    this.resetPayroll();
    this.payPeriod = null;
    this.periodsLoaded = false;
    this.periods = [];
    this.loadPeriods();
  }

  resetPayroll(): void {
    this.payrollRequestVersion++;
    this.payroll = null;
    this.payrollError = '';
    this.payrollLoading = false;
  }

  loadPayroll(): void {
    if (this.payrollLoading) return;
    this.payrollError = '';
    this.payroll = null;
    if (!this.payEmployee) {
      this.payrollError = 'Hãy chọn nhân viên.';
      return;
    }
    if (!this.payPeriod) {
      this.payrollError = 'Hãy chọn kỳ lương.';
      return;
    }

    const employeeId = this.payEmployee;
    const periodId = this.payPeriod;
    const requestVersion = ++this.payrollRequestVersion;
    this.payrollLoading = true;
    this.api.payrollItems(employeeId, periodId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          if (requestVersion !== this.payrollRequestVersion ||
              employeeId !== this.payEmployee || periodId !== this.payPeriod) return;
          this.payroll = result;
          this.payrollLoading = false;
        },
        error: (error: HttpErrorResponse) => {
          if (requestVersion !== this.payrollRequestVersion) return;
          this.payrollLoading = false;
          this.payrollError = this.fail(error, 'Không lấy được khoản thưởng/khấu trừ của kỳ.');
        },
      });
  }

  money(value: number | null | undefined): string {
    return value === null || value === undefined
      ? '—'
      : new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 4 }).format(value);
  }

  employeeLabel(employee: Employee): string {
    return employee.employeeCode + ' — ' + employee.fullName;
  }

  private localToday(): string {
    const date = new Date();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return date.getFullYear() + '-' + month + '-' + day;
  }
}