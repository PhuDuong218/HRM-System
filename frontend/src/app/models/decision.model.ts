export type DecisionType = 'KHEN_THUONG' | 'KY_LUAT';
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
}

// Backend lấy issuedBy/approvedBy từ phiên đăng nhập; client không gửi các trường này.
export interface DecisionRequest {
  employeeId: number;
  decisionNumber: string;
  decisionType: DecisionType;
  content: string;
  issuedDate: string;
  payrollEffect: DecisionPayrollEffect;
  amount: number | null;
  applyDate: string | null;
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
  netAdjustment: number;
  policyVersion: string | null;
}