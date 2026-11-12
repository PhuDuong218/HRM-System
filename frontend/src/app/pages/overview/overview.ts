import { FormsModule } from '@angular/forms';
import { formatMoney } from '../../services/report-format';
import {HrmNotice} from '../../shared/ui/notice';
import {PageHeader} from '../../shared/page-header';
import {HrmButton} from '../../shared/ui/button';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { EMPTY, Subscription, catchError, exhaustMap, filter, timer } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { LanguageService } from '../../services/language.service';
import { ContractReport, EmployeeReport, GroupRow, OverviewMonth, PayrollBatch, PayrollReport, PendingItem, PendingOverview, ReportFilters, ReportService, WorkReport } from '../../services/report.service';

type LoadState = 'loading' | 'ready' | 'error';
interface StatusItem { label: string; count: number; color: string; }

@Component({
  selector: 'app-overview', standalone: true, imports:[HrmNotice,PageHeader,HrmButton,RouterLink,FormsModule],
  templateUrl: './overview.html', styleUrl: './overview.scss'
})
export class OverviewPage implements OnInit {
  language = inject(LanguageService);
  private api = inject(ReportService);
  private router = inject(Router, { optional: true });
  private auth = inject(AuthService);
  private destroyRef = inject(DestroyRef);
  private requests = new Subscription();
  private pendingRequest?: Subscription;
  attendanceStatus: {workDate: string; present: number; absent: number; leave: number; missing: number} | null = null;
  attendanceState: LoadState = 'loading';
  months: OverviewMonth[] = [];
  trendsState: LoadState = 'loading';
  payroll: PayrollBatch | null = null;
  payrollState: LoadState | 'denied' = 'loading';
  readonly chartSeries = {
    work: [{ key: 'overtime', label: 'Đơn OT đã xác nhận', color: '#087c70' },
      { key: 'late', label: 'Lượt đi muộn', color: '#b05b04' }],
    movement: [{ key: 'hired', label: 'Tuyển dụng', color: '#165b9e' },
      { key: 'terminated', label: 'Nghỉ việc', color: '#a33d72' }]
  } as const;

  today = '';
  selectedPayrollId = 'latest';
  batches: PayrollBatch[] = [];
  openReport(event: MouseEvent, tab: string, focus: string): void {
    if (!this.isAdmin || event.defaultPrevented || event.button !== 0 ||
        event.ctrlKey || event.metaKey || event.altKey || event.shiftKey ||
        (event.target instanceof Element && event.target.closest('a,button,input,select,label'))) return;
    void this.router?.navigate(['/reports'], { queryParams: this.reportLink(tab, focus) });
  }

  reportLink(tab: string, focus: string): Record<string, string> {
    const day = this.today;
    const from = focus === 'movement' || focus === 'trend' ? (this.months[0] ? this.months[0].month + '-01' : day) : day;
    if (tab === 'payroll' && this.payroll) {
      return { tab, focus, from: this.payroll.periodStart, to: this.payroll.periodEnd, grouping: 'month' };
    }
    if (focus === 'movement' || focus === 'trend' || focus === 'daily') {
      return { tab, focus, from, to: day };
    }
    return { tab, focus };
  }

  selectPayroll(id = this.selectedPayrollId): void {
    const selected = this.batches.find(batch => String(batch.batchId) === id);
    this.selectedPayrollId = selected ? id : 'latest';
    this.payroll = selected ?? this.batches[0] ?? null;
  }
  fromDate = '';
  filters!: ReportFilters;
  employeeReport: EmployeeReport | null = null;
  contracts: ContractReport | null = null;
  work: WorkReport | null = null;
  employeeState: LoadState = 'loading';
  contractState: LoadState = 'loading';
  workState: LoadState = 'loading';
  employeeError = '';
  contractError = '';
  workError = '';
  isHr = false;
  isAdmin = false;
  pending: PendingOverview | null = null;
  pendingState: LoadState = 'loading';
  pendingError = '';
  readonly pendingCards = [
    { key: 'attendance', label: 'Công chưa xác nhận', description: 'Bản ghi công', route: '/attendance', query: { pending: 'unconfirmed' } },
    { key: 'leave', label: 'Nghỉ phép chờ duyệt', description: 'Đơn chờ duyệt theo quy trình', route: '/requests', query: { pending: 'leave' } },
    { key: 'overtimeApproval', label: 'OT chờ duyệt', description: 'Đơn đăng ký tăng ca', route: '/overtime', query: { pending: 'approval' } },
    { key: 'overtimeActual', label: 'OT chưa hoàn tất', description: 'Chờ ghi nhận / xác nhận thực tế', route: '/overtime', query: { pending: 'actual' } }
  ] as const;

  ngOnInit(): void { this.load(); }

  load(): void {
    this.requests.unsubscribe();
    this.requests = new Subscription();
    this.attendanceStatus = null; this.attendanceState = 'loading';
    this.months = []; this.trendsState = 'loading';
    this.batches = []; this.payroll = null; this.payrollState = 'loading';
    this.pending = null; this.pendingState = 'loading'; this.pendingError = '';
    this.isHr = this.isAdmin = false;
    this.today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
    this.fromDate = `${this.today.slice(0, 8)}01`;
    this.filters = { fromDate: this.fromDate, toDate: this.today, reportDate: this.today, expiringWithinDays: 30, departmentId: '' };
    this.employeeReport = null; this.contracts = null; this.work = null;
    this.employeeState = this.contractState = this.workState = 'loading';
    this.employeeError = this.contractError = this.workError = '';
    this.requests.add(this.auth.loadCurrentUser().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: profile => {
        this.isHr = profile.role === 'HR';
        this.isAdmin = profile.role === 'ADMIN';
        if (profile.role !== 'ADMIN' && profile.role !== 'HR') { this.failAll('Bạn chưa có quyền xem số liệu tổng quan.'); return; }
        this.loadPending();
        this.pollAttendanceStatus();
        this.loadTrends();
        this.loadDashboardCharts();
        if (this.isHr) this.loadHrOverview();
        else this.loadReports();
      },
      error: error => this.failAll(this.errorMessage(error))
    }));
  }

  loadPending(): void {
    if (!this.isHr && !this.isAdmin) return;
    this.pendingRequest?.unsubscribe();
    this.pending = null; this.pendingState = 'loading'; this.pendingError = '';
    this.pendingRequest = this.api.getPendingOverview().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => {
        const data = { ...result };
        for (const card of this.pendingCards) {
          const item = result?.[card.key];
          const valid = item && (item.state === 'READY'
            ? Number.isSafeInteger(item.count) && item.count! >= 0
            : ['DENIED', 'ERROR'].includes(item.state) && item.count === null);
          data[card.key] = valid ? item : { count: null, state: 'ERROR' };
        }
        this.pending = data; this.pendingState = 'ready';
      },
      error: error => { this.pendingError = this.errorMessage(error); this.pendingState = 'error'; }
    });
    this.requests.add(this.pendingRequest);
  }

  pendingItem(key: 'attendance' | 'leave' | 'overtimeApproval' | 'overtimeActual'): PendingItem | null {
    return this.pending?.[key] ?? null;
  }

  private loadHrOverview(): void {
    this.requests.add(this.api.getHrOverview().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: data => {
        const employees = data?.employees;
        const contracts = data?.contracts;
        const work = data?.work;
        if (Array.isArray(employees?.byDepartment) && Array.isArray(employees?.byStatus) &&
            [...employees.byDepartment, ...employees.byStatus].every(row => Number.isSafeInteger(row?.employeeCount) && row.employeeCount >= 0)) {
          this.employeeReport = employees; this.employeeState = 'ready';
        } else { this.employeeError = 'Dữ liệu tổng quan chưa hợp lệ.'; this.employeeState = 'error'; }
        if (Number.isSafeInteger(contracts?.effectiveCount) && contracts.effectiveCount >= 0 &&
            Number.isSafeInteger(contracts?.expiringCount) && contracts.expiringCount >= 0) {
          this.contracts = contracts; this.contractState = 'ready';
        } else { this.contractError = 'Dữ liệu tổng quan chưa hợp lệ.'; this.contractState = 'error'; }
        if (Number.isSafeInteger(work?.confirmedWorkDays) && work.confirmedWorkDays >= 0 &&
            Number.isSafeInteger(work?.confirmedAbsentDays) && work.confirmedAbsentDays >= 0) {
          this.work = work; this.workState = 'ready';
        } else { this.workError = 'Dữ liệu tổng quan chưa hợp lệ.'; this.workState = 'error'; }
      },
      error: error => this.failAll(this.errorMessage(error))
    }));
  }

  private loadReports(): void {
    this.requests.add(this.api.get('employees', this.filters).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: report => {
        const data = report as EmployeeReport;
        if (!Array.isArray(data?.byDepartment) || !Array.isArray(data?.byStatus) ||
            ![...data.byDepartment, ...data.byStatus].every(row => Number.isSafeInteger(row?.employeeCount) && row.employeeCount >= 0)) {
          this.employeeError = 'Dữ liệu báo cáo chưa hợp lệ.'; this.employeeState = 'error'; return;
        }
        this.employeeReport = data; this.employeeState = 'ready';
      },
      error: error => { this.employeeError = this.errorMessage(error); this.employeeState = 'error'; }
    }));
    this.requests.add(this.api.get('contracts', this.filters).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: report => {
        const data = report as ContractReport;
        if (!Number.isSafeInteger(data?.effectiveCount) || data.effectiveCount < 0 || !Number.isSafeInteger(data?.expiringCount) || data.expiringCount < 0) { this.contractError = 'Dữ liệu báo cáo chưa hợp lệ.'; this.contractState = 'error'; return; }
        this.contracts = data; this.contractState = 'ready';
      },
      error: error => { this.contractError = this.errorMessage(error); this.contractState = 'error'; }
    }));
    this.requests.add(this.api.get('work', this.filters).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: report => {
        const data = report as WorkReport;
        if (!Number.isSafeInteger(data?.confirmedWorkDays) || data.confirmedWorkDays < 0 || !Number.isSafeInteger(data?.confirmedAbsentDays) || data.confirmedAbsentDays < 0) { this.workError = 'Dữ liệu báo cáo chưa hợp lệ.'; this.workState = 'error'; return; }
        this.work = data; this.workState = 'ready';
      },
      error: error => { this.workError = this.errorMessage(error); this.workState = 'error'; }
    }));
  }

  private loadTrends(): void {
    this.requests.add(this.api.getOverviewTrends().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: months => { this.months = months; this.trendsState = 'ready'; },
      error: () => { this.trendsState = 'error'; }
    }));
  }

  private loadDashboardCharts(): void {
    if (!this.isAdmin) {
      this.payrollState = 'denied';
      return;
    }
    this.requests.add(this.api.get('payroll', { ...this.filters, fromDate: '0001-01-01' })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: report => {
          const batches = (report as PayrollReport).batches;
          this.batches = [...batches].sort((a, b) =>
            b.periodEnd.localeCompare(a.periodEnd) || b.periodId - a.periodId || b.versionNo - a.versionNo);
          this.selectPayroll();
          this.payrollState = 'ready';
        },
        error: error => { this.payrollState = error.status === 403 ? 'denied' : 'error'; }
      }));
  }

  seriesMax(series: typeof this.chartSeries.work | typeof this.chartSeries.movement): number {
    return this.axisMax(...this.months.flatMap(month => series.map(item => month[item.key])));
  }

  readonly payrollColors = { net: '#1963a5', deductions: '#d38b13' };

  get payrollGradient(): string {
    const totals = this.payroll?.totals;
    const net = Number(totals?.netPayroll);
    const deductions = Number(totals?.deductions);
    const gross = Number(totals?.grossPayroll);
    if (!Number.isFinite(gross) || !Number.isFinite(net) || !Number.isFinite(deductions)
        || gross <= 0 || net < 0 || deductions < 0 || Math.abs(net + deductions - gross) > 0.01) {
      return 'var(--track)';
    }
    const boundary = Math.min(100, net / gross * 100);
    return `conic-gradient(${this.payrollColors.net} 0% ${boundary}%, ${this.payrollColors.deductions} ${boundary}% 100%)`;
  }

  payrollPercent(value: string): number | null {
    const gross = Number(this.payroll?.totals.grossPayroll);
    return gross > 0 ? Number(value) / gross * 100 : null;
  }

  money(value: string): string {
    return formatMoney(value.replace(/\.0+$/, '')) + ' ₫';
  }

  get loading(): boolean { return [this.employeeState, this.contractState, this.workState, ...(this.isAdmin || this.isHr ? [this.pendingState] : [])].includes('loading'); }
  get departments(): GroupRow[] { return [...(this.employeeReport?.byDepartment || [])].sort((a, b) => b.employeeCount - a.employeeCount); }
  get departmentMax(): number { return this.axisMax(this.departments.reduce((max, row) => Math.max(max, row.employeeCount), 0)); }
  private pollAttendanceStatus(): void {
    this.requests.add(timer(0, 15000).pipe(
      filter(() => !document.hidden),
      exhaustMap(() => this.api.getAttendanceStatus().pipe(catchError(() => {
        this.attendanceState = 'error';
        return EMPTY;
      }))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(data => {
      this.attendanceStatus = data;
      this.attendanceState = 'ready';
    }));
  }

  get statusItems(): StatusItem[] {
    const status = this.attendanceStatus;
    return [
      { label: 'Đi làm', count: status?.present ?? 0, color: '#194e96' },
      { label: 'Vắng', count: status?.absent ?? 0, color: '#ce3050' },
      { label: 'Nghỉ phép', count: status?.leave ?? 0, color: '#8d398c' },
      { label: 'Có lịch nhưng thiếu check-in/check-out', count: status?.missing ?? 0, color: '#d38b13' }
    ];
  }
  get totalEmployees(): number { return this.statusItems.reduce((total, row) => total + row.count, 0); }
  get activeEmployees(): number { return (this.employeeReport?.byStatus || []).find(row => row.groupCode === 'DANG_LAM')?.employeeCount || 0; }
  get statusGradient(): string {
    const total = this.totalEmployees;
    if (!total) return '#dbe7f1';
    let position = 0;
    const stops = this.statusItems.filter(row => row.count > 0).map(row => {
      const start = position; position += row.count / total * 100;
      return `${row.color} ${start}% ${position}%`;
    });
    return `conic-gradient(${stops.join(',')})`;
  }
  get workMax(): number { return this.axisMax(this.work?.confirmedWorkDays || 0, this.work?.confirmedAbsentDays || 0); }
  get contractMax(): number { return this.axisMax(this.contracts?.effectiveCount || 0, this.contracts?.expiringCount || 0); }
  ticks(max: number): number[] { return [max, max * .75, max * .5, max * .25, 0]; }
  number(value: number): string { return new Intl.NumberFormat(this.language.current() === 'en' ? 'en-US' : 'vi-VN', { maximumFractionDigits: 2 }).format(value); }
  date(value: string): string { const [year, month, day] = value.split('-'); return `${day}/${month}/${year}`; }
  private axisMax(...values: number[]): number { return Math.max(4, Math.ceil(Math.max(0, ...values) / 4) * 4); }
  private failAll(message: string): void {
    this.attendanceState = 'error';
    this.trendsState = 'error';
    this.payrollState = 'error';
    this.employeeError = this.contractError = this.workError = message;
    this.employeeState = this.contractState = this.workState = 'error';
  }
  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 403) return 'Bạn chưa có quyền xem số liệu tổng quan.';
    if (error instanceof HttpErrorResponse && error.status === 401) return 'Phiên đăng nhập đã hết hạn.';
    return 'Không tải được dữ liệu. Vui lòng thử lại.';
  }
}
