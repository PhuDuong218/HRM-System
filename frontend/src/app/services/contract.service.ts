import { Injectable, inject } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { API_BASE_URL } from "./api.config";
import {
  ContractCreateRequest,
  ExpiringContractResponse,
  EmployeeWithoutActiveContractResponse,
  ContractResponse,
  ContractStatus,
  ContractTypeResponse,
  ContractRenewRequest,
  ContractTerminateRequest,
  PageResponse,
  ContractTypeRequest,
  SalaryTermResponse,
  SalaryTermRequest,
  SalaryRateSegment,
  SalaryTermChangeRequest,
} from "../models/contract.model";

@Injectable({ providedIn: "root" })
export class ContractService {
  private http = inject(HttpClient);

  search(page = 0, employeeId?: number, status?: ContractStatus, q?: string, from?: string, to?: string) {
    let params = new HttpParams().set("page", page).set("size", 10);
    if (employeeId) params = params.set("employeeId", employeeId);
    if (status) params = params.set("status", status);
    if (q?.trim()) params = params.set("q", q.trim());
    if (from) params = params.set("from", from);
    if (to) params = params.set("to", to);

    return this.http.get<PageResponse<ContractResponse>>(
      `${API_BASE_URL}/contracts`,
      { params },
    );
  }

  get(id: number) {
    return this.http.get<ContractResponse>(`${API_BASE_URL}/contracts/${id}`);
  }

  expiring(days = 30, page = 0, size = 10) {
    return this.http.get<PageResponse<ExpiringContractResponse>>(
      `${API_BASE_URL}/contracts/expiring`, { params: { days, page, size } },
    );
  }

  withoutActiveContract(page = 0, size = 10) {
    return this.http.get<PageResponse<EmployeeWithoutActiveContractResponse>>(
      `${API_BASE_URL}/employees/without-active-contract`, { params: { page, size } },
    );
  }

  types() {
    return this.http.get<ContractTypeResponse[]>(
      `${API_BASE_URL}/contract-types`,
    );
  }

  create(request: ContractCreateRequest) {
    return this.http.post<ContractResponse>(
      `${API_BASE_URL}/contracts`,
      request,
    );
  }

  renew(id: number, request: ContractRenewRequest) {
    return this.http.post<ContractResponse>(
      `${API_BASE_URL}/contracts/${id}/renew`,
      request,
    );
  }

  terminate(id: number, request: ContractTerminateRequest) {
    return this.http.post<ContractResponse>(
      `${API_BASE_URL}/contracts/${id}/terminate`,
      request,
    );
  }

  createType(request: ContractTypeRequest) {
    return this.http.post<ContractTypeResponse>(
      `${API_BASE_URL}/contract-types`,
      request,
    );
  }

  updateType(id: number, request: ContractTypeRequest) {
    return this.http.put<ContractTypeResponse>(
      `${API_BASE_URL}/contract-types/${id}`,
      request,
    );
  }

  deactivateType(id: number) {
    return this.http.delete<void>(`${API_BASE_URL}/contract-types/${id}`);
  }

  salaryTerms(contractId: number) {
    return this.http.get<SalaryTermResponse[]>(
      `${API_BASE_URL}/contracts/${contractId}/salary-terms`,
    );
  }

  createSalaryTerm(contractId: number, request: SalaryTermRequest) {
    return this.http.post<SalaryTermResponse>(
      `${API_BASE_URL}/contracts/${contractId}/salary-terms`,
      request,
    );
  }

  salaryRates(employeeId: number, from: string, to: string) {
    return this.http.get<SalaryRateSegment[]>(
      `${API_BASE_URL}/employees/${employeeId}/salary-rates`,
      { params: { from, to } },
    );
  }

  updateSalaryTerm(
    contractId: number,
    salaryTermId: number,
    request: SalaryTermRequest,
  ) {
    return this.http.put<SalaryTermResponse>(
      `${API_BASE_URL}/contracts/${contractId}/salary-terms/${salaryTermId}`,
      request,
    );
  }

  deleteSalaryTerm(contractId: number, salaryTermId: number, reason: string) {
    return this.http.delete<void>(
      `${API_BASE_URL}/contracts/${contractId}/salary-terms/${salaryTermId}`,
      { params: { reason } },
    );
  }

  changeSalaryRate(
    contractId: number,
    salaryTermId: number,
    request: SalaryTermChangeRequest,
  ) {
    return this.http.post<SalaryTermResponse>(
      `${API_BASE_URL}/contracts/${contractId}/salary-terms/${salaryTermId}/change-rate`,
      request,
    );
  }
}
