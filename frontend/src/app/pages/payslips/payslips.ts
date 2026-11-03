import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PayrollService } from '../../services/payroll.service';

@Component({
  selector: 'app-payslips',
  standalone: true,
  imports: [CommonModule, FormsModule, DecimalPipe],
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
    this.payrollService.getMyPayslips(1).subscribe({
      next: (data) => {
        this.payslips = data || [];
        if (this.payslips.length > 0) {
          this.selectedPayslip = this.payslips[0];
        }
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'Chưa có phiếu lương nào được công bố cho tài khoản của bạn.';
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
      error: () => alert('Không thể tải bản in phiếu lương.')
    });
  }
}