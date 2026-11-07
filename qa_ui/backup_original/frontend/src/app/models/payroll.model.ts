export interface PayrollPeriod {
  periodId: number;
  periodCode: string;
  periodStart: string;
  periodEnd: string;
}

export type BatchStatus = 'NHAP' | 'DA_CHOT' | 'CONG_BO';

export interface PayrollBatch {
  batchId: number;
  periodId: number;
  versionNo: number;
  previousBatchId: number | null;
  status: BatchStatus;
  adjustmentReason: string | null;
  lockedBy: number | null;
  lockedAt: string | null;
  publishedBy: number | null;
  publishedAt: string | null;
}

export interface PayrollLine {
  code: string;
  name: string;
  direction: 'ADD' | 'DEDUCT';
  amount: number;
  explanation?: string;
}

export interface PayrollDetailView {
  payrollDetailId: number;
  employeeId: number;
  employeeCode?: string;
  employeeName?: string;
  baseSalary?: number;
  workDays?: string;
  overtimeAmount?: number;
  allowanceAmount?: number;
  insuranceAmount?: number;
  taxAmount?: number;
  totalGross: number;
  totalDeduction: number;
  netSalary: number;
  lines: PayrollLine[];
}

export interface AdjustmentRequest {
  adjustmentReason: string;
}