import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, OnInit, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { ContractReport, EmployeeReport, GroupRow, PayrollReport, Report, ReportFilters, ReportService, ReportType, WorkReport } from '../../services/report.service';
import { formatMoney } from '../../services/report-format';

interface ReportTab { id: ReportType; label: string; description: string; }
interface EmployeeGroup { label: string; rows: GroupRow[]; }

@Component({
  selector: 'app-reports',
  standalone: true,
  templateUrl: './reports.html',
  styleUrl: './reports.css'
})
export class ReportsPage implements OnInit {
  @ViewChild('reportResults') private reportResults?: ElementRef<HTMLElement>;
  private api = inject(ReportService);
  private destroyRef = inject(DestroyRef);
  private viewRequest?: Subscription;
  private exportRequest?: Subscription;
  private appliedFilters?: ReportFilters;
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
  loading = false;
  loadingHeight = 0;
  exporting = false;
  error = '';
  exportError = '';

  ngOnInit(): void { this.load(); }

  submit(event: Event): void { event.preventDefault(); this.load(); }

  get activeTab(): ReportTab { return this.tabs.find(tab => tab.id === this.selected)!; }
  get hasResult(): boolean { return !!(this.employeeReport || this.contractReport || this.workReport || this.payrollReport); }

  select(type: ReportType): void {
    if (type === this.selected) return;
    this.viewRequest?.unsubscribe();
    this.exportRequest?.unsubscribe();
    this.selected = type;
    this.exporting = false;
    this.exportError = '';
    this.load();
  }

  load(): void {
    // Keep the results area at its current height while the next report loads.
    this.loadingHeight = this.reportResults?.nativeElement.offsetHeight ?? 0;
    this.viewRequest?.unsubscribe();
    this.exportRequest?.unsubscribe();
    this.exporting = false;
    this.loading = false;
    this.clearResults();
    this.error = this.validate();
    this.exportError = '';
    if (this.error) return;

    const type = this.selected;
    const filters = { ...this.filters, departmentId: this.filters.departmentId.trim() };
    this.loading = true;
    this.viewRequest = this.api.get(type, filters).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (result: Report) => {
        this.loading = false;
        if (type === 'employees') {
          this.employeeReport = result as EmployeeReport;
          this.employeeGroups = [
            { label: 'Theo phòng ban', rows: this.employeeReport.byDepartment },
            { label: 'Theo chức vụ', rows: this.employeeReport.byPosition },
            { label: 'Theo trạng thái', rows: this.employeeReport.byStatus },
            { label: 'Theo trình độ', rows: this.employeeReport.byQualification }
          ];
        } else if (type === 'contracts') {
          this.contractReport = result as ContractReport;
          // Preserve the effective server date when the user left reportDate blank.
          filters.reportDate = this.contractReport.reportDate;
        } else if (type === 'work') this.workReport = result as WorkReport;
        else this.payrollReport = result as PayrollReport;
        this.appliedFilters = filters;
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

  money(value: string): string { return formatMoney(value); }

  date(value: string): string {
    const parts = value.split('-');
    return parts.length === 3 ? `${parts[2]}/${parts[1]}/${parts[0]}` : value;
  }

  private initialFilters(): ReportFilters {
    const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
    return { fromDate: `${today.slice(0, 8)}01`, toDate: today, reportDate: '', expiringWithinDays: 30, departmentId: '' };
  }

  private validate(): string {
    if (this.selected === 'employees') return '';
    if (!this.filters.fromDate || !this.filters.toDate) return 'Vui lòng chọn khoảng thời gian báo cáo.';
    if (this.filters.fromDate > this.filters.toDate) return 'Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.';
    if (this.selected !== 'contracts' && this.filters.toDate > '9999-12-30') return 'Ngày kết thúc không được sau 30/12/9999.';
    if (this.selected === 'contracts') {
      if (!Number.isSafeInteger(this.filters.expiringWithinDays) || this.filters.expiringWithinDays < 0 || this.filters.expiringWithinDays > 2147483647)
        return 'Số ngày sắp hết hạn phải là số nguyên từ 0 đến 2147483647.';
      const id = this.filters.departmentId.trim();
      if (id && (!/^[1-9]\d*$/.test(id) || !Number.isSafeInteger(Number(id))))
        return 'ID phòng ban phải là số nguyên dương.';
    }
    return '';
  }

  private clearResults(): void {
    this.employeeReport = null; this.contractReport = null; this.workReport = null; this.payrollReport = null;
    this.employeeGroups = []; this.appliedFilters = undefined;
  }

  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 403) return this.selected === 'payroll'
        ? 'Bạn cần quyền REPORT_VIEW và PAYROLL_VIEW để xem báo cáo lương.'
        : 'Bạn cần quyền REPORT_VIEW để xem báo cáo này.';
      if (error.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
      if (error.status === 404) return 'Không tìm thấy API báo cáo. Hãy kiểm tra backend và cấu hình kết nối.';
      if (error.status === 400 && typeof error.error?.loi_chi_tiet === 'string') return error.error.loi_chi_tiet;
    }
    return 'Không tải được báo cáo. Hãy kiểm tra kết nối backend và thử lại.';
  }
}
