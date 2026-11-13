export interface PayrollPeriod {
  periodId: number;
  periodCode: string;
  periodStart: string;
  periodEnd: string;
}

export interface PayrollBatch {
  batchId: number;
  periodId: number;
  versionNo: number;
  previousBatchId?: number | null;
  status: 'NHAP' | 'DA_CHOT' | 'CONG_BO';
  adjustmentReason?: string | null;
  lockedAt?: string | null;
  publishedAt?: string | null;
}

export interface PayrollLineItem {
  code: string;
  name: string;
  direction: 'ADD' | 'DEDUCT';
  quantity?: number;
  unitRate?: number;
  multiplier?: number;
  amount: number;
  explanation?: string;
  deductionShareText?: string;
}

export type PayrollLine = PayrollLineItem;

export interface PayslipDetail {
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

export interface PayrollDetailView {
  payrollDetailId: number | null;
  missingSalary?: boolean;
  provisional?: boolean;
  employeeId: number;
  employeeCode?: string;
  employeeName?: string;
  departmentName?: string;

  baseSalary?: number;
  workDays?: string;
  overtimeAmount?: number;
  allowanceAmount?: number;
  insuranceAmount?: number;
  taxAmount?: number;

  totalGross: number | null;
  totalDeduction: number | null;
  netSalary: number | null;

  payslipId?: number | null;
  lines: PayrollLineItem[];
}

// --- 1. MINH CHỨNG NGÀY CÔNG ---
export interface DailyWorkEvidence {
  scheduleId: number;
  attendanceId: number | null;
  workDate: string;
  shiftName: string;
  plannedStart: string;
  plannedEnd: string;
  actualStart: string | null;
  actualEnd: string | null;
  actualBreakMinutes: number;
  plannedHours: number;
  workedHours: number;
  lateMinutes: number;
  earlyLeaveMinutes: number;
  lunchAllowanceForfeited: boolean;
  attendanceResult: string;
  verificationStatus: string;
  evidenceNote: string;
}

export interface EmployeePayrollWorkDetail {
  batchId: number;
  employeeId: number;
  employeeCode: string;
  fullName: string;
  departmentName: string;
  standardWorkDays: number;
  actualWorkDays: number;
  totalWorkedHours: number;
  totalLateMinutes: number;
  totalLateHours: number;
  forfeitedLunchDays: number;
  unexcusedAbsenceDays: number;
  paidLeaveDays: number;
  overtimeHours: number;
  dailyRecords: DailyWorkEvidence[];
}

// --- 2. PHỤ CẤP THEO THÁNG & PHÒNG BAN ---
export interface PeriodAllowanceItem {
  employeeAllowanceId: number;
  employeeId: number;
  employeeCode: string;
  fullName: string;
  departmentId: number;
  departmentName: string;
  allowanceCode: string;
  allowanceName: string;
  calculationBasis: string;
  amountType: string;
  configuredAmount: number;
  estimatedPeriodAmount: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  isTaxable: boolean;
  countsForSocialInsurance: boolean;
  conditionNote: string | null;
}

export interface DepartmentAllowanceSummary {
  departmentId: number;
  departmentCode: string;
  departmentName: string;
  employeeCount: number;
  allowanceCount: number;
  totalAllowanceAmount: number;
  amountByAllowanceType: Record<string, number>;
}

export interface PeriodAllowanceOverview {
  periodId: number;
  periodCode: string;
  periodStart: string;
  periodEnd: string;
  totalCompanyAllowance: number;
  departmentSummaries: DepartmentAllowanceSummary[];
  items: PeriodAllowanceItem[];
}

// --- 4. LỊCH SỬ LƯƠNG NHÂN VIÊN & CHI PHÍ PHÒNG BAN ---
export interface EmployeePayrollHistoryItem {
  payrollDetailId: number;
  batchId: number;
  periodId: number;
  periodCode: string;
  periodStart: string;
  periodEnd: string;
  versionNo: number;
  batchStatus: string;
  published: boolean;
  employeeId: number;
  employeeCode: string;
  fullName: string;
  departmentId: number | null;
  departmentName: string;
  positionName: string;
  actualWorkDays: number;
  overtimeHours: number;
  totalGross: number;
  totalDeduction: number;
  netSalary: number;
  payslipId: number | null;
}

export interface DepartmentPayrollCost {
  departmentId: number;
  departmentCode: string;
  departmentName: string;
  employeeCount: number;
  totalWorkSalary: number;
  totalOvertimePay: number;
  totalAllowance: number;
  totalBonus: number;
  totalGross: number;
  totalDeduction: number;
  totalNet: number;
  costSharePercent: number;
}

// --- 5. BÁO CÁO ĐIỀU KIỆN / LÝ DO CHẶN TÍNH LƯƠNG ---
export interface PayrollBlockerItem {
  category: string;
  categoryLabel: string;
  employeeId: number | null;
  employeeCode: string;
  fullName: string;
  departmentName: string;
  referenceCode: string;
  dateInfo: string;
  detail: string;
  actionRoute: string;
}

export interface PayrollReadinessReport {
  batchId: number | null;
  periodId: number;
  periodCode: string;
  readyForCalculate: boolean;
  readyForLock: boolean;
  summaryMessage: string;
  blockers: PayrollBlockerItem[];
}