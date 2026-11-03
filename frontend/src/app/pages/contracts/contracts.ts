import { TranslatePipe } from "../../shared/translate.pipe";
import { LanguageService } from "../../services/language.service";
import { Pagination } from "../../shared/pagination";
import {
  EmployeeService,
  Employee,
  errorMessage,
} from "../../services/employee.service";
import { RecordWorkspace } from "../../shared/record-workspace";
import { Component, DestroyRef, ElementRef, OnInit, ViewChild, inject } from "@angular/core";
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
  SalaryRateSegment,
} from "../../models/contract.model";

type SalaryTermDraft = {
  effectiveFrom: string;
  effectiveTo: string;
  baseSalary: string;
  payBasis: PayBasis | "";
  documentReference: string;
};

@Component({
  selector: "app-contracts",
  standalone: true,
  imports: [Pagination, RecordWorkspace, DecimalPipe, TranslatePipe],
  styleUrl: "./contracts.css",
  templateUrl: "./contracts.html",
})
export class ContractsPage implements OnInit {
  @ViewChild("createFormPanel") private createFormPanel?: ElementRef<HTMLElement>;
  @ViewChild("salaryFormPanel") private salaryFormPanel?: ElementRef<HTMLElement>;
  @ViewChild("lifecycleFormPanel") private lifecycleFormPanel?: ElementRef<HTMLElement>;

  private focusPanel(getPanel: () => ElementRef<HTMLElement> | undefined): void {
    requestAnimationFrame(() => {
      const element = getPanel()?.nativeElement;
      if (!element?.isConnected) return;
      element.scrollIntoView({ behavior: "smooth", block: "nearest" });
      element.focus({ preventScroll: true });
    });
  }

  language = inject(LanguageService);
  private listRequest?: Subscription;
  private readonly contractPageCache = new Map<
    string,
    { rows: ContractResponse[]; page: number; totalPages: number; savedAt: number }
  >();
  private readonly contractCacheTtlMs = 30_000;
  private readonly maxCachedContractPages = 20;
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
    this.error = "";

    const cacheKey = JSON.stringify({
      page,
      employeeId: this.employeeId ?? null,
      status: this.status ?? null,
      q: this.q.trim(),
    });
    const cached = this.contractPageCache.get(cacheKey);

    if (cached && Date.now() - cached.savedAt < this.contractCacheTtlMs) {
      this.contractPageCache.delete(cacheKey);
      this.contractPageCache.set(cacheKey, cached);
      this.rows = cached.rows;
      this.page = cached.page;
      this.totalPages = cached.totalPages;
      this.loading = false;
      return;
    }

    this.contractPageCache.delete(cacheKey);
    this.loading = true;

    this.listRequest = this.api
      .search(page, this.employeeId, this.status, this.q)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.rows = result.items;
          this.page = result.page;
          this.totalPages = result.totalPages;
          this.loading = false;
          this.contractPageCache.set(cacheKey, {
            rows: result.items,
            page: result.page,
            totalPages: result.totalPages,
            savedAt: Date.now(),
          });
          while (this.contractPageCache.size > this.maxCachedContractPages) {
            const oldestKey = this.contractPageCache.keys().next().value;
            if (oldestKey === undefined) break;
            this.contractPageCache.delete(oldestKey);
          }
        },
        error: (e: HttpErrorResponse) => {
          this.loading = false;
          this.rows = [];
          this.error =
            e.error?.loi_chi_tiet ?? "Không tải được danh sách hợp đồng.";
        },
      });
  }

  private clearContractPageCache(): void {
    this.contractPageCache.clear();
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

    // The list endpoint returns the same ContractResponse used by GET /contracts/{id}.
    // Reuse the row already loaded for this page instead of making a duplicate DB read.
    const listedContract = this.rows.find((row) => row.contractId === id);
    if (listedContract) {
      this.selected = listedContract;
      this.detailLoading = false;
      this.loadSalaryTerms(listedContract.contractId);
      return;
    }

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
    this.focusPanel(() => this.createFormPanel);
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
          this.clearContractPageCache();
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
    this.focusPanel(() => this.lifecycleFormPanel);

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
          this.clearContractPageCache();
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
          this.clearContractPageCache();
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
    this.salaryFormMode = 'create';
    this.selectedSalaryTerm = null;
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

  // ===== QUẢN LÝ ĐIỀU KHOẢN MỨC LƯƠNG (FE-014 / D-07, D-08) =====
  showSalaryForm = false;
  salaryFormMode: "create" | "edit" | "change" = "create";
  selectedSalaryTerm: SalaryTermResponse | null = null;
  salaryFormError = "";
  salaryMessage = "";
  salaryDraft: SalaryTermDraft = this.emptySalaryDraft();

  private emptySalaryDraft(): SalaryTermDraft {
    return { effectiveFrom: "", effectiveTo: "", baseSalary: "", payBasis: "", documentReference: "" };
  }

  private draftFromTerm(term: SalaryTermResponse, effectiveFrom = term.effectiveFrom): SalaryTermDraft {
    return {
      effectiveFrom,
      effectiveTo: term.effectiveTo ?? "",
      baseSalary: String(term.baseSalary),
      payBasis: term.payBasis,
      documentReference: term.documentReference ?? "",
    };
  }

  private localToday(): string {
    const date = new Date();
    return date.getFullYear() + "-" + String(date.getMonth() + 1).padStart(2, "0") + "-" + String(date.getDate()).padStart(2, "0");
  }

  private addDays(value: string, days: number): string {
    const date = new Date(value + "T00:00:00");
    date.setDate(date.getDate() + days);
    return date.getFullYear() + "-" + String(date.getMonth() + 1).padStart(2, "0") + "-" + String(date.getDate()).padStart(2, "0");
  }

  private maxDate(values: string[]): string {
    return values.reduce((latest, value) => (value > latest ? value : latest));
  }

  minSalaryEffectiveFrom(): string {
    const contract = this.selected;
    if (!contract) return "";
    const minimums = [contract.startDate];
    if (this.salaryFormMode === "edit") minimums.push(this.addDays(this.localToday(), 1));
    if (this.salaryFormMode === "change" && this.selectedSalaryTerm) {
      // Backend must leave at least one day in the old range before the new rate starts.
      minimums.push(this.localToday(), this.addDays(this.selectedSalaryTerm.effectiveFrom, 2));
    }
    return this.maxDate(minimums);
  }

  canEditSalaryTerm(term: SalaryTermResponse): boolean {
    return Boolean(
      this.selected?.status === "DA_KY" &&
        term.contractId === this.selected.contractId &&
        term.effectiveFrom > this.localToday(),
    );
  }

  canChangeSalaryTerm(term: SalaryTermResponse): boolean {
    const contract = this.selected;
    if (!contract || contract.status !== "DA_KY" || term.contractId !== contract.contractId) return false;
    const earliest = this.maxDate([
      this.localToday(),
      this.addDays(term.effectiveFrom, 2),
      contract.startDate,
    ]);
    return (!contract.endDate || earliest <= contract.endDate) &&
      (!term.effectiveTo || earliest < term.effectiveTo);
  }

  salaryFormTitle(): string {
    if (this.salaryFormMode === "edit") return "Sửa điều khoản lương";
    if (this.salaryFormMode === "change") return "Đổi mức lương";
    return "Thêm mức lương";
  }

  salaryFormDescription(): string {
    if (this.salaryFormMode === "edit") return "Chỉ chỉnh sửa điều khoản chưa có hiệu lực; lịch sử đã hiệu lực được giữ nguyên.";
    if (this.salaryFormMode === "change") return "Tạo mức mới và giữ dòng cũ trong lịch sử. Ngày kết thúc phải giữ nguyên.";
    return "Nhập mức lương và khoảng thời gian áp dụng cho hợp đồng này.";
  }

  salaryFormSubmitLabel(): string {
    if (this.salaryFormMode === "edit") return "Cập nhật mức lương";
    if (this.salaryFormMode === "change") return "Xác nhận đổi mức";
    return "Lưu mức lương";
  }

  openSalaryForm(): void {
    if (this.saving || !this.selected) return;
    if (this.selected.status !== "DA_KY") {
      this.salaryFormError = "Chỉ thêm mức lương cho hợp đồng có trạng thái Đã ký.";
      return;
    }
    this.salaryFormMode = "create";
    this.selectedSalaryTerm = null;
    this.salaryDraft = this.emptySalaryDraft();
    this.salaryFormError = "";
    this.salaryMessage = "";
    this.showSalaryForm = true;
    this.focusPanel(() => this.salaryFormPanel);
  }

  openSalaryEdit(term: SalaryTermResponse): void {
    if (this.saving || !this.selected) return;
    if (!this.canEditSalaryTerm(term)) {
      this.salaryFormError = "Chỉ sửa được điều khoản lương chưa có hiệu lực của hợp đồng đã ký.";
      return;
    }
    this.salaryFormMode = "edit";
    this.selectedSalaryTerm = term;
    this.salaryDraft = this.draftFromTerm(term);
    this.salaryFormError = "";
    this.salaryMessage = "";
    this.showSalaryForm = true;
    this.focusPanel(() => this.salaryFormPanel);
  }

  openSalaryRateChange(term: SalaryTermResponse): void {
    if (this.saving || !this.selected) return;
    if (!this.canChangeSalaryTerm(term)) {
      this.salaryFormError = "Không thể đổi mức cho khoảng này; hãy kiểm tra ngày bắt đầu, ngày kết thúc hợp đồng và ngày kết thúc mức lương.";
      return;
    }
    this.salaryFormMode = "change";
    this.selectedSalaryTerm = term;
    this.salaryDraft = this.draftFromTerm(term, "");
    this.salaryFormError = "";
    this.salaryMessage = "";
    this.showSalaryForm = true;
    this.focusPanel(() => this.salaryFormPanel);
  }

  deleteSalaryTerm(term: SalaryTermResponse): void {
    if (this.saving || !this.selected) return;
    if (!this.canEditSalaryTerm(term)) {
      this.salaryFormError = "Chỉ xóa được điều khoản lương chưa có hiệu lực của hợp đồng đã ký.";
      return;
    }
    const contract = this.selected;
    const reason = window.prompt("Nhập lý do xóa điều khoản lương chưa có hiệu lực (tối đa 500 ký tự):");
    if (reason === null) return;
    if (!reason.trim()) {
      this.salaryFormError = "Vui lòng nhập lý do xóa điều khoản.";
      return;
    }
    if (reason.trim().length > 500) {
      this.salaryFormError = "Lý do không được vượt quá 500 ký tự.";
      return;
    }
    if (!window.confirm("Xóa điều khoản lương từ ngày " + term.effectiveFrom + "?")) return;

    this.saving = true;
    this.salaryFormError = "";
    this.salaryMessage = "";
    this.api
      .deleteSalaryTerm(contract.contractId, term.salaryTermId, reason.trim())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving = false;
          this.salaryMessage = "Đã xóa điều khoản lương chưa hiệu lực.";
          this.loadSalaryTerms(contract.contractId);
        },
        error: (error: HttpErrorResponse) => {
          this.saving = false;
          this.salaryFormError = this.readError(error, "Không xóa được điều khoản lương.");
        },
      });
  }

  cancelSalaryForm(): void {
    if (this.saving) return;
    this.showSalaryForm = false;
    this.salaryFormMode = "create";
    this.selectedSalaryTerm = null;
    this.salaryDraft = this.emptySalaryDraft();
    this.salaryFormError = "";
  }

  saveSalaryTerm(event: Event): void {
    event.preventDefault();
    const contract = this.selected;
    if (!contract || this.saving || !this.showSalaryForm) return;
    if (contract.status !== "DA_KY") {
      this.salaryFormError = "Chỉ ghi nhận mức lương cho hợp đồng đã ký.";
      return;
    }
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
    if (effectiveTo && effectiveTo <= effectiveFrom) {
      this.salaryFormError = "Ngày cuối phải sau ngày bắt đầu.";
      return;
    }
    if (effectiveFrom < contract.startDate) {
      this.salaryFormError = "Ngày bắt đầu mức lương không được trước ngày bắt đầu hợp đồng.";
      return;
    }
    if (contract.endDate && (effectiveFrom > contract.endDate || (effectiveTo !== null && effectiveTo > contract.endDate))) {
      this.salaryFormError = "Khoảng mức lương không được vượt thời hạn hợp đồng.";
      return;
    }
    if (this.salaryFormMode === "edit" && effectiveFrom <= this.localToday()) {
      this.salaryFormError = "Chỉ cập nhật được điều khoản bắt đầu từ ngày mai trở đi.";
      return;
    }
    if (this.salaryFormMode === "change" && this.selectedSalaryTerm) {
      const oldTerm = this.selectedSalaryTerm;
      if (effectiveFrom < this.localToday()) {
        this.salaryFormError = "Không thể đổi mức lương hồi tố.";
        return;
      }
      if (effectiveFrom <= oldTerm.effectiveFrom) {
        this.salaryFormError = "Ngày bắt đầu mức mới phải sau ngày bắt đầu của mức cũ.";
        return;
      }
      if (effectiveTo !== oldTerm.effectiveTo) {
        this.salaryFormError = "Đổi mức lương phải giữ nguyên ngày kết thúc của khoảng cũ.";
        return;
      }
    }
    if (!/^\d{1,15}(\.\d{1,4})?$/.test(salaryText) || !Number.isFinite(baseSalary)) {
      this.salaryFormError = "Mức lương phải không âm, tối đa 15 chữ số nguyên và 4 chữ số thập phân.";
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
    if (reason.length > 500) {
      this.salaryFormError = "Lý do không được vượt quá 500 ký tự.";
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
    const contractId = contract.contractId;
    let call$;
    if (this.salaryFormMode === "create") {
      call$ = this.api.createSalaryTerm(contractId, request);
    } else if (this.salaryFormMode === "edit" && this.selectedSalaryTerm) {
      call$ = this.api.updateSalaryTerm(contractId, this.selectedSalaryTerm.salaryTermId, request);
    } else if (this.salaryFormMode === "change" && this.selectedSalaryTerm) {
      call$ = this.api.changeSalaryRate(contractId, this.selectedSalaryTerm.salaryTermId, { newTerm: request });
    } else {
      this.saving = false;
      return;
    }

    call$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        const mode = this.salaryFormMode;
        this.saving = false;
        this.showSalaryForm = false;
        this.selectedSalaryTerm = null;
        this.salaryFormMode = "create";
        this.salaryDraft = this.emptySalaryDraft();
        this.salaryFormError = "";
        this.salaryMessage = mode === "create"
          ? "Đã thêm mức lương."
          : mode === "edit"
            ? "Đã cập nhật mức lương."
            : "Đã tạo mức lương mới; mức cũ vẫn được giữ trong lịch sử.";
        // Re-read authoritative salary history after every successful write.
        this.loadSalaryTerms(contractId);
      },
      error: (error: HttpErrorResponse) => {
        this.saving = false;
        this.salaryFormError = this.readError(
          error,
          this.salaryFormMode === "create"
            ? "Không thêm được mức lương."
            : this.salaryFormMode === "edit"
              ? "Không cập nhật được mức lương."
              : "Không đổi được mức lương.",
        );
      },
    });
  }
  salaryRates: SalaryRateSegment[] = [];
  salaryRatesLoading = false;
  salaryRatesError = "";
  salaryRatesSearched = false;

  loadSalaryRates(from: string, to: string): void {
    const employeeId = this.selected?.employeeId;

    if (!employeeId || this.salaryRatesLoading) return;

    this.salaryRatesError = "";
    this.salaryRates = [];
    this.salaryRatesSearched = false;

    if (!from || !to) {
      this.salaryRatesError = "Hãy chọn ngày bắt đầu và ngày kết thúc.";
      return;
    }

    if (to < from) {
      this.salaryRatesError = "Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.";
      return;
    }

    const start = new Date(`${from}T00:00:00Z`);
    const end = new Date(`${to}T00:00:00Z`);
    const dayCount =
      Math.floor((end.getTime() - start.getTime()) / 86_400_000) + 1;

    if (dayCount > 366) {
      this.salaryRatesError = "Khoảng tra cứu tối đa 366 ngày.";
      return;
    }

    this.salaryRatesLoading = true;

    this.api
      .salaryRates(employeeId, from, to)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rows) => {
          this.salaryRates = rows;
          this.salaryRatesSearched = true;
          this.salaryRatesLoading = false;
        },
        error: (error: HttpErrorResponse) => {
          this.salaryRates = [];
          this.salaryRatesLoading = false;
          this.salaryRatesError = this.readError(
            error,
            "Không tra cứu được mức lương theo kỳ.",
          );
        },
      });
  }
}
