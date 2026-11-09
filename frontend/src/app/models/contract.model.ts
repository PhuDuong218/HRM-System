export type ContractTermKind = 'KHONG_XAC_DINH' | 'XAC_DINH' | 'THU_VIEC' | 'CTV_CAN_RA_SOAT';
export type ProbationGroup = 'QUAN_LY_DN' | 'CAO_DANG_TRO_LEN' | 'TRUNG_CAP_NGHIEP_VU' | 'CONG_VIEC_KHAC';
export type ContractStatus = 'DA_KY' | 'DA_KET_THUC' | 'DA_HUY';

export const contractTermKindLabels: Record<ContractTermKind, string> = {
  XAC_DINH: 'Xác định thời hạn',
  KHONG_XAC_DINH: 'Không xác định thời hạn',
  THU_VIEC: 'Thử việc',
  CTV_CAN_RA_SOAT: 'Cộng tác viên — cần rà soát',
};
export type TerminationReasonType = 'HET_HAN' | 'THOA_THUAN' | 'NLD_DON_PHUONG' | 'NSDLD_DON_PHUONG' | 'SA_THAI';

export type {ItemPage as PageResponse} from './page.model';

export interface ContractResponse {
  contractId: number;
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  contractTypeId: number;
  contractTypeName: string;
  contractNumber: string;
  signedDate: string;       
  startDate: string;
  endDate: string | null;
  status: ContractStatus;
  termKind: ContractTermKind;
  probationGroup: ProbationGroup | null;
  reasonType: TerminationReasonType | null;
}

export interface ExpiringContractResponse {
  contractId: number;
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  endDate: string;
  daysLeft: number;
}

export interface EmployeeWithoutActiveContractResponse {
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  lastContractEndDate: string | null;
  overdueDays: number | null;
  overdueOver30: boolean;
  nextContractStart: string | null;
}

export interface ContractCreateRequest {
  probationGroup?: ProbationGroup | null;
  employeeId: number;
  contractTypeId: number;
  contractNumber?: string;
  signedDate: string;
  startDate: string;
  endDate: string | null;
}

export interface ContractTypeResponse {
  contractTypeId: number;
  typeCode: string;
  typeName: string;
  isActive: boolean;
  termKind: ContractTermKind;
  maxMonths: number | null;
}

export interface ContractRenewRequest {
  newContract: ContractCreateRequest;
  reason: string;
}

export interface ContractTerminateRequest {
  terminationDate: string;
  reason: string;
  reasonType: TerminationReasonType;
}

export interface ContractTypeRequest {
  reason: string;
  typeCode: string;
  typeName: string;
  isActive: boolean | null;
  termKind: ContractTermKind;
  maxMonths: number | null;
}

export type PayBasis = 'THANG' | 'NGAY' | 'GIO';

export interface SalaryTermResponse {
  officialReferenceSalary: number | null;
  officialReferencePayBasis: PayBasis | null;
  warnings: string[];
  salaryTermId: number;
  contractId: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  baseSalary: number;
  payBasis: PayBasis;
  documentReference: string | null;
}

export interface SalaryTermRequest {
  officialReferenceSalary?: number | null;
  officialReferencePayBasis?: PayBasis | null;
  effectiveFrom: string;
  effectiveTo: string | null;
  baseSalary: number;
  payBasis: PayBasis;
  documentReference: string | null;
  reason: string;
}

export interface SalaryRateSegment {
  employeeId: number;
  contractId: number;
  salaryTermId: number;
  from: string;
  to: string;
  baseSalary: number;
  payBasis: PayBasis;
}

export interface SalaryTermChangeRequest {
  newTerm: SalaryTermRequest;
}
