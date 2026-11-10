import {SeniorityReport} from '../../shared/seniority-report';
import {SearchAssistDirective} from '../../shared/search-assist.directive';
import {HrmNotice} from '../../shared/ui/notice';
import {PageHeader} from '../../shared/page-header';
import {DetailFocusDirective} from '../../shared/detail-focus.directive';
import {TranslatePipe} from '../../shared/translate.pipe';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, OnInit, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, from, map, mergeMap, toArray } from 'rxjs';
import { ContractDetail, ContractReport, EmployeeGroupBy, EmployeeMovement, EmployeeReport, EmployeeReportDetail, GroupRow, PayrollAmounts, PayrollBatch, PayrollEmployee, PayrollLine, PayrollReport, Report, ReportDepartment, ReportFilters, ReportPage, ReportService, ReportType, WorkReport } from '../../services/report.service';
import { formatMoney, sumMoney } from '../../services/report-format';
import { LanguageService } from '../../services/language.service';

interface ReportTab { id: ReportType; label: string; description: string; }
interface EmployeeGroup { key: EmployeeGroupBy; label: string; description: string; rows: GroupRow[]; max: number; total: number; }
interface EmployeeSelection { groupBy: EmployeeGroupBy; row: GroupRow; label: string; }
interface WorkMonth { label: string; fromDate: string; report: WorkReport; }
type PayrollMetric = keyof PayrollAmounts;
interface PayrollColumn { key: string; label: string; batches: PayrollBatch[]; }
interface PayrollCell { value: string | null; batches: PayrollBatch[]; }
interface PayrollRow { key: string; label: string; metric: PayrollMetric; cells: PayrollCell[]; total: string; }
interface PayrollSelection { rowKey: string; columnKey: string; label: string; batches: PayrollBatch[]; }

@Component({
  selector: 'app-reports',
  standalone: true,imports:[SearchAssistDirective,HrmNotice,PageHeader,DetailFocusDirective,TranslatePipe],
  templateUrl: './reports.html',
  styleUrl: './reports.css'
})
export class ReportsPage implements OnInit {
  @ViewChild('reportResults') private reportResults?: ElementRef<HTMLElement>;
  private api = inject(ReportService);
  private language = inject(LanguageService, { optional: true });
  private destroyRef = inject(DestroyRef);
  private viewRequest?: Subscription;
  private exportRequest?: Subscription;
  private extraRequests = new Subscription();
  private detailRequest?: Subscription;
  private departmentRequest?: Subscription;
  private employeeDetailRequest?: Subscription;
  private payrollEmployeeRequest?: Subscription;
  private payrollLineRequest?: Subscription;
  private appliedFilters?: ReportFilters;
  private submittedFilters?: ReportFilters;
  private readonly formatter = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 4 });

  readonly tabs: ReportTab[] = [
    { id: 'employees', label: 'Nhân sự', description: 'Cơ cấu nhân sự theo phòng ban, chức vụ, trạng thái và trình độ.' },
    { id: 'contracts', label: 'Hợp đồng', description: 'Tổng hợp hợp đồng có hiệu lực và sắp hết hạn.' },
    { id: 'work', label: 'Công & thời gian', description: 'Tổng hợp chấm công, nghỉ phép và tăng ca theo kỳ.' },
    { id: 'payroll', label: 'Quỹ lương', description: 'Tổng hợp các bảng lương đã chốt theo kỳ.' }
  ];
  selected: ReportType = 'employees';
  filters: ReportFilters = this.initialFilters();
  employeeReport: EmployeeReport | null = null;
  contractReport: ContractReport | null = null;
  workReport: WorkReport | null = null;
  payrollReport: PayrollReport | null = null;
  employeeGroups: EmployeeGroup[] = [];
  employeeReportDate = '';
  employeeSelection: EmployeeSelection | null = null;
  employeeDetailReveal: EmployeeSelection | null = null;
  employeeDetails: ReportPage<EmployeeReportDetail> | null = null;
  employeeDetailPage = 0;
  employeeDetailLoading = false;
  employeeDetailError = '';
  loading = false;
  loadingHeight = 0;
  exporting = false;
  error = '';
  exportError = '';
  departments: ReportDepartment[] = [];
  departmentsLoading = false;
  departmentsError = '';
  updatedAt: Date | null = null;
  previousReport: ContractReport | WorkReport | PayrollReport | null = null;
  previousFilters: ReportFilters | null = null;
  comparisonLoading = false;
  comparisonError = '';
  movements: EmployeeMovement[] = [];
  previousMovements: EmployeeMovement[] = [];
  movementLoading = false;
  movementError = '';
  workMonths: WorkMonth[] = [];
  workTrendLoading = false;
  workTrendError = '';
  detailKind: 'expiring' | 'expired' = 'expiring';
  details: ContractDetail[] = [];
  detailPage = 0;
  detailPages = 0;
  detailTotal = 0;
  detailLoading = false;
  detailError = '';
  expiredTotal: number | null = null;
  expiredCountError = '';
  readonly payrollMetricOptions: { key: PayrollMetric; label: string }[] = [
    { key: 'grossPayroll', label: 'Trước khấu trừ' },
    { key: 'allowances', label: 'Phụ cấp' },
    { key: 'bonuses', label: 'Thưởng' },
    { key: 'deductions', label: 'Khấu trừ' },
    { key: 'netPayroll', label: 'Thực nhận' }
  ];
  payrollGrouping: 'components' | 'periods' = 'components';
  payrollGranularity: 'month' | 'quarter' | 'year' = 'quarter';
  payrollMetric: PayrollMetric = 'netPayroll';
  payrollColumns: PayrollColumn[] = [];
  payrollRows: PayrollRow[] = [];
  payrollColumnTotals: string[] = [];
  payrollSelection: PayrollSelection | null = null;
  activePayrollBatch: PayrollBatch | null = null;
  payrollEmployees: ReportPage<PayrollEmployee> | null = null;
  payrollEmployeePage = 0;
  payrollEmployeeLoading = false;
  payrollEmployeeError = '';
  activePayrollEmployee: PayrollEmployee | null = null;
  payrollLines: PayrollLine[] = [];
  payrollLineLoading = false;
  payrollLineError = '';

  ngOnInit(): void {
    this.loadDepartments();
    this.load();
    const timer = setInterval(() => this.refreshIfIdle(), 5 * 60_000);
    this.destroyRef.onDestroy(() => clearInterval(timer));
  }

  loadDepartments(): void {
    this.departmentRequest?.unsubscribe();
    this.departmentsLoading = true;
    this.departmentsError = '';
    this.departmentRequest = this.api.departments().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: departments => { this.departments = departments; this.departmentsLoading = false; },
      error: () => { this.departmentsLoading = false; this.departmentsError = 'Không tải được danh sách phòng ban.'; }
    });
  }

  submit(event: Event): void { event.preventDefault(); this.load(); }

  get activeTab(): ReportTab { return this.tabs.find(tab => tab.id === this.selected)!; }
  get hasResult(): boolean { return !!(this.employeeReport || this.contractReport || this.workReport || this.payrollReport); }
  get appliedDepartmentId(): string { return this.appliedFilters?.departmentId ?? ''; }

  select(type: ReportType): void {
    if (type === this.selected) return;
    this.viewRequest?.unsubscribe();
    this.exportRequest?.unsubscribe();
    this.extraRequests.unsubscribe();
    this.extraRequests = new Subscription();
    this.detailRequest?.unsubscribe();
    this.selected = type;
    this.exporting = false;
    this.exportError = '';
    this.load();
  }

  load(preserveDetail = false): void {
    const detailKind = preserveDetail ? this.detailKind : 'expiring';
    const detailPage = preserveDetail ? this.detailPage : 0;
    const payrollSelection = preserveDetail ? this.payrollSelection : null;
    const employeeSelection = preserveDetail ? this.employeeSelection : null;
    const employeeDetailPage = preserveDetail ? this.employeeDetailPage : 0;
    const payrollBatch = preserveDetail ? this.activePayrollBatch : null;
    const payrollEmployeePage = preserveDetail ? this.payrollEmployeePage : 0;
    // Keep the results area at its current height while the next report loads.
    this.loadingHeight = this.reportResults?.nativeElement.offsetHeight ?? 0;
    this.viewRequest?.unsubscribe();
    this.exportRequest?.unsubscribe();
    this.extraRequests.unsubscribe();
    this.extraRequests = new Subscription();
    this.detailRequest?.unsubscribe();
    this.exporting = false;
    this.loading = false;
    this.clearResults();
    this.updatedAt = null;
    this.previousReport = null;
    this.previousFilters = null;
    this.comparisonLoading = false;
    this.comparisonError = '';
    this.movements = []; this.previousMovements = []; this.movementLoading = false; this.movementError = '';
    this.workMonths = []; this.workTrendLoading = false; this.workTrendError = '';
    this.details = []; this.detailPages = 0; this.detailTotal = 0; this.detailLoading = false; this.detailError = '';
    this.expiredTotal = null; this.expiredCountError = '';
    this.error = this.validate();
    this.exportError = '';
    if (this.error) return;

    const type = this.selected;
    const submittedFilters = { ...this.filters };
    const filters = { ...this.filters, departmentId: this.filters.departmentId.trim() };
    if (type === 'employees') {
      this.employeeReportDate = new Intl.DateTimeFormat('vi-VN', {
        timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric'
      }).format(new Date());
    }
    this.loading = true;
    this.viewRequest = this.api.get(type, filters).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (result: Report) => {
        this.loading = false;
        if (type === 'employees') {
          this.employeeReport = result as EmployeeReport;
          this.employeeGroups = [
            this.employeeGroup('department', 'Theo phòng ban', 'Phân bổ đội ngũ', this.employeeReport.byDepartment),
            this.employeeGroup('position', 'Theo chức vụ', 'Vai trò trong tổ chức', this.employeeReport.byPosition),
            this.employeeGroup('status', 'Theo trạng thái', 'Tình trạng làm việc', this.employeeReport.byStatus),
            this.employeeGroup('qualification', 'Theo trình độ', 'Trình độ chuyên môn chính', this.employeeReport.byQualification)
          ];
        } else if (type === 'contracts') {
          this.contractReport = result as ContractReport;
          // Preserve the effective server date when the user left reportDate blank.
          filters.reportDate = this.contractReport.reportDate;
        } else if (type === 'work') this.workReport = result as WorkReport;
        else {
          this.payrollReport = result as PayrollReport;
          this.buildPayrollMatrix(payrollSelection);
        }
        this.appliedFilters = filters;
        this.submittedFilters = submittedFilters;
        this.updatedAt = new Date();
        if (employeeSelection) {
          const group = this.employeeGroups.find(item => item.key === employeeSelection.groupBy);
          const row = group?.rows.find(item => item.groupId === employeeSelection.row.groupId && item.groupCode === employeeSelection.row.groupCode);
          if (group && row) this.loadEmployeeDetails(group.key, row, employeeDetailPage);
        }
        if (payrollBatch) {
          const batch = this.payrollDetailBatches.find(item => item.batchId === payrollBatch.batchId);
          if (batch) this.loadPayrollEmployees(batch, payrollEmployeePage);
        }
        if (type === 'employees') this.loadMovements(filters);
        else {
          this.loadComparison(type, filters);
          if (type === 'contracts') {
            this.loadDetails(detailKind, detailPage);
            this.extraRequests.add(this.api.contractDetails(filters,'expired',0).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
              next: result => this.expiredTotal=result.totalElements,
              error: () => this.expiredCountError='Không tải được số hợp đồng đã hết hạn.'
            }));
          }
          if (type === 'work') this.loadWorkTrend(filters);
        }
      },
      error: (error: unknown) => { this.loading = false; this.error = this.errorMessage(error); }
    });
  }

  export(): void {
    if (!this.appliedFilters || this.loading || this.exporting) return;
    this.exporting = true;
    this.exportError = '';
    const type = this.selected;
    this.exportRequest = this.api.export(type, this.appliedFilters).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: blob => {
        this.exporting = false;
        if (!blob.size || blob.type.split(';')[0].toLowerCase() !== 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet') {
          this.exportError = 'Không nhận được tệp Excel hợp lệ. Vui lòng kiểm tra API báo cáo.';
          return;
        }
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `${type}-report.xlsx`;
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      },
      error: (error: unknown) => { this.exporting = false; this.exportError = this.errorMessage(error); }
    });
  }

  number(value: number): string { return this.formatter.format(value); }

  private refreshIfIdle(): void {
    if (typeof document === 'undefined' || document.hidden || this.loading || this.exporting || !this.appliedFilters ||
        !this.submittedFilters || JSON.stringify(this.filters) !== JSON.stringify(this.submittedFilters) ||
        document.activeElement?.closest?.('.report-filters')) return;
    this.load(true);
  }

  get hired(): number { return this.movements.reduce((sum, row) => sum + row.hired, 0); }
  get terminated(): number { return this.movements.reduce((sum, row) => sum + row.terminated, 0); }
  get previousHired(): number { return this.previousMovements.reduce((sum, row) => sum + row.hired, 0); }
  get previousTerminated(): number { return this.previousMovements.reduce((sum, row) => sum + row.terminated, 0); }
  get workMax(): number { return Math.max(1, ...this.workMonths.map(row => row.report.approvedOvertimeHours)); }
  get previousContract(): ContractReport | null { return this.selected === 'contracts' ? this.previousReport as ContractReport | null : null; }
  get previousWork(): WorkReport | null { return this.selected === 'work' ? this.previousReport as WorkReport | null : null; }
  get previousPayroll(): PayrollReport | null { return this.selected === 'payroll' ? this.previousReport as PayrollReport | null : null; }
  updatedLabel(): string { return this.updatedAt ? new Intl.DateTimeFormat('vi-VN', {dateStyle:'short',timeStyle:'medium'}).format(this.updatedAt) : ''; }
  change(current: number, previous: number): string {
    const delta = current - previous;
    return `${delta > 0 ? '+' : ''}${this.number(delta)} ${this.language?.current() === 'en' ? 'compared with previous period' : 'so với kỳ trước'}`;
  }

  private previousPeriod(filters: ReportFilters): ReportFilters | null {
    const start = Date.parse(`${filters.fromDate}T00:00:00Z`), end = Date.parse(`${filters.toDate}T00:00:00Z`);
    if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return null;
    const startDate = new Date(start), endDate = new Date(end);
    const lastOfEndMonth = new Date(0);
    lastOfEndMonth.setUTCFullYear(endDate.getUTCFullYear(), endDate.getUTCMonth() + 1, 0);
    lastOfEndMonth.setUTCHours(0,0,0,0);
    const wholeMonths = startDate.getUTCDate() === 1 && endDate.getUTCDate() === lastOfEndMonth.getUTCDate();
    const monthCount = (endDate.getUTCFullYear()-startDate.getUTCFullYear())*12 + endDate.getUTCMonth()-startDate.getUTCMonth()+1;
    const days = Math.round((end - start) / 86400000) + 1;
    const previousEnd = start - 86400000;
    const monthStart = new Date(0);
    monthStart.setUTCFullYear(startDate.getUTCFullYear(),startDate.getUTCMonth()-monthCount,1);
    monthStart.setUTCHours(0,0,0,0);
    const previousStart = wholeMonths ? monthStart.getTime() : previousEnd - (days - 1) * 86400000;
    if (new Date(previousStart).getUTCFullYear() < 1) return null;
    const reportTime = filters.reportDate ? Date.parse(`${filters.reportDate}T00:00:00Z`) : NaN;
    let previousDate = '';
    if (Number.isFinite(reportTime)) {
      const original = new Date(reportTime);
      const shifted = new Date(reportTime - days * 86400000);
      if (wholeMonths) {
        shifted.setUTCDate(1);
        shifted.setUTCFullYear(original.getUTCFullYear(),original.getUTCMonth()-monthCount,1);
        const last = new Date(shifted.getTime());
        last.setUTCMonth(last.getUTCMonth()+1,0);
        shifted.setUTCDate(Math.min(original.getUTCDate(),last.getUTCDate()));
      }
      if (shifted.getUTCFullYear() >= 1) previousDate = shifted.toISOString().slice(0,10);
    }
    return {...filters, fromDate: new Date(previousStart).toISOString().slice(0,10), toDate: new Date(previousEnd).toISOString().slice(0,10), reportDate: previousDate};
  }

  private loadComparison(type: Exclude<ReportType,'employees'>, filters: ReportFilters): void {
    const previous = this.previousPeriod(filters);
    if (!previous) { this.comparisonError = 'Không có kỳ trước hợp lệ để so sánh.'; return; }
    this.previousFilters = previous; this.comparisonLoading = true;
    this.extraRequests.add(this.api.get(type, previous).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: report => { this.previousReport = report as ContractReport | WorkReport | PayrollReport; this.comparisonLoading = false; },
      error: () => { this.comparisonLoading = false; this.comparisonError = 'Không tải được số liệu kỳ trước.'; }
    }));
  }

  private loadMovements(filters: ReportFilters): void {
    const previous = this.previousPeriod(filters);
    this.previousFilters = previous;
    this.movementLoading = true;
    this.extraRequests.add(this.api.movements(filters).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: data => { this.movements=data; this.movementLoading=false; },
      error: () => { this.movementLoading=false; this.movementError='Không tải được biến động nhân sự.'; }
    }));
    if (!previous) { this.comparisonError = 'Không có kỳ trước hợp lệ để so sánh.'; return; }
    this.comparisonLoading = true;
    this.extraRequests.add(this.api.movements(previous).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: data => { this.previousMovements=data; this.comparisonLoading=false; },
      error: () => { this.comparisonLoading=false; this.comparisonError='Không tải được số liệu kỳ trước.'; }
    }));
  }

  openEmployeeDetails(groupBy: EmployeeGroupBy, row: GroupRow): void {
    this.loadEmployeeDetails(groupBy, row);
    this.employeeDetailReveal = this.employeeSelection;
  }

  get employeeDetailFocus(): EmployeeSelection | null {
    return this.employeeDetailLoading ? null : this.employeeDetailReveal;
  }

  loadEmployeeDetails(groupBy: EmployeeGroupBy, row: GroupRow, page = 0): void {
    if (!this.appliedFilters || this.selected !== 'employees' || page < 0) return;
    this.employeeDetailRequest?.unsubscribe();
    this.employeeSelection = { groupBy, row, label: this.employeeGroupName(row) };
    this.employeeDetailPage = page;
    this.employeeDetails = null;
    this.employeeDetailLoading = true;
    this.employeeDetailError = '';
    this.employeeDetailRequest = this.api.employeeDetails(this.appliedFilters, groupBy, row, page)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: result => {
          if (result.totalPages > 0 && page >= result.totalPages) { this.loadEmployeeDetails(groupBy, row, result.totalPages - 1); return; }
          this.employeeDetails = result; this.employeeDetailLoading = false;
        },
        error: error => { this.employeeDetailLoading = false; this.employeeDetailError = this.errorMessage(error); }
      });
  }

  closeEmployeeDetails(): void {
    this.employeeDetailRequest?.unsubscribe();
    this.employeeSelection = null; this.employeeDetails = null; this.employeeDetailReveal = null;
    this.employeeDetailLoading = false; this.employeeDetailError = '';
  }

  loadPayrollEmployees(batch: PayrollBatch, page = 0): void {
    if (!this.appliedFilters || this.selected !== 'payroll' || page < 0) return;
    this.payrollEmployeeRequest?.unsubscribe();
    this.closePayrollLines();
    this.activePayrollBatch = batch; this.payrollEmployeePage = page;
    this.payrollEmployees = null; this.payrollEmployeeLoading = true; this.payrollEmployeeError = '';
    this.payrollEmployeeRequest = this.api.payrollEmployees(this.appliedFilters, batch.batchId, page)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: result => {
          if (result.totalPages > 0 && page >= result.totalPages) { this.loadPayrollEmployees(batch, result.totalPages - 1); return; }
          this.payrollEmployees = result; this.payrollEmployeeLoading = false;
        },
        error: error => { this.payrollEmployeeLoading = false; this.payrollEmployeeError = this.errorMessage(error); }
      });
  }

  loadPayrollLines(employee: PayrollEmployee): void {
    if (!this.appliedFilters || !this.activePayrollBatch) return;
    this.payrollLineRequest?.unsubscribe();
    this.activePayrollEmployee = employee;
    this.payrollLines = []; this.payrollLineLoading = true; this.payrollLineError = '';
    this.payrollLineRequest = this.api.payrollLines(this.appliedFilters, this.activePayrollBatch.batchId, employee.payrollDetailId)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: result => { this.payrollLines = result; this.payrollLineLoading = false; },
        error: error => { this.payrollLineLoading = false; this.payrollLineError = this.errorMessage(error); }
      });
  }

  closePayrollLines(): void {
    this.payrollLineRequest?.unsubscribe(); this.activePayrollEmployee = null;
    this.payrollLines = []; this.payrollLineLoading = false; this.payrollLineError = '';
  }

  closePayrollEmployees(): void {
    this.payrollEmployeeRequest?.unsubscribe(); this.closePayrollLines();
    this.activePayrollBatch = null; this.payrollEmployees = null;
    this.payrollEmployeeLoading = false; this.payrollEmployeeError = '';
  }

  loadDetails(kind: 'expiring' | 'expired', page = 0): void {
    if (!this.appliedFilters) return;
    this.detailRequest?.unsubscribe(); this.detailKind=kind; this.detailPage=page;
    this.detailLoading=true; this.detailError=''; this.details=[];
    this.detailRequest=this.api.contractDetails(this.appliedFilters,kind,page).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => {
        if(result.totalPages > 0 && page >= result.totalPages){this.loadDetails(kind,result.totalPages-1);return;}
        this.details=result.items;this.detailTotal=result.totalElements;this.detailPages=result.totalPages;this.detailLoading=false;
        if(kind==='expired'){this.expiredTotal=result.totalElements;this.expiredCountError='';}
      },
      error: () => {this.detailLoading=false;this.detailError='Không tải được danh sách hợp đồng.';}
    });
  }

  private loadWorkTrend(filters: ReportFilters): void {
    const start=new Date(`${filters.fromDate}T00:00:00Z`), end=new Date(`${filters.toDate}T00:00:00Z`);
    const monthCount=(end.getUTCFullYear()-start.getUTCFullYear())*12+end.getUTCMonth()-start.getUTCMonth()+1;
    if (monthCount>12) {this.workTrendError='Chọn khoảng tối đa 12 tháng để xem biểu đồ theo tháng.';return;}
    const periods: ReportFilters[]=[];
    for(let i=0;i<monthCount;i++){
      const first=new Date(0), last=new Date(0);
      first.setUTCFullYear(start.getUTCFullYear(),start.getUTCMonth()+i,1); first.setUTCHours(0,0,0,0);
      last.setUTCFullYear(start.getUTCFullYear(),start.getUTCMonth()+i+1,0); last.setUTCHours(0,0,0,0);
      periods.push({...filters,fromDate: first<start?filters.fromDate:first.toISOString().slice(0,10),toDate:last>end?filters.toDate:last.toISOString().slice(0,10)});
    }
    this.workTrendLoading=true;
    this.extraRequests.add(from(periods).pipe(mergeMap(period=>this.api.get('work',period).pipe(map(report=>({label:period.fromDate.slice(0,7),fromDate:period.fromDate,report:report as WorkReport}))),3),toArray(),takeUntilDestroyed(this.destroyRef)).subscribe({
      next: rows => {this.workMonths=rows.sort((a,b)=>a.fromDate.localeCompare(b.fromDate));this.workTrendLoading=false;},
      error: () => {this.workTrendLoading=false;this.workTrendError='Không tải được biểu đồ theo tháng.';}
    }));
  }

  get employeeTotal(): number {
    return this.employeeReport?.byStatus.reduce((total, row) => total + row.employeeCount, 0) ?? 0;
  }

  employeeStatus(code: string): number {
    return this.employeeReport?.byStatus.find(row => row.groupCode === code)?.employeeCount ?? 0;
  }

  employeeGroupName(row: GroupRow): string {
    const name = row.groupName || row.groupCode || '—';
    if (name === 'DANG_LAM') return this.language?.t('Đang làm việc') ?? 'Đang làm việc';
    if (name === 'DA_NGHI') return this.language?.t('Đã nghỉ việc') ?? 'Đã nghỉ việc';
    return name;
  }

  private employeeGroup(key: EmployeeGroupBy, label: string, description: string, rows: GroupRow[]): EmployeeGroup {
    const sorted = [...rows].sort((a, b) => b.employeeCount - a.employeeCount);
    return { key, label, description, rows: sorted, max: Math.max(0, ...sorted.map(row => row.employeeCount)),
      total: sorted.reduce((sum, row) => sum + row.employeeCount, 0) };
  }

  money(value: string): string { return formatMoney(value); }

  setPayrollGrouping(value: string): void {
    if (value !== 'components' && value !== 'periods') return;
    this.payrollGrouping = value;
    this.buildPayrollMatrix();
  }

  setPayrollGranularity(value: string): void {
    if (value !== 'month' && value !== 'quarter' && value !== 'year') return;
    this.payrollGranularity = value;
    this.buildPayrollMatrix();
  }

  setPayrollMetric(value: string): void {
    if (!this.payrollMetricOptions.some(option => option.key === value)) return;
    this.payrollMetric = value as PayrollMetric;
    this.buildPayrollMatrix();
  }

  get payrollMetricLabel(): string {
    return this.payrollMetricOptions.find(option => option.key === this.payrollMetric)!.label;
  }

  get payrollDetailBatches(): PayrollBatch[] {
    return this.payrollSelection?.batches ?? this.payrollReport?.batches ?? [];
  }

  get payrollMatrixTotal(): string {
    return sumMoney(this.payrollColumnTotals);
  }

  selectPayrollCell(row: PayrollRow, column: PayrollColumn, cell: PayrollCell): void {
    if (!cell.batches.length) return;
    this.closePayrollEmployees();
    this.payrollSelection = { rowKey: row.key, columnKey: column.key,
      label: `${row.label} · ${column.label}`, batches: cell.batches };
  }

  private buildPayrollMatrix(selection: PayrollSelection | null = null): void {
    this.closePayrollEmployees();
    const batches = [...(this.payrollReport?.batches ?? [])].sort((a, b) =>
      a.periodStart.localeCompare(b.periodStart) || a.periodId - b.periodId);
    const columns = new Map<string, PayrollColumn>();
    for (const batch of batches) {
      const [year, month] = batch.periodStart.split('-');
      const quarter = Math.ceil(Number(month) / 3);
      const key = this.payrollGranularity === 'year' ? year : this.payrollGranularity === 'quarter' ? `${year}-Q${quarter}` : `${year}-${month}`;
      const label = this.payrollGranularity === 'year' ? year : this.payrollGranularity === 'quarter' ? `Quý ${quarter}/${year}` : `${month}/${year}`;
      const column: PayrollColumn = columns.get(key) ?? { key, label, batches: [] };
      column.batches.push(batch);
      columns.set(key, column);
    }
    this.payrollColumns = [...columns.values()];
    const definitions: { key: string; label: string; metric: PayrollMetric; batchId?: number }[] =
      this.payrollGrouping === 'components'
        ? this.payrollMetricOptions.map(option => ({ key: option.key, label: option.label, metric: option.key }))
        : batches.map(batch => ({ key: `batch-${batch.batchId}`, label: `${batch.periodCode} · v${batch.versionNo}`,
            metric: this.payrollMetric, batchId: batch.batchId }));
    this.payrollRows = definitions.map(row => {
      const cells = this.payrollColumns.map(column => {
        const included = row.batchId === undefined ? column.batches : column.batches.filter(batch => batch.batchId === row.batchId);
        return { batches: included, value: included.length ? sumMoney(included.map(batch => batch.totals[row.metric])) : null };
      });
      return { key: row.key, label: row.label, metric: row.metric, cells,
        total: sumMoney(cells.flatMap(cell => cell.value === null ? [] : [cell.value])) };
    });
    this.payrollColumnTotals = this.payrollColumns.map(column => sumMoney(column.batches.map(batch => batch.totals[this.payrollMetric])));
    this.payrollSelection = null;
    if (selection) {
      const row = this.payrollRows.find(item => item.key === selection.rowKey);
      const index = this.payrollColumns.findIndex(column => column.key === selection.columnKey);
      if (row && index >= 0) this.selectPayrollCell(row, this.payrollColumns[index], row.cells[index]);
    }
  }

  date(value: string): string {
    const parts = value.split('-');
    return parts.length === 3 ? `${parts[2]}/${parts[1]}/${parts[0]}` : value;
  }

  private initialFilters(): ReportFilters {
    const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
    return { fromDate: `${today.slice(0, 8)}01`, toDate: today, reportDate: '', expiringWithinDays: 30, departmentId: '' };
  }

  private validate(): string {
    if (!this.filters.fromDate || !this.filters.toDate) return 'Vui lòng chọn khoảng thời gian báo cáo.';
    if (this.filters.fromDate > this.filters.toDate) return 'Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.';
    const lastDate = this.selected === 'payroll' ? '9999-12-31' : '9999-12-30';
    if (this.filters.fromDate < '0001-01-01' || this.filters.toDate > lastDate)
      return `Kỳ báo cáo phải nằm từ 01/01/0001 đến ${this.selected === 'payroll' ? '31' : '30'}/12/9999.`;
    if (this.selected === 'contracts') {
      if (!Number.isSafeInteger(this.filters.expiringWithinDays) || this.filters.expiringWithinDays < 0 || this.filters.expiringWithinDays > 3650)
        return 'Số ngày sắp hết hạn phải là số nguyên từ 0 đến 3650.';
      if (this.filters.reportDate) {
        const reportDate = Date.parse(`${this.filters.reportDate}T00:00:00Z`);
        if (!Number.isFinite(reportDate) || this.filters.reportDate < '0001-01-01' ||
            reportDate + this.filters.expiringWithinDays * 86400000 > Date.parse('9999-12-31T00:00:00Z'))
          return 'Ngày báo cáo và số ngày sắp hết hạn vượt khoảng ngày hợp lệ.';
      }
    }
    const id = this.filters.departmentId.trim();
    if (id && (!/^[1-9]\d*$/.test(id) || !Number.isSafeInteger(Number(id)))) return 'ID phòng ban phải là số nguyên dương.';
    return '';
  }

  private clearResults(): void {
    this.closeEmployeeDetails(); this.closePayrollEmployees();
    this.employeeReport = null; this.contractReport = null; this.workReport = null; this.payrollReport = null;
    this.employeeGroups = []; this.appliedFilters = undefined;
    this.payrollColumns = []; this.payrollRows = []; this.payrollColumnTotals = []; this.payrollSelection = null;
  }

  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 403) return this.selected === 'payroll'
        ? 'Bạn cần quyền REPORT_VIEW và PAYROLL_VIEW để xem báo cáo lương.'
        : 'Bạn cần quyền REPORT_VIEW để xem báo cáo này.';
      if (error.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
      if ([400, 404, 409].includes(error.status) && typeof error.error?.loi_chi_tiet === 'string') return error.error.loi_chi_tiet;
      if (error.status === 404) return 'Không tìm thấy dữ liệu hoặc API báo cáo. Hãy tải lại và thử lại.';
    }
    return 'Không tải được báo cáo. Hãy kiểm tra kết nối backend và thử lại.';
  }
}
