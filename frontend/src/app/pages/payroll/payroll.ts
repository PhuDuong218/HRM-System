import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { PayrollService } from '../../services/payroll.service';
import { Pagination } from '../../shared/pagination';
import { HrmNotice } from '../../shared/ui/notice';
import {
  PayrollPeriod,
  PayrollBatch,
  PayrollDetailView,
  EmployeePayrollWorkDetail,
  PeriodAllowanceOverview,
  PeriodAllowanceItem,
  DepartmentAllowanceSummary,
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

  // 2. Phụ cấp theo tháng & phòng ban (kèm bộ lọc Năm, Quý, Nhân viên & Subform tại chỗ)
  departments: Array<{ departmentId: number; departmentCode: string; departmentName: string }> = [];
  allowanceOverview: PeriodAllowanceOverview | null = null;
  selectedAllowanceDeptId: number | null = null;
  allowanceYear: number | null = null;
  allowanceQuarter: number | null = null;
  allowancePeriodMode: number | 'ALL' = 'ALL';
  allowanceEmployeeCode = '';
  allowanceKeyword = '';
  allowanceMonthsNotice = '';
  allowanceActivePeriodCodes: string[] = [];
  loadingAllowances = false;

  selectedAllowanceItem: PeriodAllowanceItem | null = null;
  allowanceSubformDetail: PayrollDetailView | null = null;
  allowanceSubformWorkDetail: EmployeePayrollWorkDetail | null = null;
  showAllowanceWorkDropdown = false;
  loadingAllowanceSubform = false;

  // 3. Lịch sử lương nhân viên (kèm bộ lọc Năm, Quý, Tháng, Nhân viên & Subform tại chỗ)
  allHistoryRows: EmployeePayrollHistoryItem[] = [];
  historyRows: EmployeePayrollHistoryItem[] = [];
  historyYear: number | null = null;
  historyQuarter: number | null = null;
  historyPeriodCode = '';
  historyDeptId: number | null = null;
  historyEmployeeCode = '';
  historyKeyword = '';
  historyStatus = '';
  historyMonthsNotice = '';
  historyActivePeriodCodes: string[] = [];
  loadingHistory = false;

  selectedHistoryRow: EmployeePayrollHistoryItem | null = null;
  historySelectedDetail: PayrollDetailView | null = null;
  historySelectedWorkDetail: EmployeePayrollWorkDetail | null = null;
  showHistoryWorkDropdown = false;
  loadingHistorySubform = false;

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
          this.allowancePeriodMode = Number(this.periods[0].periodId);
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
    if (!this.periods || this.periods.length === 0) return;
    this.loadingAllowances = true;
    this.allowanceDeptPage = 1;
    this.allowancePage = 1;

    const targetPeriods = this.resolveAllowanceTargetPeriods();
    this.allowanceActivePeriodCodes = targetPeriods.map((p) => p.periodCode);

    if (targetPeriods.length === 0) {
      this.allowanceOverview = null;
      this.loadingAllowances = false;
      return;
    }

    if (targetPeriods.length === 1) {
      const target = targetPeriods[0];
      this.payrollService.getPeriodAllowances(Number(target.periodId), this.selectedAllowanceDeptId).subscribe({
        next: (data) => {
          if (data && data.items) {
            data.items = data.items.map((it) => ({
              ...it,
              periodId: data.periodId,
              periodCode: data.periodCode
            }));
          }
          this.allowanceOverview = data;
          if (this.selectedAllowanceItem) {
            const refreshed = (data?.items || []).find(
              (x) =>
                x.employeeAllowanceId === this.selectedAllowanceItem!.employeeAllowanceId &&
                x.employeeId === this.selectedAllowanceItem!.employeeId
            );
            this.selectedAllowanceItem = refreshed || null;
          }
          this.loadingAllowances = false;
        },
        error: (err) => {
          this.errorMessage = this.extractErrorMessage(err, 'Không thể tải danh sách phụ cấp trong kỳ.');
          this.loadingAllowances = false;
        }
      });
      return;
    }

    // Tổng hợp phụ cấp của nhiều tháng khi lọc theo Quý / Năm
    const calls = targetPeriods.map((p) =>
      this.payrollService.getPeriodAllowances(Number(p.periodId), this.selectedAllowanceDeptId).pipe(
        catchError(() =>
          of({
            periodId: p.periodId,
            periodCode: p.periodCode,
            periodStart: p.periodStart,
            periodEnd: p.periodEnd,
            totalCompanyAllowance: 0,
            departmentSummaries: [],
            items: []
          } as PeriodAllowanceOverview)
        )
      )
    );

    forkJoin(calls).subscribe({
      next: (overviews) => {
        this.allowanceOverview = this.mergeAllowanceOverviews(overviews, targetPeriods);
        this.loadingAllowances = false;
      },
      error: (err) => {
        this.errorMessage = this.extractErrorMessage(err, 'Không thể tổng hợp danh sách phụ cấp theo quý/năm.');
        this.loadingAllowances = false;
      }
    });
  }

  onAllowanceYearOrQuarterChange(): void {
    // Khi người dùng chọn Quý hoặc Năm thì chuyển sang tổng hợp tất cả các tháng hiện có trong Quý/Năm đó
    if (this.allowanceYear !== null || this.allowanceQuarter !== null) {
      this.allowancePeriodMode = 'ALL';
    } else if (this.selectedPeriodId) {
      this.allowancePeriodMode = Number(this.selectedPeriodId);
    }
    this.selectedAllowanceItem = null;
    this.loadPeriodAllowances();
  }

  onAllowancePeriodModeChange(): void {
    if (this.allowancePeriodMode !== 'ALL') {
      this.selectedPeriodId = Number(this.allowancePeriodMode);
    }
    this.selectedAllowanceItem = null;
    this.loadPeriodAllowances();
  }

  resetAllowanceFilters(): void {
    this.allowanceYear = null;
    this.allowanceQuarter = null;
    this.selectedAllowanceDeptId = null;
    this.allowanceEmployeeCode = '';
    this.allowanceKeyword = '';
    this.allowanceMonthsNotice = '';
    this.selectedAllowanceItem = null;
    if (this.periods.length > 0) {
      this.allowancePeriodMode = Number(this.selectedPeriodId || this.periods[0].periodId);
    }
    this.loadPeriodAllowances();
  }

  private resolveAllowanceTargetPeriods(): PayrollPeriod[] {
    this.allowanceMonthsNotice = '';
    if (this.allowancePeriodMode !== 'ALL') {
      const single = this.periods.find((p) => Number(p.periodId) === Number(this.allowancePeriodMode));
      return single ? [single] : this.periods.slice(0, 1);
    }

    const { matched, fallbackNotice } = this.filterPeriodsByYearAndQuarter(
      this.periods,
      this.allowanceYear,
      this.allowanceQuarter
    );
    this.allowanceMonthsNotice = fallbackNotice;
    return matched;
  }

  private mergeAllowanceOverviews(
    overviews: PeriodAllowanceOverview[],
    targetPeriods: PayrollPeriod[]
  ): PeriodAllowanceOverview {
    let totalCompany = 0;
    const allItems: PeriodAllowanceItem[] = [];
    const deptMap = new Map<
      number,
      {
        departmentId: number;
        departmentCode: string;
        departmentName: string;
        empSet: Set<number>;
        allowanceCount: number;
        totalAmount: number;
        byType: Record<string, number>;
      }
    >();

    for (const ov of overviews) {
      if (!ov) continue;
      totalCompany += ov.totalCompanyAllowance || 0;
      for (const it of ov.items || []) {
        allItems.push({
          ...it,
          periodId: ov.periodId,
          periodCode: ov.periodCode
        });
        let dEntry = deptMap.get(it.departmentId);
        if (!dEntry) {
          const ds = (ov.departmentSummaries || []).find((d) => d.departmentId === it.departmentId);
          dEntry = {
            departmentId: it.departmentId,
            departmentCode: ds?.departmentCode || `PB-${it.departmentId}`,
            departmentName: it.departmentName,
            empSet: new Set<number>(),
            allowanceCount: 0,
            totalAmount: 0,
            byType: {}
          };
          deptMap.set(it.departmentId, dEntry);
        }
        dEntry.empSet.add(it.employeeId);
        dEntry.allowanceCount += 1;
        dEntry.totalAmount += it.estimatedPeriodAmount || 0;
        dEntry.byType[it.allowanceName] =
          (dEntry.byType[it.allowanceName] || 0) + (it.estimatedPeriodAmount || 0);
      }
    }

    const deptSummaries: DepartmentAllowanceSummary[] = Array.from(deptMap.values()).map((d) => ({
      departmentId: d.departmentId,
      departmentCode: d.departmentCode,
      departmentName: d.departmentName,
      employeeCount: d.empSet.size,
      allowanceCount: d.allowanceCount,
      totalAllowanceAmount: d.totalAmount,
      amountByAllowanceType: d.byType
    }));

    const codes = targetPeriods.map((p) => p.periodCode);
    const labelCode =
      codes.length > 1 ? `${codes[codes.length - 1]} → ${codes[0]} (${codes.length} tháng)` : codes[0] || '';

    return {
      periodId: targetPeriods[0]?.periodId || 0,
      periodCode: labelCode,
      periodStart: targetPeriods[targetPeriods.length - 1]?.periodStart || '',
      periodEnd: targetPeriods[0]?.periodEnd || '',
      totalCompanyAllowance: totalCompany,
      departmentSummaries: deptSummaries,
      items: allItems
    };
  }

  filterAllowanceByDept(deptId: number | null): void {
    this.selectedAllowanceDeptId = deptId;
    this.allowancePage = 1;
    this.loadPeriodAllowances();
  }

  selectAllowanceItem(item: PeriodAllowanceItem): void {
    this.selectedAllowanceItem = item;
    this.allowanceSubformDetail = null;
    this.allowanceSubformWorkDetail = null;
    this.showAllowanceWorkDropdown = false;

    const targetPeriodId = Number(item.periodId || this.selectedPeriodId || this.periods[0]?.periodId);
    if (!targetPeriodId) return;

    this.loadingAllowanceSubform = true;
    this.payrollService.getBatchesByPeriod(targetPeriodId).subscribe({
      next: (batches) => {
        const sorted = [...(batches || [])].sort((a, b) => b.versionNo - a.versionNo);
        const targetBatch =
          sorted.find((b) => b.publishedAt || b.status === 'CONG_BO') ??
          sorted.find((b) => b.status === 'DA_CHOT') ??
          sorted[0];
        if (!targetBatch) {
          this.loadingAllowanceSubform = false;
          return;
        }
        this.payrollService.getBatchDetails(targetBatch.batchId).subscribe({
          next: (details) => {
            this.allowanceSubformDetail =
              (details || []).find((d) => d.employeeId === item.employeeId) || null;
            this.loadingAllowanceSubform = false;
          },
          error: () => {
            this.loadingAllowanceSubform = false;
          }
        });
        this.payrollService.getEmployeeWorkDetails(targetBatch.batchId, item.employeeId).subscribe({
          next: (wd) => {
            this.allowanceSubformWorkDetail = wd;
          },
          error: () => {}
        });
      },
      error: () => {
        this.loadingAllowanceSubform = false;
      }
    });
  }

  closeAllowanceSubform(): void {
    this.selectedAllowanceItem = null;
    this.allowanceSubformDetail = null;
    this.allowanceSubformWorkDetail = null;
    this.showAllowanceWorkDropdown = false;
  }

  toggleAllowanceWorkDropdown(event?: Event): void {
    if (event) event.stopPropagation();
    this.showAllowanceWorkDropdown = !this.showAllowanceWorkDropdown;
  }

  loadEmployeeHistory(): void {
    this.loadingHistory = true;
    this.historyPage = 1;
    this.payrollService
      .getEmployeePayrollHistory({
        departmentId: this.historyDeptId,
        keyword: '',
        status: this.historyStatus
      })
      .subscribe({
        next: (rows) => {
          this.allHistoryRows = rows || [];
          this.applyHistoryClientFilters();
          this.loadingHistory = false;
        },
        error: (err) => {
          this.errorMessage = this.extractErrorMessage(err, 'Không thể tải lịch sử lương nhân viên.');
          this.loadingHistory = false;
        }
      });
  }

  applyHistoryClientFilters(): void {
    this.historyPage = 1;
    this.historyMonthsNotice = '';
    let list = [...(this.allHistoryRows || [])];

    // 1. Lọc theo Năm & Quý (nếu chưa đủ tháng của Quý/Năm thì hiện các tháng hiện có; nếu Quý chưa có tháng nào thì hiển thị các tháng hiện có trong năm)
    if (this.historyYear !== null || this.historyQuarter !== null) {
      const uniquePeriodsMap = new Map<string, PayrollPeriod>();
      for (const r of list) {
        if (!uniquePeriodsMap.has(r.periodCode)) {
          uniquePeriodsMap.set(r.periodCode, {
            periodId: r.periodId,
            periodCode: r.periodCode,
            periodStart: r.periodStart,
            periodEnd: r.periodEnd
          });
        }
      }
      for (const p of this.periods) {
        if (!uniquePeriodsMap.has(p.periodCode)) {
          uniquePeriodsMap.set(p.periodCode, p);
        }
      }
      const allKnownPeriods = Array.from(uniquePeriodsMap.values());
      const { matched, fallbackNotice } = this.filterPeriodsByYearAndQuarter(
        allKnownPeriods,
        this.historyYear,
        this.historyQuarter
      );
      this.historyMonthsNotice = fallbackNotice;
      const allowedCodes = new Set(matched.map((p) => p.periodCode));
      this.historyActivePeriodCodes = Array.from(allowedCodes);
      if (allowedCodes.size > 0) {
        list = list.filter((r) => allowedCodes.has(r.periodCode));
      }
    } else {
      const codes = new Set<string>();
      for (const r of list) codes.add(r.periodCode);
      this.historyActivePeriodCodes = Array.from(codes);
    }

    // 2. Lọc theo Kỳ lương (Tháng cụ thể) nếu chọn
    if (this.historyPeriodCode) {
      list = list.filter((r) => r.periodCode === this.historyPeriodCode);
    }

    // 3. Lọc theo Nhân viên (dropdown chọn nhân viên)
    if (this.historyEmployeeCode) {
      list = list.filter((r) => r.employeeCode === this.historyEmployeeCode);
    }

    // 4. Lọc theo từ khóa Mã NV / Họ tên
    const kw = this.historyKeyword.trim().toLowerCase();
    if (kw) {
      list = list.filter(
        (r) =>
          (r.employeeCode || '').toLowerCase().includes(kw) ||
          (r.fullName || '').toLowerCase().includes(kw) ||
          (r.departmentName || '').toLowerCase().includes(kw)
      );
    }

    this.historyRows = list;
    if (
      this.selectedHistoryRow &&
      !this.historyRows.some((r) => r.payrollDetailId === this.selectedHistoryRow!.payrollDetailId)
    ) {
      this.closeHistorySubform();
    }
  }

  onHistoryYearOrQuarterChange(): void {
    this.historyPeriodCode = '';
    this.applyHistoryClientFilters();
  }

  resetHistoryFilters(): void {
    this.historyYear = null;
    this.historyQuarter = null;
    this.historyPeriodCode = '';
    this.historyDeptId = null;
    this.historyEmployeeCode = '';
    this.historyKeyword = '';
    this.historyStatus = '';
    this.historyMonthsNotice = '';
    this.historyPage = 1;
    this.closeHistorySubform();
    this.loadEmployeeHistory();
  }

  /**
   * Mở Subform chi tiết lương & minh chứng ngày công NGAY TẠI TAB LỊCH SỬ LƯƠNG NHÂN VIÊN
   * (Không nhảy sang tab Đợt lương theo kỳ)
   */
  selectHistoryRow(row: EmployeePayrollHistoryItem): void {
    this.selectedHistoryRow = row;
    this.historySelectedDetail = null;
    this.historySelectedWorkDetail = null;
    this.showHistoryWorkDropdown = false;
    this.loadingHistorySubform = true;

    this.payrollService.getBatchDetails(row.batchId).subscribe({
      next: (list) => {
        const found =
          (list || []).find((d) => d.payrollDetailId === row.payrollDetailId) ??
          (list || []).find((d) => d.employeeId === row.employeeId) ??
          null;
        this.historySelectedDetail =
          found || {
            payrollDetailId: row.payrollDetailId,
            employeeId: row.employeeId,
            employeeCode: row.employeeCode,
            employeeName: row.fullName,
            departmentName: row.departmentName,
            totalGross: row.totalGross,
            totalDeduction: row.totalDeduction,
            netSalary: row.netSalary,
            payslipId: row.payslipId,
            lines: []
          };
        this.loadingHistorySubform = false;
      },
      error: () => {
        this.historySelectedDetail = {
          payrollDetailId: row.payrollDetailId,
          employeeId: row.employeeId,
          employeeCode: row.employeeCode,
          employeeName: row.fullName,
          departmentName: row.departmentName,
          totalGross: row.totalGross,
          totalDeduction: row.totalDeduction,
          netSalary: row.netSalary,
          payslipId: row.payslipId,
          lines: []
        };
        this.loadingHistorySubform = false;
      }
    });

    this.payrollService.getEmployeeWorkDetails(row.batchId, row.employeeId).subscribe({
      next: (wd) => {
        this.historySelectedWorkDetail = wd;
      },
      error: () => {}
    });
  }

  openHistoryItemInBatch(row: EmployeePayrollHistoryItem): void {
    this.selectHistoryRow(row);
  }

  closeHistorySubform(): void {
    this.selectedHistoryRow = null;
    this.historySelectedDetail = null;
    this.historySelectedWorkDetail = null;
    this.showHistoryWorkDropdown = false;
  }

  toggleHistoryWorkDropdown(event?: Event): void {
    if (event) event.stopPropagation();
    this.showHistoryWorkDropdown = !this.showHistoryWorkDropdown;
  }

  printHistoryPayslip(row: EmployeePayrollHistoryItem, detail?: PayrollDetailView | null): void {
    if (!row.published && row.batchStatus !== 'CONG_BO') {
      this.errorMessage = 'Phiếu lương chỉ khả dụng sau khi đợt lương đã được Chốt và Công bố.';
      return;
    }
    const targetPayslipId = detail?.payslipId || row.payslipId;
    const targetDetailId = detail?.payrollDetailId || row.payrollDetailId;
    const req$ = targetPayslipId
      ? this.payrollService.getPayslipHtml(targetPayslipId)
      : targetDetailId != null
        ? this.payrollService.getPayslipHtmlByPayrollDetail(targetDetailId)
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

  /**
   * In / Xuất PDF toàn bộ bảng lịch sử lương theo đúng bộ lọc người dùng đang chọn
   * (theo Năm, Quý, Tháng hiện có, Phòng ban, Nhân viên cụ thể, Trạng thái)
   */
  printFilteredHistoryPdf(): void {
    const rows = this.historyRows || [];
    if (rows.length === 0) {
      this.errorMessage = 'Không có dữ liệu lịch sử lương nào trong bộ lọc hiện tại để in PDF.';
      return;
    }
    const printWindow = window.open('', '_blank');
    if (!printWindow) {
      this.errorMessage = 'Trình duyệt đang chặn cửa sổ in. Vui lòng cho phép mở popup để in/lưu PDF.';
      return;
    }

    const fmt = (n: number | null | undefined) =>
      Math.round(Number(n || 0)).toLocaleString('vi-VN');
    const esc = (s: unknown) =>
      String(s ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');

    const uniqueEmps = new Map<string, { code: string; name: string; dept: string; pos: string }>();
    const uniquePeriods = new Set<string>();
    let totalDays = 0;
    let totalOt = 0;

    for (const r of rows) {
      uniquePeriods.add(r.periodCode);
      totalDays += Number(r.actualWorkDays || 0);
      totalOt += Number(r.overtimeHours || 0);
      if (r.employeeCode && !uniqueEmps.has(r.employeeCode)) {
        uniqueEmps.set(r.employeeCode, {
          code: r.employeeCode,
          name: r.fullName,
          dept: r.departmentName,
          pos: r.positionName
        });
      }
    }

    const singleEmp = uniqueEmps.size === 1 ? Array.from(uniqueEmps.values())[0] : null;
    const sortedPeriodCodes = Array.from(uniquePeriods).sort();

    let scopeTimeText = 'Tất cả các kỳ hiện có';
    if (this.historyPeriodCode) {
      scopeTimeText = `Kỳ lương ${this.historyPeriodCode}`;
    } else if (this.historyQuarter !== null) {
      scopeTimeText = `Quý ${this.historyQuarter}${this.historyYear ? '/' + this.historyYear : ''} (${sortedPeriodCodes.length} tháng hiện có: ${sortedPeriodCodes.join(', ')})`;
    } else if (this.historyYear !== null) {
      scopeTimeText = `Năm ${this.historyYear} (${sortedPeriodCodes.length}/12 tháng hiện có: ${sortedPeriodCodes.join(', ')})`;
    } else if (sortedPeriodCodes.length > 0) {
      scopeTimeText = `Các tháng hiện có (${sortedPeriodCodes.join(', ')})`;
    }

    const deptLabel =
      this.historyDeptId != null
        ? this.departments.find((d) => d.departmentId === this.historyDeptId)?.departmentName || `Phòng ban #${this.historyDeptId}`
        : 'Tất cả phòng ban';

    const statusLabel =
      this.historyStatus === 'CONG_BO'
        ? 'Đã công bố'
        : this.historyStatus === 'DA_CHOT'
          ? 'Đã chốt'
          : this.historyStatus === 'NHAP'
            ? 'Đang nháp'
            : 'Tất cả trạng thái';

    const empLabel = singleEmp
      ? `${singleEmp.code} — ${singleEmp.name} (${singleEmp.dept} · ${singleEmp.pos})`
      : this.historyEmployeeCode
        ? this.historyEmployeeCode
        : this.historyKeyword.trim()
          ? `Tìm kiếm: "${this.historyKeyword.trim()}" (${uniqueEmps.size} nhân sự)`
          : `Tất cả nhân viên (${uniqueEmps.size} nhân sự)`;

    const reportTitle = singleEmp
      ? `BẢNG TỔNG HỢP LƯƠNG CÁ NHÂN — ${singleEmp.name.toUpperCase()} (${singleEmp.code})`
      : 'BẢNG TỔNG HỢP LỊCH SỬ TIỀN LƯƠNG NHÂN VIÊN';

    const nowStr = new Date().toLocaleString('vi-VN');

    const rowsHtml = rows
      .map((r, idx) => {
        const stText =
          r.batchStatus === 'CONG_BO'
            ? 'Đã công bố'
            : r.batchStatus === 'DA_CHOT'
              ? 'Đã chốt'
              : 'Đang nháp';
        return `<tr>
          <td class="tc">${idx + 1}</td>
          <td class="tc fw">${esc(r.periodCode)}</td>
          <td class="tc">v${r.versionNo}</td>
          <td class="tc">${esc(stText)}</td>
          <td class="fw">${esc(r.employeeCode)}</td>
          <td class="fw">${esc(r.fullName)}</td>
          <td>${esc(r.departmentName)}<br><span class="muted">${esc(r.positionName)}</span></td>
          <td class="tc">${r.actualWorkDays} công${r.overtimeHours > 0 ? `<br><span class="ot">+${r.overtimeHours}h OT</span>` : ''}</td>
          <td class="tr mono">${fmt(r.totalGross)} đ</td>
          <td class="tr mono ded">-${fmt(r.totalDeduction)} đ</td>
          <td class="tr mono net">${fmt(r.netSalary)} đ</td>
        </tr>`;
      })
      .join('');

    const html = `<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <title>${esc(reportTitle)}</title>
  <style>
    @page { size: A4 landscape; margin: 12mm; }
    body { font-family: 'Segoe UI', Arial, sans-serif; color: #0f172a; margin: 20px auto; max-width: 1120px; background: #fff; font-size: 12.5px; }
    .toolbar { display: flex; justify-content: flex-end; gap: 10px; margin-bottom: 16px; }
    .btn { padding: 8px 16px; border-radius: 6px; border: 1px solid #cbd5e1; cursor: pointer; font-weight: 600; font-size: 13px; background: #fff; color: #1e293b; }
    .btn-primary { background: #004581; color: #fff; border-color: #004581; }
    .header { text-align: center; border-bottom: 2px solid #004581; padding-bottom: 12px; margin-bottom: 16px; }
    .header h1 { margin: 0 0 4px 0; font-size: 19px; color: #004581; letter-spacing: 0.3px; }
    .header p { margin: 0; color: #475569; font-size: 12.5px; }
    .meta-box { display: grid; grid-template-columns: 1fr 1fr; gap: 6px 24px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px 16px; margin-bottom: 14px; font-size: 12.5px; }
    .summary-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 16px; }
    .stat { border: 1px solid #e2e8f0; border-radius: 8px; padding: 10px 12px; background: #fff; }
    .stat small { display: block; color: #64748b; font-size: 11px; text-transform: uppercase; font-weight: 700; margin-bottom: 3px; }
    .stat strong { font-size: 15px; }
    table { width: 100%; border-collapse: collapse; margin-bottom: 18px; }
    th, td { border: 1px solid #cbd5e1; padding: 7px 8px; vertical-align: middle; }
    th { background: #f1f5f9; color: #004581; font-weight: 700; font-size: 12px; text-align: left; }
    .tc { text-align: center; }
    .tr { text-align: right; }
    .fw { font-weight: 600; }
    .muted { color: #64748b; font-size: 11px; }
    .ot { color: #0284c7; font-size: 11px; font-weight: 600; }
    .mono { font-family: 'Consolas', 'Courier New', monospace; font-variant-numeric: tabular-nums; white-space: nowrap; }
    .ded { color: #b91c1c; }
    .net { color: #004581; font-weight: 700; }
    .total-row td { background: #eff6ff; font-weight: 700; font-size: 13px; }
    .notice { background: #eff6ff; border: 1px solid #bfdbfe; color: #1e3a8a; border-radius: 6px; padding: 8px 12px; margin-bottom: 14px; font-size: 12px; }
    .signs { display: grid; grid-template-columns: repeat(3, 1fr); text-align: center; margin-top: 28px; }
    .signs div { min-height: 85px; }
    @media print { .no-print { display: none !important; } body { margin: 0; max-width: none; } }
  </style>
</head>
<body>
  <div class="toolbar no-print">
    <button class="btn btn-primary" onclick="window.print()">🖨 In / Lưu thành file PDF</button>
    <button class="btn" onclick="window.close()">Đóng</button>
  </div>

  <div class="header">
    <h1>${esc(reportTitle)}</h1>
    <p>Hệ thống Quản trị Nhân sự &amp; Tiền lương (HRM Center) · Xuất báo cáo lúc ${esc(nowStr)}</p>
  </div>

  <div class="meta-box">
    <div><strong>Phạm vi thời gian:</strong> ${esc(scopeTimeText)}</div>
    <div><strong>Phòng ban:</strong> ${esc(deptLabel)}</div>
    <div><strong>Đối tượng nhân sự:</strong> ${esc(empLabel)}</div>
    <div><strong>Trạng thái đợt lương:</strong> ${esc(statusLabel)}</div>
  </div>

  ${this.historyMonthsNotice ? `<div class="notice">📅 <strong>Ghi chú phạm vi dữ liệu:</strong> ${esc(this.historyMonthsNotice)}</div>` : ''}

  <div class="summary-grid">
    <div class="stat"><small>Số bản ghi trong báo cáo</small><strong>${rows.length} bản ghi</strong></div>
    <div class="stat"><small>Tổng thu nhập (Gross)</small><strong style="color:#004581;">${fmt(this.historyTotalGross)} đ</strong></div>
    <div class="stat"><small>Tổng khấu trừ &amp; phạt</small><strong style="color:#b91c1c;">-${fmt(this.historyTotalDeduction)} đ</strong></div>
    <div class="stat"><small>Tổng thực lĩnh (Net)</small><strong style="color:#15803d;">${fmt(this.historyTotalNet)} đ</strong></div>
  </div>

  <table>
    <thead>
      <tr>
        <th class="tc" style="width:36px;">STT</th>
        <th class="tc" style="width:74px;">Kỳ lương</th>
        <th class="tc" style="width:46px;">Đợt</th>
        <th class="tc" style="width:86px;">Trạng thái</th>
        <th style="width:82px;">Mã NV</th>
        <th>Họ và tên nhân viên</th>
        <th>Phòng ban / Chức vụ</th>
        <th class="tc" style="width:86px;">Công / OT</th>
        <th class="tr" style="width:118px;">Tổng Gross</th>
        <th class="tr" style="width:112px;">Khấu trừ</th>
        <th class="tr" style="width:124px;">Thực lĩnh (Net)</th>
      </tr>
    </thead>
    <tbody>
      ${rowsHtml}
      <tr class="total-row">
        <td colspan="7" class="tr">TỔNG CỘNG (${rows.length} bản ghi):</td>
        <td class="tc">${Number(totalDays.toFixed(2))} công${totalOt > 0 ? `<br><span class="ot">+${Number(totalOt.toFixed(2))}h OT</span>` : ''}</td>
        <td class="tr mono">${fmt(this.historyTotalGross)} đ</td>
        <td class="tr mono ded">-${fmt(this.historyTotalDeduction)} đ</td>
        <td class="tr mono net">${fmt(this.historyTotalNet)} đ</td>
      </tr>
    </tbody>
  </table>

  <div class="signs">
    <div><strong>Người lập biểu</strong><br><span class="muted">(Ký, ghi rõ họ tên)</span></div>
    <div><strong>Kế toán tiền lương</strong><br><span class="muted">(Ký, ghi rõ họ tên)</span></div>
    <div><strong>Ban Giám đốc</strong><br><span class="muted">(Ký, đóng dấu xác nhận)</span></div>
  </div>
</body>
</html>`;

    printWindow.document.open();
    printWindow.document.write(html);
    printWindow.document.close();
  }

  private extractPeriodYearMonth(p: { periodCode?: string; periodStart?: string }): {
    year: number;
    month: number;
    quarter: number;
  } | null {
    const codeMatch = (p.periodCode || '').match(/^(\d{4})-(\d{2})$/);
    if (codeMatch) {
      const year = Number(codeMatch[1]);
      const month = Number(codeMatch[2]);
      if (month >= 1 && month <= 12) {
        return { year, month, quarter: Math.ceil(month / 3) };
      }
    }
    const startMatch = (p.periodStart || '').match(/^(\d{4})-(\d{2})/);
    if (startMatch) {
      const year = Number(startMatch[1]);
      const month = Number(startMatch[2]);
      if (month >= 1 && month <= 12) {
        return { year, month, quarter: Math.ceil(month / 3) };
      }
    }
    return null;
  }

  private filterPeriodsByYearAndQuarter(
    sourcePeriods: PayrollPeriod[],
    year: number | null,
    quarter: number | null
  ): { matched: PayrollPeriod[]; fallbackNotice: string } {
    const withMeta = sourcePeriods
      .map((p) => ({ period: p, ym: this.extractPeriodYearMonth(p) }))
      .filter((x) => x.ym !== null) as Array<{
      period: PayrollPeriod;
      ym: { year: number; month: number; quarter: number };
    }>;

    if (withMeta.length === 0) {
      return { matched: sourcePeriods, fallbackNotice: '' };
    }

    let yearFiltered = year !== null ? withMeta.filter((x) => x.ym.year === Number(year)) : withMeta;
    let fallbackNotice = '';

    if (year !== null && yearFiltered.length === 0) {
      yearFiltered = withMeta;
      const availCodes = yearFiltered.map((x) => x.period.periodCode).join(', ');
      fallbackNotice = `Năm ${year} chưa có dữ liệu phát sinh — Đang hiển thị các tháng hiện có trong hệ thống: ${availCodes}.`;
    }

    if (quarter !== null) {
      const qNum = Number(quarter);
      const qFiltered = yearFiltered.filter((x) => x.ym.quarter === qNum);
      if (qFiltered.length === 0) {
        const availCodes = yearFiltered.map((x) => x.period.periodCode).join(', ');
        fallbackNotice = `Quý ${qNum}${year ? '/' + year : ''} chưa có dữ liệu phát sinh — Hệ thống tự động hiển thị các tháng hiện có (${availCodes}).`;
        return { matched: yearFiltered.map((x) => x.period), fallbackNotice };
      }
      const uniqueMonths = new Set(qFiltered.map((x) => `${x.ym.year}-${x.ym.month}`));
      const codes = qFiltered.map((x) => x.period.periodCode).join(', ');
      if (uniqueMonths.size < 3) {
        fallbackNotice = `Quý ${qNum}${year ? '/' + year : ''} hiện có ${uniqueMonths.size}/3 tháng dữ liệu (${codes}) — Đang hiển thị các tháng hiện có.`;
      } else {
        fallbackNotice = `Đang lọc Quý ${qNum}${year ? '/' + year : ''} gồm các tháng: ${codes}.`;
      }
      return { matched: qFiltered.map((x) => x.period), fallbackNotice };
    }

    if (year !== null && !fallbackNotice) {
      const uniqueMonths = new Set(yearFiltered.map((x) => `${x.ym.year}-${x.ym.month}`));
      const codes = yearFiltered.map((x) => x.period.periodCode).join(', ');
      if (uniqueMonths.size < 12) {
        fallbackNotice = `Năm ${year} hiện có ${uniqueMonths.size}/12 tháng dữ liệu (${codes}) — Đang hiển thị các tháng hiện có.`;
      } else {
        fallbackNotice = `Đang lọc trọn Năm ${year} (${codes}).`;
      }
    }

    return { matched: yearFiltered.map((x) => x.period), fallbackNotice };
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

  get availableYears(): number[] {
    const years = new Set<number>();
    for (const p of this.periods || []) {
      const ym = this.extractPeriodYearMonth(p);
      if (ym) years.add(ym.year);
    }
    for (const r of this.allHistoryRows || []) {
      const ym = this.extractPeriodYearMonth(r);
      if (ym) years.add(ym.year);
    }
    if (years.size === 0) years.add(new Date().getFullYear());
    return Array.from(years).sort((a, b) => b - a);
  }

  get allowanceEmployeeOptions(): Array<{ employeeCode: string; fullName: string; departmentName: string }> {
    const map = new Map<string, { employeeCode: string; fullName: string; departmentName: string }>();
    for (const it of this.allowanceOverview?.items || []) {
      if (it.employeeCode && !map.has(it.employeeCode)) {
        map.set(it.employeeCode, {
          employeeCode: it.employeeCode,
          fullName: it.fullName,
          departmentName: it.departmentName
        });
      }
    }
    return Array.from(map.values()).sort((a, b) => a.employeeCode.localeCompare(b.employeeCode));
  }

  get filteredAllowanceItems(): PeriodAllowanceItem[] {
    let list = this.allowanceOverview?.items || [];
    if (this.allowanceEmployeeCode) {
      list = list.filter((it) => it.employeeCode === this.allowanceEmployeeCode);
    }
    const kw = this.allowanceKeyword.trim().toLowerCase();
    if (kw) {
      list = list.filter(
        (it) =>
          (it.employeeCode || '').toLowerCase().includes(kw) ||
          (it.fullName || '').toLowerCase().includes(kw) ||
          (it.allowanceName || '').toLowerCase().includes(kw) ||
          (it.departmentName || '').toLowerCase().includes(kw)
      );
    }
    return list;
  }

  get filteredAllowanceTotalAmount(): number {
    return this.filteredAllowanceItems.reduce((sum, it) => sum + (it.estimatedPeriodAmount || 0), 0);
  }

  get selectedEmployeeAllowanceItems(): PeriodAllowanceItem[] {
    if (!this.selectedAllowanceItem) return [];
    return (this.allowanceOverview?.items || []).filter(
      (it) => it.employeeId === this.selectedAllowanceItem!.employeeId
    );
  }

  get selectedEmployeeAllowanceTotal(): number {
    return this.selectedEmployeeAllowanceItems.reduce(
      (sum, it) => sum + (it.estimatedPeriodAmount || 0),
      0
    );
  }

  get historyEmployeeOptions(): Array<{ employeeCode: string; fullName: string; departmentName: string }> {
    const map = new Map<string, { employeeCode: string; fullName: string; departmentName: string }>();
    for (const r of this.allHistoryRows || []) {
      if (r.employeeCode && !map.has(r.employeeCode)) {
        map.set(r.employeeCode, {
          employeeCode: r.employeeCode,
          fullName: r.fullName,
          departmentName: r.departmentName
        });
      }
    }
    return Array.from(map.values()).sort((a, b) => a.employeeCode.localeCompare(b.employeeCode));
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
    const list = this.filteredAllowanceItems;
    return Math.max(1, Math.ceil(list.length / this.pageSize));
  }

  get pagedAllowanceItems(): PeriodAllowanceItem[] {
    const list = this.filteredAllowanceItems;
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
