import { Component, DestroyRef, EventEmitter, Input, OnChanges, Output, inject } from "@angular/core";
import { HttpErrorResponse } from "@angular/common/http";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { Subscription } from "rxjs";
import { ContractService } from "../../services/contract.service";
import { employmentApiError } from "../../shared/employment-api-error";
import { EmployeeWithoutActiveContractResponse, ExpiringContractResponse, PageResponse } from "../../models/contract.model";
import { Pagination } from "../../shared/pagination";

type WarningTab = "expiring" | "without";

@Component({
  selector: "app-contract-warnings",
  standalone: true,
  imports: [Pagination],
  templateUrl: "./contract-warnings.html",
  styleUrl: "./contract-warnings.css",
})
export class ContractWarnings implements OnChanges {
  @Input() refreshKey = 0;
  @Output() openContract = new EventEmitter<number>();
  private readonly api = inject(ContractService);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;
  tab: WarningTab = "expiring";
  days = 30;
  loading = false;
  error = "";
  expiring: PageResponse<ExpiringContractResponse> | null = null;
  without: PageResponse<EmployeeWithoutActiveContractResponse> | null = null;

  ngOnChanges(): void {
    this.expiring = null;
    this.without = null;
    this.load(0);
  }

  selectTab(tab: WarningTab): void {
    if (this.tab === tab) return;
    this.request?.unsubscribe();
    this.loading = false;
    this.error = "";
    this.tab = tab;
    if (tab === "expiring" ? !this.expiring : !this.without) this.load(0);
  }

  applyDays(value: string): void {
    const days = Number(value);
    if (!Number.isInteger(days) || days < 1 || days > 365) {
      this.error = "Số ngày cảnh báo phải là số nguyên từ 1 đến 365.";
      return;
    }
    this.days = days;
    this.expiring = null;
    this.load(0);
  }

  load(page = 0): void {
    this.request?.unsubscribe();
    this.loading = true;
    this.error = "";
    // Keep each endpoint's paging/data separate. Switching to an already loaded tab reuses its page.
    if (this.tab === "expiring") {
      this.request = this.api.expiring(this.days, page)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (result) => { this.expiring = result; this.loading = false; },
          error: (error: HttpErrorResponse) => { this.loading = false; this.expiring = null; this.error = employmentApiError(error, "Không tải được danh sách hợp đồng sắp hết hạn."); },
        });
    } else {
      this.request = this.api.withoutActiveContract(page)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (result) => { this.without = result; this.loading = false; },
          error: (error: HttpErrorResponse) => { this.loading = false; this.without = null; this.error = employmentApiError(error, "Không tải được danh sách nhân viên thiếu hợp đồng."); },
        });
    }
  }
}
