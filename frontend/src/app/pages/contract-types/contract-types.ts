import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';

import {
  ContractTypeRequest,
  ContractTypeResponse
} from '../../models/contract.model';
import { ContractService } from '../../services/contract.service';

@Component({
  selector: 'app-contract-types',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './contract-types.html'
})
export class ContractTypesPage implements OnInit {
  private api = inject(ContractService);
  private destroyRef = inject(DestroyRef);

  rows: ContractTypeResponse[] = [];

  loading = false;
  saving = false;

  error = '';
  message = '';

  showForm = false;
  editing: ContractTypeResponse | null = null;

  form: ContractTypeRequest = this.emptyForm();

  ngOnInit(): void {
    this.load();
  }

  private emptyForm(): ContractTypeRequest {
    return {
      typeCode: '',
      typeName: '',
      isActive: true
    };
  }

  load(): void {
    if (this.loading) return;

    this.loading = true;
    this.error = '';

    this.api.types()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: rows => {
          this.rows = rows;
          this.loading = false;
        },
        error: (error: HttpErrorResponse) => {
          this.loading = false;
          this.error = this.errorMessage(
            error,
            'Không tải được danh mục loại hợp đồng.'
          );
        }
      });
  }

  openCreate(): void {
    if (this.saving || this.loading) return;

    this.editing = null;
    this.form = this.emptyForm();
    this.error = '';
    this.message = '';
    this.showForm = true;
  }

  openEdit(row: ContractTypeResponse): void {
    if (this.saving || this.loading) return;

    this.editing = row;

    this.form = {
      typeCode: row.typeCode,
      typeName: row.typeName,
      isActive: row.isActive
    };

    this.error = '';
    this.message = '';
    this.showForm = true;
  }

  cancel(): void {
    if (this.saving) return;

    this.showForm = false;
    this.editing = null;
    this.error = '';
  }

  save(): void {
    if (this.saving) return;

    const request: ContractTypeRequest = {
      typeCode: this.form.typeCode.trim(),
      typeName: this.form.typeName.trim(),
      isActive: this.form.isActive
    };

    this.error = '';
    this.message = '';

    if (!request.typeCode || !request.typeName) {
      this.error = 'Hãy nhập mã và tên loại hợp đồng.';
      return;
    }

    if (!/^[A-Za-z0-9_-]+$/.test(request.typeCode)) {
      this.error = 'Mã chỉ gồm chữ không dấu, số, dấu _ hoặc dấu -.';
      return;
    }

    if (request.typeCode.length > 50 || request.typeName.length > 200) {
      this.error = 'Mã tối đa 50 ký tự; tên tối đa 200 ký tự.';
      return;
    }

    const editedId = this.editing?.contractTypeId;

    // Mã đã tạo không được sửa. Giữ đúng mã từ bản đang chọn.
    if (this.editing) {
      request.typeCode = this.editing.typeCode;
    }

    this.saving = true;

    const request$ = editedId !== undefined
      ? this.api.updateType(editedId, request)
      : this.api.createType(request);

    request$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving = false;
          this.showForm = false;
          this.editing = null;

          this.message = editedId !== undefined
            ? 'Đã cập nhật loại hợp đồng.'
            : 'Đã tạo loại hợp đồng.';

          this.load();
        },
        error: (error: HttpErrorResponse) => {
          this.saving = false;
          this.error = this.errorMessage(
            error,
            'Không lưu được loại hợp đồng.'
          );
        }
      });
  }

  deactivate(row: ContractTypeResponse): void {
    if (this.saving || this.loading || !row.isActive) return;

    const confirmed = window.confirm(
      `Ngừng sử dụng loại "${row.typeName}"? ` +
      'Các hợp đồng trong lịch sử vẫn được giữ lại.'
    );

    if (!confirmed) return;

    this.saving = true;
    this.error = '';
    this.message = '';

    this.api.deactivateType(row.contractTypeId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving = false;
          this.showForm = false;
          this.editing = null;
          this.message = 'Đã ngừng sử dụng loại hợp đồng.';
          this.load();
        },
        error: (error: HttpErrorResponse) => {
          this.saving = false;
          this.error = this.errorMessage(
            error,
            'Không ngừng sử dụng được loại hợp đồng.'
          );
        }
      });
  }

  private errorMessage(
    error: HttpErrorResponse,
    fallback: string
  ): string {
    if (error.status === 0) {
      return 'Không kết nối được backend. Hãy kiểm tra kết nối.';
    }

    if (error.status === 401) {
      return 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn.';
    }

    if (error.status === 403) {
      return 'Tài khoản không có quyền quản lý loại hợp đồng.';
    }

    const detail = error.error?.loi_chi_tiet;

    return typeof detail === 'string' && detail.trim()
      ? detail
      : fallback;
  }
}