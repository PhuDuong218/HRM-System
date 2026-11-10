export type DisciplineForm = 'KHIEN_TRACH' | 'KEO_DAI_NANG_LUONG' | 'CACH_CHUC' | 'SA_THAI';
export type DecisionType = 'KHEN_THUONG' | 'KY_LUAT' | 'BOI_THUONG';
export type DecisionApprovalStatus = 'CHO_DUYET' | 'DA_DUYET' | 'TU_CHOI';
export type DecisionPayrollEffect = 'NONE' | 'ADD' | 'DEDUCT';

export interface DecisionPageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface DecisionResponse {
  reviewBlocker?: string|null;
  decisionId: number;
  employeeId: number;
  decisionNumber: string;
  decisionType: DecisionType;
  content: string;
  issuedDate: string;
  issuedBy: number | null;
  approvalStatus: DecisionApprovalStatus;
  approvedBy: number | null;
  approvedAt: string | null;
  payrollEffect: DecisionPayrollEffect;
  amount: number | null;
  applyDate: string | null;
  disciplineForm: DisciplineForm | null;
  violationDate: string | null;
  salaryIncreaseDelayMonths: number | null;
}

// Backend lấy issuedBy/approvedBy từ phiên đăng nhập; client không gửi các trường này.
export interface DecisionRequest {
  rewardCode?: string | null;
  employeeId: number;
  decisionNumber: string;
  decisionType: DecisionType;
  content: string;
  issuedDate: string;
  payrollEffect: DecisionPayrollEffect;
  amount: number | null;
  applyDate: string | null;
  disciplineForm: DisciplineForm | null;
  violationDate: string | null;
  salaryIncreaseDelayMonths: number | null;
  reason: string;
}

export interface DecisionActionRequest {
  reason: string;
}

export interface DecisionPayrollLine {
  sourceKey: string;
  decisionId: number;
  decisionNumber: string;
  decisionType: DecisionType;
  applyDate: string;
  direction: Exclude<DecisionPayrollEffect, 'NONE'>;
  amount: number;
}

export interface DecisionPayrollResult {
  employeeId: number;
  periodId: number;
  from: string;
  to: string;
  items: DecisionPayrollLine[];
  totalAdd: number;
  totalDeduct: number;
  totalCompensation: number;
  netAdjustment: number;
  policyVersion: string | null;
}
