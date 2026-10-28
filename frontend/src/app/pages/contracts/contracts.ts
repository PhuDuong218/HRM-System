import { Component, DestroyRef, OnInit, inject } from "@angular/core";
import { HttpErrorResponse } from "@angular/common/http";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { Subscription } from "rxjs";

import { ContractService } from "../../services/contract.service";
import {
  ContractCreateRequest,
  ContractResponse,
  ContractStatus,
  ContractTypeResponse,
} from "../../models/contract.model";

@Component({
  selector: "app-contracts",
  standalone: true,
  templateUrl: "./contracts.html",
})
export class ContractsPage implements OnInit {
  private api = inject(ContractService);

  rows: ContractResponse[] = [];
  loading = false;
  error = "";
  page = 0;
  totalPages = 0;

  employeeId?: number;
  status?: ContractStatus;
  q = "";

  ngOnInit(): void {
    this.load(0);
  }

  load(page: number): void {
    this.loading = true;
    this.error = "";

    this.api.search(page, this.employeeId, this.status, this.q).subscribe({
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
    // Khi bấm liên tiếp hai hợp đồng, hủy request chi tiết trước.
    this.detailRequest?.unsubscribe();

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
        },
        error: (e: HttpErrorResponse) => {
          this.detailLoading = false;
          this.detailError =
            e.status === 404
              ? "Hợp đồng không còn tồn tại hoặc không tìm thấy."
              : this.readError(e, "Không tải được chi tiết hợp đồng.");
        },
      });
  }

  closeDetail(): void {
    this.detailRequest?.unsubscribe();
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
          this.showCreate = false;
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
}
