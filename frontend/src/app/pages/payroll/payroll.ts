import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PayrollService } from '../../services/payroll.service';
import { Pagination } from '../../shared/pagination';
import { HrmNotice } from '../../shared/ui/notice';
import {
  PayrollPeriod,
  PayrollBatch,
  PayrollDetailView,
  EmployeePayrollWorkDetail,
  PeriodAllowanceOverview,
  EmployeePayrollHistoryItem,
  DepartmentPayrollCost,
  PayrollBlockerItem
} from '../../models/payroll.model';

@Component({
  selector: 'app-payroll',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, Pagination, HrmNotice],
  templateUrl: './payroll.html',
  styleUrls: ['./payroll.css']
})
export class PayrollPage implements OnInit {
  private payrollService = inject(PayrollService);

  activeTab: 'batch' | 'allowances' | 'employee-history' | 'department-summary' | 'rules' = 'batch';
  showGuide = true;
  showWorkDaysDropdown = false;

  periods: PayrollPeriod[] = [];
  selectedPeriodId: number | null = null;
  currentBatch: PayrollBatch | null = null;
  batches: PayrollBatch[] = [];
  details: PayrollDetailView[] = [];
  selectedDetail: PayrollDetailView | null = null;
  highlightedEmployeeId: number | null = null;

  // 1. Minh chứng ngày công khi bấm Chi tiết nhân viên
  selectedWorkDetail: EmployeePayrollWorkDetail | null = null;
  loadingWorkDetail = false;

  // 2. Phụ cấp theo tháng & phòng ban
  departments: Array<{ departmentId: number; departmentCode: string; departmentName: string }> = [];
  allowanceOverview: PeriodAllowanceOverview | null = null;
  selectedAllowanceDeptId: number | null = null;
  loadingAllowances = false;

  // 3. Bộ lọc & lịch sử nhân viên
  historyRows: EmployeePayrollHistoryItem[] = [];
  historyDeptId: number | null = null;
  historyKeyword = '';
  historyStatus = '';
  loadingHistory = false;

  // 4. Chi phí theo phòng ban
  departmentCosts: DepartmentPayrollCost[] = [];
  loadingDeptCosts = false;

  // 5. Cảnh báo & Danh sách vướng mắc chi tiết tới từng nhân viên
  blockers: PayrollBlockerItem[] = [];
  loading = false;
  actionMessage = '';
  errorMessage = '';

  filterKeyword = '';
  currentPage = 1;
  allowanceDeptPage = 1;
  allowancePage = 1;
  historyPage = 1;
  deptCostPage = 1;
  pageSize = 30;

  showAdjustModal = false;
  adjustmentReasonInput = '';

  ngOnInit(): void {
    this.loadDepartments();
    this.loadPeriods();
  }

  loadDepartments(): void {
    this.payrollService.getDepartments().subscribe({
      next: (list) => (this.departments = list || []),
      error: () => (this.departments = [])
    });
  }

  loadPeriods(): void {
    this.loading = true;
    this.payrollService.getPeriods().subscribe({
      next: (data) => {
        this.periods = data || [];
        if (this.periods.length > 0) {
          this.selectedPeriodId = Number(this.periods[0].periodId);
          this.onPeriodChange();
        } else {
          this.loading = false;
        }
      },
      error: (err) => {
        this.errorMessage = this.extractErrorMessage(err, 'Không thể tải danh sách kỳ lương.');
        this.loading = false;
      }
    });
  }

  switchTab(tab: 'batch' | 'allowances' | 'employee-history' | 'department-summary' | 'rules'): void {
    this.activeTab = tab;
    if (tab === 'allowances') {
      this.loadPeriodAllowances();
    } else if (tab === 'employee-history') {
      this.loadEmployeeHistory();
    } else if (tab === 'department-summary') {
      this.loadDepartmentCosts();
    }
  }

  onPeriodChange(): void {
    if (!this.selectedPeriodId) return;
    this.selectedPeriodId = Number(this.selectedPeriodId);
    this.currentPage = 1;
    this.filterKeyword = '';
    this.highlightedEmployeeId = null;
    this.checkPeriodData();

    if (this.activeTab === 'allowances') this.loadPeriodAllowances();
    if (this.activeTab === 'department-summary') this.loadDepartmentCosts();
  }

  checkPeriodData(): void {
    const periodId = Number(this.selectedPeriodId);
    if (!periodId) return;
    this.resetFeedback();
    this.closeDrawer();
    this.details = [];
    this.batches = [];
    this.currentBatch = null;
    this.loading = true;

    this.payrollService.getBatchesByPeriod(periodId).subscribe({
      next: (batches) => {
        if (Number(this.selectedPeriodId) !== periodId) return;
        this.batches = [...(batches || [])].sort((a, b) => b.versionNo - a.versionNo);
        const batch =
          this.batches[0] ??
          this.batches.find((item) => item.publishedAt || item.status === 'CONG_BO') ??
          this.batches.find((item) => item.status === 'DA_CHOT');

        if (batch) {
          this.selectBatch(batch.batchId);
        } else {
          this.loading = false;
        }
      },
      error: (err) => {
        if (Number(this.selectedPeriodId) !== periodId) return;
        this.loading = false;
        this.errorMessage = this.extractErrorMessage(err, 'Không thể tải các phiên bản bảng lương.');
      }
    });
  }

  get hasDraftBatch(): boolean {
    return this.batches.some((batch) => batch.status === 'NHAP');
  }

  selectBatch(batchId: number): void {
    const targetId = Number(batchId);
    const batch = this.batches.find((item) => item.batchId === targetId);
    if (!batch) return;
    this.resetFeedback();
    this.closeDrawer();
    this.currentBatch = batch;
    this.details = [];
    this.loading = true;
    this.loadBatchDetails(targetId);
  }

  setBatch(batch: PayrollBatch): void {
    this.currentBatch = batch;
    this.batches = [batch, ...this.batches.filter((item) => item.batchId !== batch.batchId)].sort(
      (a, b) => b.versionNo - a.versionNo
    );
  }

  loadBatchDetails(batchId: number): void {
    this.payrollService.getBatchDetails(batchId).subscribe({
      next: (list) => {
        if (this.currentBatch?.batchId !== batchId) return;
        this.details = list || [];
        this.currentPage = 1;
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = this.extractErrorMessage(err, 'Không thể tải bảng chi tiết lương.');
        this.loading = false;
      }
    });
  }

  loadDetails(batchId: number): void {
    this.loadBatchDetails(batchId);
  }

  get hasProvisionalDetails(): boolean {
    return this.details.some((detail) => (detail as any).provisional);
  }

  get hasCalculatedDetails(): boolean {
    return this.details.some((detail) => detail.lines && detail.lines.length > 0);
  }

  createDraft(): void {
    if (!this.selectedPeriodId) {
      this.errorMessage = 'Vui lòng chọn kỳ tính lương trước khi tạo đợt mới.';
      return;
    }
    this.loading = true;
    this.resetFeedback();
    this.payrollService.createDraftBatch(Number(this.selectedPeriodId)).subscribe({
      next: (batch) => {
        this.setBatch(batch);
        this.details = [];
        this.actionMessage = `Đã khởi tạo thành công đợt lương nháp #${batch.batchId} (Phiên bản v${batch.versionNo}). Hãy bấm "⚡ Tính tự động" để tổng hợp số liệu.`;
        this.loadBatchDetails(batch.batchId);
      },
      error: (err) => {
        this.handleActionError(err, 'Không thể tạo đợt lương nháp.');
      }
    });
  }

  calculate(forceDemo = false): void {
    if (!this.currentBatch) {
      this.errorMessage = 'Chưa có đợt lương nháp. Vui lòng bấm "+ Tạo đợt" trước.';
      return;
    }
    this.loading = true;
    this.resetFeedback();

    const calc$ =
      typeof (this.payrollService as any).calculateBatch === 'function'
        ? this.payrollService.calculateBatch(this.currentBatch.batchId, forceDemo)
        : (this.payrollService as any).calculatePayroll(this.currentBatch.batchId, forceDemo);

    calc$.subscribe({
      next: (msg: string) => {
        this.actionMessage = msg || `Tính lương tự động hoàn tất cho đợt #${this.currentBatch!.batchId}.`;
        this.loadBatchDetails(this.currentBatch!.batchId);
      },
      error: (err: any) => {
        this.handleActionError(err, 'Không thể tính lương do còn đơn từ / dữ liệu chấm công chưa duyệt.');
        this.fetchDetailedBlockers(this.currentBatch!.batchId);
      }
    });
  }

  inspectReadinessOnly(): void {
    if (!this.currentBatch) return;
    this.loading = true;
    this.resetFeedback();
    this.payrollService.checkBatchReadiness(this.currentBatch.batchId).subscribe({
      next: (report) => {
        this.blockers = report.blockers || [];
        if (report.readyForCalculate) {
          this.actionMessage = `✅ Kỳ ${report.periodCode} đã đủ toàn bộ điều kiện. Bạn có thể bấm "⚡ Tính tự động" và "🔒 Chốt" ngay!`;
        } else {
          this.errorMessage = report.summaryMessage;
        }
        this.loading = false;
      },
      error: (err) => this.handleActionError(err, 'Không thể kiểm tra điều kiện tính lương.')
    });
  }

  lock(forceDemo = false): void {
    if (!this.currentBatch) return;
    if (
      !forceDemo &&
      !confirm(
        `Xác nhận CHỐT bảng lương đợt #${this.currentBatch.batchId} (v${this.currentBatch.versionNo})?\nSau khi chốt, số liệu sẽ khóa cứng bất biến theo quy tắc kế toán.`
      )
    ) {
      return;
    }
    this.loading = true;
    this.resetFeedback();

    this.payrollService.lockBatch(this.currentBatch.batchId, forceDemo).subscribe({
      next: (batch) => {
        this.setBatch(batch);
        this.actionMessage = `🔒 Đã CHỐT thành công bảng lương đợt #${batch.batchId} (v${batch.versionNo}). Bạn có thể bấm "Công bố" để phát hành phiếu lương cho nhân viên.`;
        this.loading = false;
      },
      error: (err) => {
        this.handleActionError(err, 'Không thể chốt bảng lương do còn đơn từ chưa hoàn tất.');
        this.fetchDetailedBlockers(this.currentBatch!.batchId);
      }
    });
  }

  publish(): void {
    if (this.loading || !this.currentBatch || this.currentBatch.status !== 'DA_CHOT' || this.currentBatch.publishedAt) {
      return;
    }
    if (!confirm('Xác nhận CÔNG BỐ bảng lương và phát hành phiếu lương chính thức cho toàn bộ nhân viên?')) {
      return;
    }
    this.loading = true;
    this.resetFeedback();

    this.payrollService.publishBatch(this.currentBatch.batchId).subscribe({
      next: (batch) => {
        const updatedBatch = { ...batch, status: 'CONG_BO' as const };
        this.setBatch(updatedBatch);
        this.actionMessage = `🎉 Đã CÔNG BỐ bảng lương đợt #${batch.batchId} và phát hành phiếu lương cá nhân cho toàn bộ nhân viên.`;
        this.loadBatchDetails(batch.batchId);
      },
      error: (err) => {
        this.handleActionError(err, 'Không thể công bố bảng lương.');
      }
    });
  }

  submitAdjustment(): void {
    if (!this.currentBatch) return;
    const reason = this.adjustmentReasonInput.trim();
    if (!reason) {
      this.errorMessage = 'Bắt buộc nhập lý do giải trình khi tạo phiên bản điều chỉnh!';
      return;
    }
    this.loading = true;
    this.resetFeedback();

    const request$ =
      typeof (this.payrollService as any).createAdjustmentBatch === 'function'
        ? this.payrollService.createAdjustmentBatch(this.currentBatch.batchId, reason)
        : (this.payrollService as any).createAdjustment(this.currentBatch.batchId, reason);

    request$.subscribe({
      next: (newBatch: PayrollBatch) => {
        this.setBatch(newBatch);
        this.details = [];
        this.showAdjustModal = false;
        this.adjustmentReasonInput = '';
        this.actionMessage = `Đã tạo phiên bản điều chỉnh v${newBatch.versionNo} (trạng thái NHÁP). Hãy bấm "⚡ Tính tự động" để cập nhật số liệu mới.`;
        this.loadBatchDetails(newBatch.batchId);
      },
      error: (err: any) => {
        this.showAdjustModal = false;
        this.handleActionError(err, 'Không thể tạo phiên bản điều chỉnh.');
      }
    });
  }

  viewDetail(item: PayrollDetailView): void {
    this.selectedDetail = item;
    this.highlightedEmployeeId = item.employeeId;
    this.selectedWorkDetail = null;
    this.showWorkDaysDropdown = false;
    if (!this.currentBatch) return;

    this.loadingWorkDetail = true;
    this.payrollService.getEmployeeWorkDetails(this.currentBatch.batchId, item.employeeId).subscribe({
      next: (workDetail) => {
        this.selectedWorkDetail = workDetail;
        this.loadingWorkDetail = false;
      },
      error: () => {
        this.loadingWorkDetail = false;
      }
    });
  }

  toggleWorkDaysDropdown(event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.showWorkDaysDropdown = !this.showWorkDaysDropdown;
  }

  jumpToBlockerEmployee(blocker: PayrollBlockerItem): void {
    if (!blocker.employeeId && !blocker.employeeCode) return;
    this.highlightedEmployeeId = blocker.employeeId;
    const target = this.details.find(
      (d) =>
        (blocker.employeeId != null && d.employeeId === blocker.employeeId) ||
        (blocker.employeeCode && d.employeeCode === blocker.employeeCode)
    );
    if (target) {
      this.filterKeyword = target.employeeCode || target.employeeName || '';
      this.currentPage = 1;
      this.viewDetail(target);
    } else if (blocker.employeeCode && blocker.employeeCode !== 'HỆ THỐNG') {
      this.filterKeyword = blocker.employeeCode;
      this.currentPage = 1;
    }
    const el = document.getElementById('payroll-main-table');
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  getEmployeeBlockers(employeeId: number): PayrollBlockerItem[] {
    if (!this.blockers || this.blockers.length === 0) return [];
    return this.blockers.filter((b) => b.employeeId === employeeId);
  }

  closeDrawer(): void {
    this.selectedDetail = null;
    this.selectedWorkDetail = null;
    this.showWorkDaysDropdown = false;
  }

  printPayslip(item: PayrollDetailView): void {
    if (!this.currentBatch?.publishedAt && this.currentBatch?.status !== 'CONG_BO') {
      this.errorMessage = 'Phiếu lương chỉ khả dụng sau khi đợt lương đã được Chốt và Công bố.';
      return;
    }

    const req$ =
      item.payslipId && typeof (this.payrollService as any).getPayslipHtml === 'function'
        ? this.payrollService.getPayslipHtml(item.payslipId)
        : item.payrollDetailId != null && typeof (this.payrollService as any).getPayslipHtmlByPayrollDetail === 'function'
          ? this.payrollService.getPayslipHtmlByPayrollDetail(item.payrollDetailId)
          : null;

    if (!req$) {
      this.errorMessage = 'Chưa có thông tin phiếu lương để in.';
      return;
    }

    const printWindow = window.open('', '_blank');
    if (!printWindow) {
      this.errorMessage = 'Trình duyệt đang chặn cửa sổ in.';
      return;
    }

    req$.subscribe({
      next: (html: string) => {
        printWindow.document.open();
        printWindow.document.write(html);
        printWindow.document.close();
      },
      error: () => {
        printWindow.close();
        this.errorMessage = 'Chưa tìm thấy phiếu lương đã phát hành cho phiên bản này.';
      }
    });
  }

  loadPeriodAllowances(): void {
    if (!this.selectedPeriodId) return;
    this.loadingAllowances = true;
    this.allowanceDeptPage = 1;
    this.allowancePage = 1;
    this.payrollService.getPeriodAllowances(Number(this.selectedPeriodId), this.selectedAllowanceDeptId).subscribe({
      next: (data) => {
        this.allowanceOverview = data;
        this.loadingAllowances = false;
      },
      error: (err) => {
        this.errorMessage = this.extractErrorMessage(err, 'Không thể tải danh sách phụ cấp trong kỳ.');
        this.loadingAllowances = false;
      }
    });
  }

  filterAllowanceByDept(deptId: number | null): void {
    this.selectedAllowanceDeptId = deptId;
    this.allowancePage = 1;
    this.loadPeriodAllowances();
  }

  loadEmployeeHistory(): void {
    this.loadingHistory = true;
    this.historyPage = 1;
    this.payrollService
      .getEmployeePayrollHistory({
        departmentId: this.historyDeptId,
        keyword: this.historyKeyword,
        status: this.historyStatus
      })
      .subscribe({
        next: (rows) => {
          this.historyRows = rows || [];
          this.historyPage = 1;
          this.loadingHistory = false;
        },
        error: (err) => {
          this.errorMessage = this.extractErrorMessage(err, 'Không thể tải lịch sử lương nhân viên.');
          this.loadingHistory = false;
        }
      });
  }

  resetHistoryFilters(): void {
    this.historyDeptId = null;
    this.historyKeyword = '';
    this.historyStatus = '';
    this.historyPage = 1;
    this.loadEmployeeHistory();
  }

  openHistoryItemInBatch(row: EmployeePayrollHistoryItem): void {
    this.selectedPeriodId = Number(row.periodId);
    this.activeTab = 'batch';
    this.filterKeyword = row.employeeCode || '';
    this.currentPage = 1;
    this.highlightedEmployeeId = row.employeeId;
    this.payrollService.getBatchesByPeriod(Number(row.periodId)).subscribe({
      next: (batches) => {
        this.batches = [...(batches || [])].sort((a, b) => b.versionNo - a.versionNo);
        const targetBatch = this.batches.find((b) => b.batchId === row.batchId) ?? this.batches[0];
        if (targetBatch) {
          this.currentBatch = targetBatch;
          this.payrollService.getBatchDetails(targetBatch.batchId).subscribe({
            next: (list) => {
              this.details = list || [];
              const empDetail = this.details.find((d) => d.employeeId === row.employeeId);
              if (empDetail) {
                this.viewDetail(empDetail);
              }
            }
          });
        }
      }
    });
  }

  loadDepartmentCosts(): void {
    if (!this.selectedPeriodId) return;
    this.loadingDeptCosts = true;
    this.deptCostPage = 1;
    this.payrollService.getDepartmentCostSummary(Number(this.selectedPeriodId)).subscribe({
      next: (rows) => {
        this.departmentCosts = rows || [];
        this.deptCostPage = 1;
        this.loadingDeptCosts = false;
      },
      error: (err) => {
        this.errorMessage = this.extractErrorMessage(err, 'Không thể tải thống kê chi phí phòng ban.');
        this.loadingDeptCosts = false;
      }
    });
  }

  private fetchDetailedBlockers(batchId: number): void {
    this.payrollService.checkBatchReadiness(batchId).subscribe({
      next: (report) => {
        this.blockers = report.blockers || [];
        if (report.summaryMessage) {
          this.errorMessage = report.summaryMessage;
        }
      },
      error: () => {}
    });
  }

  private handleActionError(err: any, fallback: string): void {
    this.errorMessage = this.extractErrorMessage(err, fallback);
    this.loading = false;
  }

  private extractErrorMessage(err: any, fallback: string): string {
    if (!err) return fallback;
    if (typeof err.error === 'string') {
      try {
        const parsed = JSON.parse(err.error);
        return parsed.message || parsed.loi_chi_tiet || err.error;
      } catch {
        return err.error || fallback;
      }
    }
    return err.error?.message || err.error?.loi_chi_tiet || err.message || fallback;
  }

  private resetFeedback(): void {
    this.actionMessage = '';
    this.errorMessage = '';
    this.blockers = [];
  }

  get filteredDetails(): PayrollDetailView[] {
    const kw = this.filterKeyword.trim().toLowerCase();
    if (!kw) return this.details;
    return this.details.filter(
      (d) =>
        (d.employeeCode || `NV-${d.employeeId}`).toLowerCase().includes(kw) ||
        (d.employeeName || '').toLowerCase().includes(kw) ||
        (d.departmentName || '').toLowerCase().includes(kw)
    );
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredDetails.length / this.pageSize));
  }

  get pagedDetails(): PayrollDetailView[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredDetails.slice(start, start + this.pageSize);
  }

  get allowanceDeptTotalPages(): number {
    const list = this.allowanceOverview?.departmentSummaries || [];
    return Math.max(1, Math.ceil(list.length / this.pageSize));
  }

  get pagedAllowanceDepts(): NonNullable<PeriodAllowanceOverview['departmentSummaries']> {
    const list = this.allowanceOverview?.departmentSummaries || [];
    const start = (this.allowanceDeptPage - 1) * this.pageSize;
    return list.slice(start, start + this.pageSize);
  }

  get allowanceTotalPages(): number {
    const list = this.allowanceOverview?.items || [];
    return Math.max(1, Math.ceil(list.length / this.pageSize));
  }

  get pagedAllowanceItems(): NonNullable<PeriodAllowanceOverview['items']> {
    const list = this.allowanceOverview?.items || [];
    const start = (this.allowancePage - 1) * this.pageSize;
    return list.slice(start, start + this.pageSize);
  }

  get historyTotalPages(): number {
    return Math.max(1, Math.ceil(this.historyRows.length / this.pageSize));
  }

  get pagedHistoryRows(): EmployeePayrollHistoryItem[] {
    const start = (this.historyPage - 1) * this.pageSize;
    return this.historyRows.slice(start, start + this.pageSize);
  }

  get deptCostTotalPages(): number {
    return Math.max(1, Math.ceil(this.departmentCosts.length / this.pageSize));
  }

  get pagedDepartmentCosts(): DepartmentPayrollCost[] {
    const start = (this.deptCostPage - 1) * this.pageSize;
    return this.departmentCosts.slice(start, start + this.pageSize);
  }

  get totalGross(): number {
    return this.details.reduce((sum, item) => sum + (item.totalGross || 0), 0);
  }

  get totalDeduction(): number {
    return this.details.reduce((sum, item) => sum + (item.totalDeduction || 0), 0);
  }

  get totalNet(): number {
    return this.details.reduce((sum, item) => sum + (item.netSalary || 0), 0);
  }

  get historyTotalGross(): number {
    return this.historyRows.reduce((s, r) => s + (r.totalGross || 0), 0);
  }

  get historyTotalDeduction(): number {
    return this.historyRows.reduce((s, r) => s + (r.totalDeduction || 0), 0);
  }

  get historyTotalNet(): number {
    return this.historyRows.reduce((s, r) => s + (r.netSalary || 0), 0);
  }

  get deptTotalGross(): number {
    return this.departmentCosts.reduce((s, r) => s + (r.totalGross || 0), 0);
  }

  get deptTotalDeduction(): number {
    return this.departmentCosts.reduce((s, r) => s + (r.totalDeduction || 0), 0);
  }

  get deptTotalNet(): number {
    return this.departmentCosts.reduce((s, r) => s + (r.totalNet || 0), 0);
  }
}
