import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { LanguageService } from '../../services/language.service';
import { ContractReport, EmployeeReport, GroupRow, ReportFilters, ReportService, WorkReport } from '../../services/report.service';

type LoadState = 'loading' | 'ready' | 'error';
interface StatusItem { label: string; count: number; color: string; }

@Component({
  selector: 'app-overview', standalone: true, imports: [RouterLink],
  templateUrl: './overview.html', styleUrl: './overview.css'
})
export class OverviewPage implements OnInit {
  language = inject(LanguageService);
  private api = inject(ReportService);
  private auth = inject(AuthService);
  private destroyRef = inject(DestroyRef);
  private requests = new Subscription();
  private readonly colors = ['#2c78ba', '#71bca9', '#e9b969', '#a28ac7', '#dc8f81', '#80a3c5'];

  today = '';
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

  ngOnInit(): void { this.load(); }

  load(): void {
    this.requests.unsubscribe();
    this.requests = new Subscription();
    this.today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
    this.fromDate = `${this.today.slice(0, 8)}01`;
    this.filters = { fromDate: this.fromDate, toDate: this.today, reportDate: this.today, expiringWithinDays: 30, departmentId: '' };
    this.employeeReport = null; this.contracts = null; this.work = null;
    this.employeeState = this.contractState = this.workState = 'loading';
    this.employeeError = this.contractError = this.workError = '';
    this.requests.add(this.auth.loadCurrentUser().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: profile => {
        if (profile.role !== 'ADMIN') {
          this.failAll('Số liệu tổng quan cho HR đang chờ giới hạn theo phòng ban.');
          return;
        }
        this.loadReports();
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

  get loading(): boolean { return [this.employeeState, this.contractState, this.workState].includes('loading'); }
  get departments(): GroupRow[] { return [...(this.employeeReport?.byDepartment || [])].sort((a, b) => b.employeeCount - a.employeeCount); }
  get departmentMax(): number { return this.axisMax(this.departments.reduce((max, row) => Math.max(max, row.employeeCount), 0)); }
  get statusItems(): StatusItem[] {
    return (this.employeeReport?.byStatus || []).map((row, index) => ({
      label: row.groupCode === 'DANG_LAM' ? 'Đang làm việc' : row.groupCode === 'DA_NGHI' ? 'Đã nghỉ việc' : (row.groupName || row.groupCode || 'Chưa xác định'),
      count: row.employeeCount, color: this.colors[index % this.colors.length]
    }));
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
    this.employeeError = this.contractError = this.workError = message;
    this.employeeState = this.contractState = this.workState = 'error';
  }
  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 403) return 'Bạn chưa có quyền xem số liệu báo cáo.';
    if (error instanceof HttpErrorResponse && error.status === 401) return 'Phiên đăng nhập đã hết hạn.';
    return 'Không tải được dữ liệu. Vui lòng thử lại.';
  }
}
