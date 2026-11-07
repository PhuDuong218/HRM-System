import type { SalaryRateSegment } from './contract.model';

// Khớp với DTO backend: AllowanceTypeRequest/Response, EmployeeAllowanceRequest/Response, AllowanceCalculationResult.
export type AllowanceAmountType = 'FIXED' | 'PERCENT_BASE_SALARY';
export type AllowanceBasis = 'THEO_KY' | 'THEO_NGAY_CONG' | 'THEO_GIO';

export interface AllowanceTypeResponse {
  allowanceTypeId: number;
  typeCode: string;
  typeName: string;
  description: string | null;
  isActive: boolean;
  isTaxable: boolean;
  countsForSocialInsurance: boolean;
}

export interface AllowanceTypeRequest {
  typeCode: string;
  typeName: string;
  description: string | null;
  isActive: boolean | null;
  isTaxable: boolean;
  countsForSocialInsurance: boolean;
  reason: string;
}

export interface EmployeeAllowanceResponse {
  employeeAllowanceId: number;
  employeeId: number;
  allowanceTypeId: number;
  typeCode: string;
  typeName: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  calculationBasis: AllowanceBasis;
  amountType: AllowanceAmountType;
  amount: number; // Số tiền cố định hoặc tỷ lệ phần trăm
  conditionNote: string | null;
}

export interface EmployeeAllowanceRequest {
  allowanceTypeId: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  calculationBasis: AllowanceBasis;
  amountType: AllowanceAmountType;
  amount: number;
  conditionNote: string | null;
  reason: string;
}

export interface AllowanceChangeRequest {
  newAllowance: EmployeeAllowanceRequest;
}

export interface AllowanceEndRequest {
  stopFrom: string;
  reason: string;
}

export interface AllowanceLine {
  employeeAllowanceId: number;
  allowanceTypeId: number;
  typeCode: string;
  from: string;
  to: string;
  basis: AllowanceBasis;
  unitAmount: number;
  amountType: AllowanceAmountType;
  configuredAmount: number;
  salaryReference: SalaryRateSegment | null;
  effectiveDays: number;
  periodDays: number;
  eligibleUnits: number;
  rawAmount: number;
  isTaxable: boolean;
  countsForSocialInsurance: boolean;
  sourceReference: string | null;
  policyVersion: string | null;
}

export interface AllowanceCalculationResult {
  employeeId: number;
  period: { periodId: number; from: string; to: string };
  items: AllowanceLine[];
  totalRaw: number;
  totalRounded: number;
  roundingStatus: string;
}

export interface PayrollPeriod {
  periodId: number;
  periodCode: string;
  periodStart: string;
  periodEnd: string;
}
