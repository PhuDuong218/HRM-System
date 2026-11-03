export type ContractStatus = 'DA_KY' | 'DA_KET_THUC';

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
}

export interface ContractCreateRequest {
  employeeId: number;
  contractTypeId: number;
  contractNumber: string;
  signedDate: string;
  startDate: string;
  endDate: string | null;
}

export interface ContractTypeResponse {
  contractTypeId: number;
  typeCode: string;
  typeName: string;
  isActive: boolean;
}

export interface ContractRenewRequest {
  newContract: ContractCreateRequest;
  reason: string;
}

export interface ContractTerminateRequest {
  terminationDate: string;
  reason: string;
}

export interface ContractTypeRequest {
  typeCode: string;
  typeName: string;
  isActive: boolean | null;
}