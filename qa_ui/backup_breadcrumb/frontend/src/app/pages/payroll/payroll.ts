import {RowViewDirective} from '../../shared/row-view.directive';
import {Pagination} from '../../shared/pagination';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PayrollService } from '../../services/payroll.service';
import { PayrollPeriod, PayrollBatch, PayrollDetailView } from '../../models/payroll.model';

@Component({
  selector: 'app-payroll',
  standalone: true,
  imports:[RowViewDirective,Pagination,CommonModule, FormsModule, DecimalPipe],
  templateUrl: './payroll.html',
  styleUrl: './payroll.css'
})
export class PayrollPage implements OnInit {
  private payrollService = inject(PayrollService);

  periods: PayrollPeriod[] = [];
  selectedPeriodId: number | null = null;
  currentBatch: PayrollBatch | null = null;
  details: PayrollDetailView[] = [];

  // Phân trang danh sách
  currentPage = 1;
  pageSize = 5;

  selectedDetail: PayrollDetailView | null = null;
  isDrawerOpen = false;

  loading = false;
  actionMessage = '';
  errorMessage = '';

  adjustmentReasonInput = '';
  showAdjustModal = false;

  ngOnInit(): void {
    this.loadPeriods();
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

  createDraft(): void {
    if (!this.selectedPeriodId) {
      this.errorMessage = 'Vui lòng chọn kỳ lương trước.';
      return;
    }
    this.loading = true;
    this.resetState();
    this.payrollService.createDraftBatch(Number(this.selectedPeriodId)).subscribe({
      next: (batch) => {
        this.currentBatch = batch;
        this.actionMessage = `Đã tạo đợt lương nháp thành công (Mã #${batch.batchId} - Bản v${batch.versionNo}).`;
        this.loadDetails(batch.batchId);
      },
      error: (err) => {
        this.errorMessage = err.error?.loi_chi_tiet || 'Kỳ này đã có đợt lương nháp đang xử lý.';
        this.loading = false;
      }
    });
  }

  calculate(): void {
    if (!this.currentBatch?.batchId) {
      this.errorMessage = 'Chưa có đợt lương để tính. Bấm "+ Tạo đợt lương" trước.';
      return;
    }
    this.loading = true;
    this.resetState();
    this.payrollService.calculatePayroll(this.currentBatch.batchId).subscribe({
      next: () => {
        this.actionMessage = 'Hệ thống đã tính toán tiền lương tự động thành công!';
        this.loadDetails(this.currentBatch!.batchId);
      },
      error: (err) => {
        this.errorMessage = err.error?.loi_chi_tiet || 'Không thể tính toán đợt lương này.';
        this.loading = false;
      }
    });
  }

  lock(): void {
    if (!this.currentBatch?.batchId) {
      this.errorMessage = 'Không xác định được đợt lương cần chốt.';
      return;
    }
    if (!confirm('Bạn có chắc chắn muốn CHỐT bảng lương? Số liệu sau khi chốt sẽ trở thành bất biến theo quy định kế toán.')) return;
    this.loading = true;
    this.resetState();
    this.payrollService.lockBatch(this.currentBatch.batchId).subscribe({
      next: (batch) => {
        this.currentBatch = batch;
        this.actionMessage = 'Bảng lương đã được CHỐT thành công và niêm phong bất biến!';
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.loi_chi_tiet || 'Không thể chốt bảng lương.';
        this.loading = false;
      }
    });
  }

  publish(): void {
    if (!this.currentBatch?.batchId) return;
    this.loading = true;
    this.resetState();
    this.payrollService.publishBatch(this.currentBatch.batchId).subscribe({
      next: (batch) => {
        this.currentBatch = batch;
        this.actionMessage = 'Đã công bố bảng lương và tự động phát hành phiếu lương cho nhân viên!';
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.loi_chi_tiet || 'Không thể công bố bảng lương.';
        this.loading = false;
      }
    });
  }

  submitAdjustment(): void {
    if (!this.adjustmentReasonInput.trim()) {
      alert('Vui lòng điền lý do điều chỉnh.');
      return;
    }
    if (!this.currentBatch?.batchId) return;
    this.loading = true;
    this.payrollService.createAdjustment(this.currentBatch.batchId, this.adjustmentReasonInput).subscribe({
      next: (batch) => {
        this.currentBatch = batch;
        this.showAdjustModal = false;
        this.adjustmentReasonInput = '';
        this.actionMessage = `Đã tạo phiên bản điều chỉnh bù trừ mới (v${batch.versionNo})!`;
        this.loadDetails(batch.batchId);
      },
      error: (err) => {
        this.errorMessage = err.error?.loi_chi_tiet || 'Không thể tạo phiên bản điều chỉnh.';
        this.loading = false;
      }
    });
  }

  viewDetail(item: PayrollDetailView): void {
    this.selectedDetail = item;
    this.isDrawerOpen = true;
  }

  closeDrawer(): void {
    this.isDrawerOpen = false;
    this.selectedDetail = null;
  }

  // In chuẩn: Tải nội dung HTML qua Service có đính kèm Bearer token và đổ ra tab in
  printPayslip(item: PayrollDetailView): void {
    this.payrollService.getPreviewPayslipHtml(item.employeeId).subscribe({
      next: (htmlContent) => {
        const printWin = window.open('', '_blank');
        if (printWin) {
          printWin.document.open();
          printWin.document.write(htmlContent);
          printWin.document.close();
        }
      },
      error: () => alert('Chưa tìm thấy phiếu lương hợp lệ để in.')
    });
  }

  // Phân trang
  get totalPages(): number {
    return Math.ceil(this.details.length / this.pageSize) || 1;
  }

  get pagedDetails(): PayrollDetailView[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.details.slice(start, start + this.pageSize);
  }

  prevPage(): void {
    if (this.currentPage > 1) this.currentPage--;
  }

  nextPage(): void {
    if (this.currentPage < this.totalPages) this.currentPage++;
  }

  // Thống kê tổng quỹ
  get totalGross(): number {
    return this.details.reduce((acc, cur) => acc + (cur.totalGross || 0), 0);
  }

  get totalDeduction(): number {
    return this.details.reduce((acc, cur) => acc + (cur.totalDeduction || 0), 0);
  }

  get totalNet(): number {
    return this.details.reduce((acc, cur) => acc + (cur.netSalary || 0), 0);
  }

  private resetState(): void {
    this.actionMessage = '';
    this.errorMessage = '';
  }
}
