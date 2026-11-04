import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PayrollService } from '../../services/payroll.service';
import { DepartmentService, Department, Position } from '../../services/department.service';
import { PayrollPeriod, PayrollBatch, PayrollDetailView } from '../../models/payroll.model';

@Component({
  selector: 'app-payroll',
  standalone: true,
  imports: [CommonModule, FormsModule, DecimalPipe],
  templateUrl: './payroll.html',
  styleUrl: './payroll.css'
})
export class PayrollPage implements OnInit {
  private payrollService = inject(PayrollService);
  private deptService = inject(DepartmentService);

  // Tabs chế độ xem
  activeTab: 'batch' | 'employee-history' | 'department-summary' = 'batch';

  periods: PayrollPeriod[] = [];
  selectedPeriodId: number | null = null;
  currentBatch: PayrollBatch | null = null;
  details: PayrollDetailView[] = [];

  // Danh mục phục vụ bộ lọc
  departments: Department[] = [];
  positions: Position[] = [];

  // Các trường của bộ lọc
  filterKeyword: string = '';
  filterDeptId: string = '';
  filterPosId: string = '';
  filterMinNet: number | null = null;
  sortColumn: string = 'netSalary';
  sortAscending: boolean = false;

  // Phân trang
  currentPage = 1;
  pageSize = 10;

  selectedDetail: PayrollDetailView | null = null;
  isDrawerOpen = false;
  loading = false;
  actionMessage = '';
  errorMessage = '';

  adjustmentReasonInput = '';
  showAdjustModal = false;

  ngOnInit(): void {
    this.loadPeriods();
    this.loadCatalogs();
  }

  loadCatalogs(): void {
    this.deptService.list(0).subscribe({ next: res => this.departments = res.content || [] });
    this.deptService.positions(0).subscribe({ next: res => this.positions = res.content || [] });
  }

  loadPeriods(): void {
    this.loading = true;
    this.payrollService.getPeriods().subscribe({
      next: (res) => {
        this.periods = res;
        if (this.periods.length > 0) {
          this.selectedPeriodId = this.periods[0].periodId;
          this.checkPeriodData();
        }
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'Không thể tải danh sách kỳ lương.';
        this.loading = false;
      }
    });
  }

  onPeriodChange(): void {
    this.currentPage = 1;
    this.checkPeriodData();
  }

  checkPeriodData(): void {
    if (!this.selectedPeriodId) return;
    this.resetState();
    this.details = [];
    this.currentBatch = null;

    this.payrollService.getCurrentBatchByPeriod(this.selectedPeriodId).subscribe({
      next: (batch) => {
        this.currentBatch = batch;
        if (batch && batch.batchId) {
          this.loadDetails(batch.batchId);
        }
      },
      error: () => {
        this.currentBatch = null;
        this.details = [];
      }
    });
  }

  loadDetails(batchId: number): void {
    this.payrollService.getBatchDetails(batchId).subscribe({
      next: (items) => {
        this.details = items;
        this.currentPage = 1;
        this.loading = false;
      },
      error: () => {
        this.details = [];
        this.loading = false;
      }
    });
  }

  // Sắp xếp dữ liệu linh hoạt
  toggleSort(col: string): void {
    if (this.sortColumn === col) {
      this.sortAscending = !this.sortAscending;
    } else {
      this.sortColumn = col;
      this.sortAscending = true;
    }
  }

  // Danh sách đã qua lọc và sắp xếp
  get filteredAndSortedDetails(): PayrollDetailView[] {
    let result = [...this.details];

    if (this.filterKeyword.trim()) {
      const kw = this.filterKeyword.toLowerCase().trim();
      result = result.filter(d => 
        ("NV-" + d.employeeId).toLowerCase().includes(kw) ||
        (d.employeeName && d.employeeName.toLowerCase().includes(kw))
      );
    }

    if (this.filterMinNet !== null && this.filterMinNet > 0) {
      result = result.filter(d => d.netSalary >= this.filterMinNet!);
    }

    result.sort((a: any, b: any) => {
      let valA = a[this.sortColumn] ?? 0;
      let valB = b[this.sortColumn] ?? 0;
      if (typeof valA === 'string') {
        return this.sortAscending ? valA.localeCompare(valB) : valB.localeCompare(valA);
      }
      return this.sortAscending ? (valA - valB) : (valB - valA);
    });

    return result;
  }

  // Thống kê tổng hợp theo phòng ban cho kỳ đang chọn
  get departmentStats(): { deptName: string; count: number; totalGross: number; totalNet: number }[] {
    // Nhóm theo phòng ban dựa trên danh sách chi tiết
    const map = new Map<string, { count: number; totalGross: number; totalNet: number }>();
    for (const d of this.details) {
      const dept = 'Phòng Đào Tạo & Nhân Sự'; // Có thể map từ departmentId nếu có
      const stat = map.get(dept) || { count: 0, totalGross: 0, totalNet: 0 };
      stat.count++;
      stat.totalGross += d.totalGross || 0;
      stat.totalNet += d.netSalary || 0;
      map.set(dept, stat);
    }
    return Array.from(map.entries()).map(([deptName, s]) => ({ deptName, ...s }));
  }

  get totalPages(): number {
    return Math.ceil(this.filteredAndSortedDetails.length / this.pageSize) || 1;
  }

  get pagedDetails(): PayrollDetailView[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredAndSortedDetails.slice(start, start + this.pageSize);
  }

  prevPage(): void { if (this.currentPage > 1) this.currentPage--; }
  nextPage(): void { if (this.currentPage < this.totalPages) this.currentPage++; }

  get totalGross(): number { return this.details.reduce((acc, cur) => acc + (cur.totalGross || 0), 0); }
  get totalDeduction(): number { return this.details.reduce((acc, cur) => acc + (cur.totalDeduction || 0), 0); }
  get totalNet(): number { return this.details.reduce((acc, cur) => acc + (cur.netSalary || 0), 0); }

  createDraft(): void {
    if (!this.selectedPeriodId) return;
    this.loading = true;
    this.resetState();
    this.payrollService.createDraftBatch(Number(this.selectedPeriodId)).subscribe({
      next: (b) => {
        this.currentBatch = b;
        this.actionMessage = `Đã tạo đợt lương nháp #${b.batchId} (v${b.versionNo}).`;
        this.loadDetails(b.batchId);
      },
      error: (e) => { this.errorMessage = e.error?.loi_chi_tiet || 'Kỳ này đã có đợt lương.'; this.loading = false; }
    });
  }

  calculate(): void {
    if (!this.currentBatch?.batchId) return;
    this.loading = true;
    this.resetState();
    this.payrollService.calculatePayroll(this.currentBatch.batchId).subscribe({
      next: () => {
        this.actionMessage = 'Đã tính toán tiền lương tự động thành công!';
        this.loadDetails(this.currentBatch!.batchId);
      },
      error: (e) => { this.errorMessage = e.error?.loi_chi_tiet || 'Không thể tính toán.'; this.loading = false; }
    });
  }

  lock(): void {
    if (!this.currentBatch?.batchId || !confirm('Xác nhận CHỐT bảng lương thành bất biến?')) return;
    this.loading = true;
    this.payrollService.lockBatch(this.currentBatch.batchId).subscribe({
      next: (b) => { this.currentBatch = b; this.actionMessage = 'Bảng lương đã được CHỐT thành công!'; this.loading = false; },
      error: (e) => { this.errorMessage = e.error?.loi_chi_tiet || 'Không thể chốt lương.'; this.loading = false; }
    });
  }

  publish(): void {
    if (!this.currentBatch?.batchId) return;
    this.loading = true;
    this.payrollService.publishBatch(this.currentBatch.batchId).subscribe({
      next: (b) => { this.currentBatch = b; this.actionMessage = 'Đã công bố và phát hành phiếu lương cho nhân viên!'; this.loading = false; },
      error: (e) => { this.errorMessage = e.error?.loi_chi_tiet || 'Không thể công bố.'; this.loading = false; }
    });
  }

  submitAdjustment(): void {
    if (!this.adjustmentReasonInput.trim() || !this.currentBatch?.batchId) return;
    this.loading = true;
    this.payrollService.createAdjustment(this.currentBatch.batchId, this.adjustmentReasonInput).subscribe({
      next: (b) => {
        this.currentBatch = b;
        this.showAdjustModal = false;
        this.adjustmentReasonInput = '';
        this.actionMessage = `Đã tạo phiên bản điều chỉnh (v${b.versionNo})!`;
        this.loadDetails(b.batchId);
      },
      error: (e) => { this.errorMessage = e.error?.loi_chi_tiet || 'Lỗi tạo điều chỉnh.'; this.loading = false; }
    });
  }

  viewDetail(item: PayrollDetailView): void { this.selectedDetail = item; this.isDrawerOpen = true; }
  closeDrawer(): void { this.isDrawerOpen = false; this.selectedDetail = null; }

  printPayslip(item: PayrollDetailView): void {
    this.payrollService.getPreviewPayslipHtml(item.employeeId).subscribe({
      next: (html) => {
        const w = window.open('', '_blank');
        if (w) { w.document.write(html); w.document.close(); }
      },
      error: () => alert('Chưa tìm thấy phiếu lương hợp lệ để in.')
    });
  }

  private resetState(): void { this.actionMessage = ''; this.errorMessage = ''; }
}