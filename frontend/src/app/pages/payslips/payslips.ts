import {HrmNotice} from '../../shared/ui/notice';
import {PageHeader} from '../../shared/page-header';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PayrollService } from '../../services/payroll.service';

@Component({
  selector: 'app-payslips',
  standalone: true,
  imports:[HrmNotice,PageHeader,CommonModule, FormsModule, DecimalPipe],
  templateUrl: './payslips.html',
  styleUrl: './payslips.css'
})
export class PayslipsPage implements OnInit {
  private payrollService = inject(PayrollService);

  payslips: any[] = [];
  selectedPayslip: any = null;
  loading = false;
  errorMessage = '';

  ngOnInit(): void {
    this.loadMyPayslips();
  }

  loadMyPayslips(): void {
    this.loading = true;
    this.errorMessage = '';
    this.selectedPayslip = null;
    this.payrollService.getMyPayslips().subscribe({
      next: (data) => {
        this.payslips = data || [];
        if (this.payslips.length > 0) {
          this.selectedPayslip = this.payslips[0];
        }
        this.loading = false;
      },
      error: (error) => {
        this.payslips = [];
        this.errorMessage = error.status === 0 ? 'Không thể kết nối máy chủ. Vui lòng thử lại.'
          : error.status === 403 ? 'Bạn không có quyền xem phiếu lương này.'
          : error.error?.loi_chi_tiet || 'Không tải được phiếu lương. Vui lòng thử lại.';
        this.loading = false;
      }
    });
  }

  onSelectPayslip(event: Event): void {
    const target = event.target as HTMLSelectElement;
    const payslipId = Number(target.value);
    this.selectedPayslip = this.payslips.find(p => p.payslipId === payslipId) || null;
  }

  // Sử dụng Service tải HTML qua HTTP Client (hỗ trợ JWT, không hardcode cổng)
  printCurrentPayslip(): void {
    if (!this.selectedPayslip) return;
    this.payrollService.getPayslipHtml(this.selectedPayslip.payslipId).subscribe({
      next: (htmlContent) => {
        const printWin = window.open('', '_blank');
        if (printWin) {
          printWin.document.open();
          printWin.document.write(htmlContent);
          printWin.document.close();
        }
      },
      error: () => this.errorMessage = 'Không thể tải bản in phiếu lương.'
    });
  }
}
