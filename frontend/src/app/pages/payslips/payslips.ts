import { PayrollLine } from '../../models/payroll.model';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import {HrmNotice} from '../../shared/ui/notice';
import {PageHeader} from '../../shared/page-header';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PayslipDirectoryRow, PayrollService } from '../../services/payroll.service';

interface PayslipDetails {
  payslipId: number;
  payslipNumber: string;
  periodCode: string;
  positionName: string | null;
  actualWorkDays: number | null;
  standardWorkDays: number | null;
  totalGross: number;
  totalDeduction: number;
  netSalary: number;
  lines: PayrollLine[];
}

@Component({
  selector: 'app-payslips',
  standalone: true,
  imports:[HrmNotice,PageHeader,FormsModule],
  templateUrl: './payslips.html',
  styleUrl: './payslips.scss'
})
export class PayslipsPage implements OnInit {
  private payrollService = inject(PayrollService);

  private destroyRef = inject(DestroyRef);
  private detailRequest?: Subscription;
  private rows: PayslipDirectoryRow[] = [];
  canSelectEmployee = false;
  employeeQuery = '';
  employeeId: number | null = null;
  periodCode = '';
  slipQuery = '';
  payslips: PayslipDirectoryRow[] = [];
  employees: PayslipDirectoryRow[] = [];
  periods: string[] = [];
  selectedRow: PayslipDirectoryRow | null = null;
  selectedPayslip: PayslipDetails | null = null;
  loading = false;
  detailLoading = false;
  errorMessage = '';

  private readonly currency = new Intl.NumberFormat('vi-VN', {
    style: 'currency', currency: 'VND', maximumFractionDigits: 0
  });
  private readonly days = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 4 });

  private readonly percent = new Intl.NumberFormat('vi-VN', { style: 'percent', maximumFractionDigits: 2 });
  deductionShare(amount: number, gross: number): string { return this.percent.format(amount / gross); }

  money(value: number): string { return this.currency.format(value); }
  workDays(value: number | null): string { return value == null ? 'Chưa có dữ liệu' : this.days.format(value); }

  get earnings(): PayrollLine[] {
    return this.selectedPayslip?.lines.filter(line => line.direction === 'ADD') ?? [];
  }
  get deductions(): PayrollLine[] {
    return this.selectedPayslip?.lines.filter(line => line.direction === 'DEDUCT') ?? [];
  }

  ngOnInit(): void {
    this.loading = true;
    this.payrollService.getPayslipDirectory().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: directory => {
        this.canSelectEmployee = directory.canSelectEmployee;
        this.rows = directory.rows;
        this.employees = [...new Map(this.rows.map(row => [row.employeeId, row])).values()]
          .sort((a, b) => a.employeeName.localeCompare(b.employeeName, 'vi'));
        this.periods = [...new Set(this.rows.map(row => row.periodCode))];
        this.loading = false;
        this.search();
      },
      error: error => {
        this.errorMessage = error.status === 403 ? 'Bạn không có quyền xem phiếu lương.'
          : 'Không tải được danh sách phiếu lương. Vui lòng thử lại.';
        this.loading = false;
      }
    });
  }

  private normalize(value: string): string {
    return value.trim().toLowerCase().normalize('NFD').replace(/\p{M}/gu, '').replace(/đ/g, 'd');
  }

  get employeeOptions(): PayslipDirectoryRow[] {
    const tokens = this.normalize(this.employeeQuery).split(/\s+/);
    return this.employees.filter(row => row.employeeId === this.employeeId ||
      tokens.every(token => this.normalize(row.employeeCode + ' ' + row.employeeName).includes(token)));
  }

  search(): void {
    const employees = new Set(this.employeeOptions.map(row => row.employeeId));
    const query = this.normalize(this.slipQuery);
    this.payslips = this.rows.filter(row =>
      (this.employeeId === null ? employees.has(row.employeeId) : row.employeeId === this.employeeId) &&
      (!this.periodCode || row.periodCode === this.periodCode) &&
      this.normalize(row.payslipNumber).includes(query));
    this.selectPayslip(this.payslips[0]?.payslipId ?? null);
  }

  resetFilters(): void {
    this.employeeId = null;
    this.employeeQuery = this.periodCode = this.slipQuery = '';
    this.search();
  }

  selectPayslip(id: number | null): void {
    this.detailRequest?.unsubscribe();
    this.selectedRow = this.payslips.find(row => row.payslipId === id) ?? null;
    this.selectedPayslip = null;
    this.errorMessage = '';
    this.detailLoading = id !== null && this.selectedRow !== null;
    if (id === null || !this.selectedRow) return;
    this.detailRequest = this.payrollService.getPayslipDetail(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: detail => { this.selectedPayslip = detail; this.detailLoading = false; },
      error: () => { this.errorMessage = 'Không tải được chi tiết phiếu lương.'; this.detailLoading = false; }
    });
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
