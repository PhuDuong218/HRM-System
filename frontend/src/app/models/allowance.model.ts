// Khớp với DTO backend: AllowanceTypeRequest/Response, EmployeeAllowanceRequest/Response, AllowanceCalculationResult.
export type AllowanceBasis = 'THEO_KY' | 'THEO_NGAY_CONG' | 'THEO_GIO';

export interface AllowanceTypeResponse {
  allowanceTypeId: number;
  typeCode: string;
  typeName: string;
  description: string | null;
  isActive: boolean;
}

export interface AllowanceTypeRequest {
  typeCode: string;
  typeName: string;
  description: string | null;
  isActive: boolean | null;
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
  amount: number; // ĐƠN GIÁ theo calculationBasis, KHÔNG phải tiền cuối kỳ
  conditionNote: string | null;
}

export interface EmployeeAllowanceRequest {
  allowanceTypeId: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  calculationBasis: AllowanceBasis;
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
  eligibleUnits: number;
  rawAmount: number;
  sourceReference: string | null;
  policyVersion: string | null;
}

export interface AllowanceCalculationResult {
  employeeId: number;
  period: { periodId: number; from: string; to: string };
  items: AllowanceLine[];
  totalRaw: number;
  roundingStatus: string;
}

export interface PayrollPeriod {
  periodId: number;
  periodCode: string;
  periodStart: string;
  periodEnd: string;
}
