import { TranslatePipe } from '../../shared/translate.pipe';
import { LanguageService } from "../../services/language.service";
import { Pagination } from "../../shared/pagination";
import {
  EmployeeService,
  Employee,
  errorMessage,
} from "../../services/employee.service";
import { RecordWorkspace } from "../../shared/record-workspace";
import { Component, DestroyRef, OnInit, inject } from "@angular/core";
import { HttpErrorResponse } from "@angular/common/http";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { Subscription } from "rxjs";
import { DecimalPipe } from "@angular/common";

import { ContractService } from "../../services/contract.service";
import {
  ContractCreateRequest,
  ContractResponse,
  ContractStatus,
  ContractTypeResponse,
  ContractRenewRequest,
  ContractTerminateRequest,
  SalaryTermResponse,
  SalaryTermRequest,
  PayBasis,
} from "../../models/contract.model";

@Component({
  selector: "app-contracts",
  standalone: true,
  imports: [Pagination, RecordWorkspace, DecimalPipe ,TranslatePipe],
  styleUrl: "./contracts.css",
  templateUrl: "./contracts.html",
})
export class ContractsPage implements OnInit {
  language = inject(LanguageService);
  private listRequest?: Subscription;
  selectedId: number | null = null;
  private api = inject(ContractService);
  private personnel = inject(EmployeeService);
  employees: Employee[] = [];
  employeesLoading = false;
  employeesError = "";
  loadEmployees(): void {
    if (this.employeesLoading) return;
    this.employeesLoading = true;
    this.employeesError = "";
    // Unpaged endpoint applies HR scope on the server, and returns the full permitted selection.
    this.personnel
      .list(false, 0, "", "", "")
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.employees = Array.isArray(result) ? result : result.content;
          this.employeesLoading = false;
        },
        error: (error) => {
          this.employeesError = errorMessage(error);
          this.employeesLoading = false;
        },
      });
  }

  rows: ContractResponse[] = [];
  loading = false;
  error = "";
  page = 0;
  totalPages = 0;

  employeeId?: number;
  status?: ContractStatus;
  q = "";

  lifecycleMode: "renew" | "terminate" | null = null;
  lifecycleContract: ContractResponse | null = null;
  lifecycleError = "";

  renewTypes: ContractTypeResponse[] = [];
  renewTypesLoading = false;

  ngOnInit(): void {
    this.load(0);
    this.loadEmployees();
  }

  load(page: number): void {
    this.listRequest?.unsubscribe();
    this.loading = true;
    this.error = "";

    this.listRequest = this.api
      .search(page, this.employeeId, this.status, this.q)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.rows = result.items;
          this.page = result.page;
          this.totalPages = result.totalPages;
          this.loading = false;
        },
        error: (e: HttpErrorResponse) => {
          this.loading = false;
          this.rows = [];
          this.error =
            e.error?.loi_chi_tiet ?? "Không tải được danh sách hợp đồng.";
        },
      });
  }

  search(employeeIdText: string, statusText: string, q: string): void {
    this.employeeId = employeeIdText ? Number(employeeIdText) : undefined;
    this.status = statusText ? (statusText as ContractStatus) : undefined;
    this.q = q;
    this.load(0); // đổi bộ lọc thì quay về trang đầu
  }

  clearFilters(
    employee: HTMLSelectElement,
    status: HTMLSelectElement,
    keyword: HTMLInputElement,
  ): void {
    employee.value = "";
    status.value = "";
    keyword.value = "";
    this.employeeId = undefined;
    this.status = undefined;
    this.q = "";
    this.load(0);
  }

  private destroyRef = inject(DestroyRef);
  private detailRequest?: Subscription;

  // Xem chi tiết
  selected: ContractResponse | null = null;
  detailLoading = false;
  detailError = "";

  // Tạo hợp đồng
  showCreate = false;
  typesLoading = false;
  activeTypes: ContractTypeResponse[] = [];
  saving = false;
  createError = "";
  message = "";

  viewDetail(id: number): void {
    if (this.saving || this.renewTypesLoading) return;
    this.clearSalaryTerms();
    this.cancelLifecycle();
    this.cancelCreate();
    // Khi bấm liên tiếp hai hợp đồng, hủy request chi tiết trước.
    this.detailRequest?.unsubscribe();

    this.selectedId = id;
    this.selected = null;
    this.detailError = "";
    this.detailLoading = true;

    this.detailRequest = this.api
      .get(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (contract) => {
          this.selected = contract;
          this.detailLoading = false;

          this.loadSalaryTerms(contract.contractId);
        },
        error: (e: HttpErrorResponse) => {
          this.detailLoading = false;
          this.selectedId = null;
          this.detailError =
            e.status === 404
              ? "Hợp đồng không còn tồn tại hoặc không tìm thấy."
              : this.readError(e, "Không tải được chi tiết hợp đồng.");
        },
      });
  }

  closeWorkspace(): void {
    if (this.saving || this.renewTypesLoading) return;
    this.cancelCreate();
    this.closeDetail();
  }

  closeDetail(): void {
    if (this.saving || this.renewTypesLoading) return;
    this.clearSalaryTerms();
    this.cancelLifecycle();

    this.detailRequest?.unsubscribe();
    this.selectedId = null;
    this.selected = null;
    this.detailLoading = false;
    this.detailError = "";
  }

  private readError(error: HttpErrorResponse, fallback: string): string {
    if (error.status === 0) {
      return "Không kết nối được backend. Hãy kiểm tra server và proxy.";
    }

    if (error.status === 401) {
      return "Backend yêu cầu đăng nhập.";
    }

    if (error.status === 403) {
      return "Bạn không có quyền thực hiện thao tác này.";
    }

    const detail = error.error?.loi_chi_tiet;

    return typeof detail === "string" && detail.trim() ? detail : fallback;
  }

  openCreate(): void {
    if (this.saving || this.typesLoading || this.renewTypesLoading) return;
    this.closeDetail();
    if (this.renewTypesLoading) return;
    this.cancelLifecycle();

    if (this.saving || this.typesLoading) return;

    this.showCreate = true;
    this.createError = "";
    this.message = "";
    this.activeTypes = [];
    this.typesLoading = true;

    this.api
      .types()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (types) => {
          this.activeTypes = types.filter((type) => type.isActive);
          this.typesLoading = false;
        },
        error: (e: HttpErrorResponse) => {
          this.typesLoading = false;
          this.createError = this.readError(
            e,
            "Không tải được danh mục loại hợp đồng.",
          );
        },
      });
  }

  cancelCreate(): void {
    if (this.saving) return;

    this.showCreate = false;
    this.createError = "";
  }

  saveContract(event: Event): void {
    event.preventDefault();

    if (this.saving || this.typesLoading) return;

    const form = event.target as HTMLFormElement;
    if (!form.reportValidity()) return;

    const data = new FormData(form);
    const value = (name: string) => String(data.get(name) ?? "").trim();

    const employeeId = Number(value("employeeId"));
    const contractTypeId = Number(value("contractTypeId"));
    const contractNumber = value("contractNumber");
    const signedDate = value("signedDate");
    const startDate = value("startDate");
    const endDate = value("endDate") || null;

    this.createError = "";
    this.message = "";

    if (
      !Number.isSafeInteger(employeeId) ||
      employeeId <= 0 ||
      !Number.isSafeInteger(contractTypeId) ||
      contractTypeId <= 0
    ) {
      this.createError = "Nhân viên và loại hợp đồng phải có ID hợp lệ.";
      return;
    }

    if (!contractNumber) {
      this.createError = "Vui lòng nhập số hợp đồng.";
      return;
    }

    // Chuỗi YYYY-MM-DD có thể so sánh trực tiếp theo thứ tự ngày.
    if (endDate && endDate < startDate) {
      this.createError = "Ngày kết thúc không được trước ngày bắt đầu.";
      return;
    }

    const request: ContractCreateRequest = {
      employeeId,
      contractTypeId,
      contractNumber,
      signedDate,
      startDate,
      endDate,
    };

    this.saving = true;

    this.api
      .create(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (created) => {
          this.saving = false;
          this.cancelCreate();
          this.message = `Đã tạo hợp đồng ${created.contractNumber}.`;

          this.load(0);
          this.viewDetail(created.contractId);
        },
        error: (e: HttpErrorResponse) => {
          this.saving = false;
          this.createError = this.readError(e, "Không tạo được hợp đồng.");
        },
      });
  }

  //Phương thức mở form
  openLifecycle(mode: "renew" | "terminate"): void {
    if (!this.selected || this.saving || this.renewTypesLoading) {
      return;
    }

    const contract = this.selected;

    this.lifecycleError = "";
    this.message = "";

    if (mode === "renew" && !contract.endDate) {
      this.lifecycleError =
        "Hợp đồng không xác định thời hạn không dùng chức năng gia hạn.";
      return;
    }

    if (mode === "terminate" && contract.status !== "DA_KY") {
      this.lifecycleError = "Chỉ chấm dứt hợp đồng có trạng thái Đã ký.";
      return;
    }

    this.showCreate = false;
    this.lifecycleContract = contract;
    this.lifecycleMode = mode;

    if (mode === "renew") {
      this.renewTypes = [];
      this.renewTypesLoading = true;

      this.api
        .types()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (types) => {
            this.renewTypes = types.filter((type) => type.isActive);
            this.renewTypesLoading = false;
          },
          error: (e: HttpErrorResponse) => {
            this.renewTypesLoading = false;
            this.lifecycleError = this.readError(
              e,
              "Không tải được loại hợp đồng.",
            );
          },
        });
    }
  }

  cancelLifecycle(): void {
    if (this.saving || this.renewTypesLoading) return;

    this.lifecycleMode = null;
    this.lifecycleContract = null;
    this.lifecycleError = "";
  }

  //Phương thức lưu gia hạn
  saveRenew(event: Event): void {
    event.preventDefault();

    const old = this.lifecycleContract;

    if (
      !old ||
      !old.endDate ||
      this.lifecycleMode !== "renew" ||
      this.saving ||
      this.renewTypesLoading
    ) {
      return;
    }

    const form = event.target as HTMLFormElement;
    if (!form.reportValidity()) return;

    const data = new FormData(form);
    const value = (name: string) => String(data.get(name) ?? "").trim();

    const contractTypeId = Number(value("contractTypeId"));
    const contractNumber = value("contractNumber");
    const signedDate = value("signedDate");
    const startDate = value("startDate");
    const endDate = value("endDate") || null;
    const reason = value("reason");

    this.lifecycleError = "";
    this.message = "";

    if (
      !this.renewTypes.some((type) => type.contractTypeId === contractTypeId)
    ) {
      this.lifecycleError = "Hãy chọn loại hợp đồng đang hoạt động.";
      return;
    }

    if (!contractNumber || !reason) {
      this.lifecycleError = "Cần nhập số hợp đồng mới và lý do gia hạn.";
      return;
    }

    if (startDate <= old.endDate) {
      this.lifecycleError =
        "Ngày bắt đầu hợp đồng mới phải sau ngày cuối của hợp đồng cũ.";
      return;
    }

    if (endDate && endDate < startDate) {
      this.lifecycleError = "Ngày kết thúc không được trước ngày bắt đầu.";
      return;
    }

    const request: ContractRenewRequest = {
      newContract: {
        employeeId: old.employeeId,
        contractTypeId,
        contractNumber,
        signedDate,
        startDate,
        endDate,
      },
      reason,
    };

    this.saving = true;

    this.api
      .renew(old.contractId, request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (created) => {
          this.saving = false;
          this.cancelLifecycle();

          this.message = `Đã gia hạn thành hợp đồng mới ${created.contractNumber}.`;

          this.load(this.page);
          this.viewDetail(created.contractId);
        },
        error: (e: HttpErrorResponse) => {
          this.saving = false;
          this.lifecycleError = this.readError(
            e,
            "Không gia hạn được hợp đồng.",
          );
        },
      });
  }

  //Phương thức chấm dứt
  saveTerminate(event: Event): void {
    event.preventDefault();

    const contract = this.lifecycleContract;

    if (!contract || this.lifecycleMode !== "terminate" || this.saving) {
      return;
    }

    const form = event.target as HTMLFormElement;
    if (!form.reportValidity()) return;

    const data = new FormData(form);
    const terminationDate = String(data.get("terminationDate") ?? "").trim();
    const reason = String(data.get("reason") ?? "").trim();

    this.lifecycleError = "";
    this.message = "";

    if (!terminationDate || !reason) {
      this.lifecycleError = "Cần nhập ngày chấm dứt và lý do.";
      return;
    }

    if (
      terminationDate < contract.startDate ||
      (contract.endDate !== null && terminationDate > contract.endDate)
    ) {
      this.lifecycleError = "Ngày chấm dứt phải nằm trong thời hạn hợp đồng.";
      return;
    }

    const request: ContractTerminateRequest = {
      terminationDate,
      reason,
    };

    this.saving = true;

    this.api
      .terminate(contract.contractId, request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.saving = false;
          this.cancelLifecycle();

          this.selected = updated;
          this.message = `Đã ghi nhận ngày cuối còn hiệu lực: ${updated.endDate}.`;

          this.load(this.page);
        },
        error: (e: HttpErrorResponse) => {
          this.saving = false;
          this.lifecycleError = this.readError(
            e,
            "Không chấm dứt được hợp đồng.",
          );
        },
      });
  }

  salaryTerms: SalaryTermResponse[] = [];
  salaryTermsLoading = false;
  salaryTermsError = "";

  private salaryTermsRequest?: Subscription;

  loadSalaryTerms(contractId: number): void {
    this.salaryTermsRequest?.unsubscribe();

    this.salaryTerms = [];
    this.salaryTermsError = "";
    this.salaryTermsLoading = true;

    this.salaryTermsRequest = this.api
      .salaryTerms(contractId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rows) => {
          this.salaryTerms = rows;
          this.salaryTermsLoading = false;
        },
        error: (error: HttpErrorResponse) => {
          this.salaryTerms = [];
          this.salaryTermsLoading = false;
          this.salaryTermsError = this.readError(
            error,
            "Không tải được lịch sử mức lương.",
          );
        },
      });
  }

  private clearSalaryTerms(): void {
    this.salaryTermsRequest?.unsubscribe();

    this.salaryTerms = [];
    this.salaryTermsLoading = false;
    this.salaryTermsError = "";

    this.showSalaryForm = false;
    this.salaryFormError = "";
    this.salaryMessage = "";
  }

  payBasisLabel(value: SalaryTermResponse["payBasis"]): string {
    const labels = {
      THANG: "Tháng",
      NGAY: "Ngày",
      GIO: "Giờ",
    };

    return labels[value];
  }

  showSalaryForm = false;
  salaryFormError = "";
  salaryMessage = "";

  openSalaryForm(): void {
    if (this.saving || !this.selected) return;

    if (this.selected.status !== "DA_KY") {
      this.salaryFormError =
        "Chỉ thêm mức lương cho hợp đồng có trạng thái Đã ký.";
      return;
    }

    this.salaryFormError = "";
    this.salaryMessage = "";
    this.showSalaryForm = true;
  }

  cancelSalaryForm(): void {
    if (this.saving) return;

    this.showSalaryForm = false;
    this.salaryFormError = "";
  }

  saveSalaryTerm(event: Event): void {
    event.preventDefault();

    const contract = this.selected;

    if (!contract || this.saving || !this.showSalaryForm) return;

    const form = event.target as HTMLFormElement;

    if (!form.reportValidity()) return;

    const data = new FormData(form);
    const value = (name: string): string => String(data.get(name) ?? "").trim();

    const effectiveFrom = value("effectiveFrom");
    const effectiveTo = value("effectiveTo") || null;
    const salaryText = value("baseSalary");
    const baseSalary = Number(salaryText);
    const payBasisText = value("payBasis");
    const documentReference = value("documentReference") || null;
    const reason = value("reason");

    this.salaryFormError = "";
    this.salaryMessage = "";

    if (!effectiveFrom) {
      this.salaryFormError = "Vui lòng chọn ngày bắt đầu.";
      return;
    }

    // Backend yêu cầu ngày cuối lớn hơn ngày bắt đầu.
    if (effectiveTo && effectiveTo <= effectiveFrom) {
      this.salaryFormError = "Ngày cuối phải sau ngày bắt đầu.";
      return;
    }

    if (effectiveFrom < contract.startDate) {
      this.salaryFormError =
        "Ngày bắt đầu mức lương không được trước ngày bắt đầu hợp đồng.";
      return;
    }

    if (
      contract.endDate &&
      (effectiveFrom > contract.endDate ||
        (effectiveTo !== null && effectiveTo > contract.endDate))
    ) {
      this.salaryFormError =
        "Khoảng mức lương không được vượt thời hạn hợp đồng.";
      return;
    }

    if (
      !/^\d{1,15}(\.\d{1,4})?$/.test(salaryText) ||
      !Number.isFinite(baseSalary)
    ) {
      this.salaryFormError =
        "Mức lương phải không âm, tối đa 15 chữ số nguyên và 4 chữ số thập phân.";
      return;
    }

    if (!["THANG", "NGAY", "GIO"].includes(payBasisText)) {
      this.salaryFormError = "Vui lòng chọn đơn vị tính lương.";
      return;
    }

    if (!reason) {
      this.salaryFormError = "Vui lòng nhập lý do.";
      return;
    }

    const request: SalaryTermRequest = {
      effectiveFrom,
      effectiveTo,
      baseSalary,
      payBasis: payBasisText as PayBasis,
      documentReference,
      reason,
    };

    this.saving = true;

    this.api
      .createSalaryTerm(contract.contractId, request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving = false;
          this.showSalaryForm = false;
          this.salaryMessage = "Đã thêm mức lương.";

          this.loadSalaryTerms(contract.contractId);
        },
        error: (error: HttpErrorResponse) => {
          this.saving = false;
          this.salaryFormError = this.readError(
            error,
            "Không thêm được mức lương.",
          );
        },
      });
  }
}
