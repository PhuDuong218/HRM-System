import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, OnInit, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, timer } from 'rxjs';
import { Account, AccountFilters, AccountRole, AccountService, AccessPermission } from '../../services/account.service';

type Section = 'accounts' | 'roles' | 'permissions';
type DialogMode = 'account-create' | 'account-view' | 'account-edit' | 'account-lock' | 'account-reset'
  | 'role-create' | 'role-view' | 'role-edit' | 'role-permissions' | 'role-delete'
  | 'permission-create' | 'permission-view' | 'permission-edit' | 'permission-delete';

@Component({
  selector: 'app-accounts',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './accounts.html',
  styleUrl: './accounts.css'
})
export class AccountsPage implements OnInit {
  @ViewChild('actionDialog') private actionDialog?: ElementRef<HTMLDialogElement>;
  private api = inject(AccountService);
  private destroyRef = inject(DestroyRef);
  private pending?: Subscription;
  private filters: AccountFilters = { keyword: '', active: '', roleId: '' };

  saving = false;
  dialogError = '';
  success = '';
  private feedbackTimer?: Subscription;
  readonly editForm = new FormGroup({
    username: new FormControl('', {nonNullable:true, validators:[Validators.required, Validators.maxLength(100), Validators.pattern('[A-Za-z0-9][A-Za-z0-9._@-]*')]}),
    roleId: new FormControl<number|null>(null, [Validators.required, Validators.min(1)]),
    active: new FormControl(true, {nonNullable:true})
  });

  rows: Account[] = [];
  roles: AccountRole[] = [];
  permissions: AccessPermission[] = [];
  section: Section = 'accounts';
  dialogMode: DialogMode | null = null;
  selectedAccount: Account | null = null;
  selectedRole: AccountRole | null = null;
  selectedPermission: AccessPermission | null = null;
  page = 0;
  pages = 0;
  total = 0;
  loading = false;
  error = '';
  rolesError = '';
  rolesLoading = false;
  permissionsError = '';
  permissionsLoading = false;
  permissionsLoaded = false;

  ngOnInit(): void {
    this.load();
    this.loadRoles();
  }

  search(event: Event, keyword: string, active: string, roleId: string): void {
    event.preventDefault();
    this.filters = {
      keyword: keyword.trim(),
      active: active === 'true' || active === 'false' ? active : '',
      roleId
    };
    this.load(0);
  }

  reset(form: HTMLFormElement): void {
    form.reset();
    this.filters = { keyword: '', active: '', roleId: '' };
    this.load(0);
  }

  load(page = this.page): void {
    this.pending?.unsubscribe();
    this.page = page;
    this.loading = true;
    this.error = '';
    this.rows = [];
    this.pending = this.api.list(page, this.filters).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => {
        this.rows = result.items;
        this.page = result.page;
        this.pages = result.totalPages;
        this.total = result.totalElements;
        this.loading = false;
      },
      error: (error: unknown) => {
        this.loading = false;
        this.pages = 0;
        this.total = 0;
        this.error = this.errorMessage(error);
      }
    });
  }

  loadRoles(): void {
    if (this.rolesLoading) return;
    this.rolesLoading = true;
    this.rolesError = '';
    this.api.roles().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: roles => { this.roles = roles; this.rolesLoading = false; },
      error: (error: unknown) => {
        this.rolesLoading = false;
        this.rolesError = error instanceof HttpErrorResponse && error.status === 403
          ? 'Bạn không có quyền xem danh sách vai trò.' : 'Không tải được danh sách vai trò.';
      }
    });
  }

  selectSection(section: Section): void {
    this.section = section;
    if (section !== 'accounts' && !this.permissionsLoaded && !this.permissionsLoading) this.loadPermissions();
  }

  loadPermissions(): void {
    if (this.permissionsLoading) return;
    this.permissionsLoading = true;
    this.permissionsError = '';
    this.api.permissions().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: permissions => { this.permissions = permissions; this.permissionsLoaded = true; this.permissionsLoading = false; },
      error: (error: unknown) => {
        this.permissionsLoading = false;
        this.permissionsError = error instanceof HttpErrorResponse && error.status === 403
          ? 'Bạn không có quyền xem danh sách quyền.' : 'Không tải được danh sách quyền.';
      }
    });
  }

  openDialog(mode: DialogMode, item?: Account | AccountRole | AccessPermission): void {
    if (this.saving) return;
    this.dialogError = '';
    this.feedbackTimer?.unsubscribe();
    this.selectedAccount = item && 'accountId' in item ? item : null;
    this.selectedRole = item && 'roleId' in item && !('accountId' in item) ? item : null;
    this.selectedPermission = item && 'permissionId' in item ? item : null;
    this.editForm.reset({username:this.selectedAccount?.username ?? '', roleId:this.selectedAccount?.roleId ?? null, active:this.selectedAccount?.active ?? true});
    this.dialogMode = mode;
    this.actionDialog?.nativeElement.showModal();
  }

  closeDialog(): void { if (!this.saving) this.actionDialog?.nativeElement.close(); }
  cancelDialog(event: Event): void { if (this.saving) event.preventDefault(); }
  saveAccount(): void {
    if (this.saving || this.dialogMode !== 'account-edit' || !this.selectedAccount) return;
    this.editForm.controls.username.setValue(this.editForm.controls.username.value.trim());
    this.editForm.markAllAsTouched();
    if (this.editForm.invalid) { this.dialogError = 'Nhập tên đăng nhập hợp lệ và chọn vai trò.'; return; }
    const value = this.editForm.getRawValue();
    if (!this.roles.some(role => role.roleId === value.roleId)) { this.dialogError = 'Vai trò không hợp lệ. Hãy tải lại danh sách vai trò.'; return; }
    if (!window.confirm('Lưu thay đổi tài khoản ' + this.selectedAccount.username + '? Tài khoản bị thay đổi sẽ cần đăng nhập lại.')) return;
    this.saving = true; this.dialogError = '';
    this.api.update(this.selectedAccount.accountId, {username:value.username, roleId:value.roleId!, active:value.active})
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: updated => {
          this.saving = false;
          this.rows = this.rows.map(row => row.accountId === updated.accountId ? updated : row);
          this.closeDialog();
          this.success = 'Đã cập nhật tài khoản ' + updated.username + '.';
          this.feedbackTimer?.unsubscribe();
          this.feedbackTimer = timer(10000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.success = '');
          this.load(this.page);
        },
        error: error => {
          this.saving = false;
          const detail = error instanceof HttpErrorResponse ? error.error?.loi_chi_tiet ?? error.error?.detail ?? error.error?.message : null;
          this.dialogError = typeof detail === 'string' ? detail : 'Không lưu được tài khoản. Kiểm tra kết nối và thử lại.';
          this.feedbackTimer?.unsubscribe();
          this.feedbackTimer = timer(10000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.dialogError = '');
        }
      });
  }
  clearDialog(): void {
    this.dialogMode = null;
    this.selectedAccount = null;
    this.selectedRole = null;
    this.selectedPermission = null;
  }

  hasPermission(role: AccountRole | null, permission: AccessPermission): boolean {
    return role?.permissions.some(item => item.permissionId === permission.permissionId) ?? false;
  }

  isSystemRole(role: AccountRole): boolean { return ['ADMIN', 'HR', 'EMPLOYEE'].includes(role.roleCode); }

  get dialogTitle(): string {
    const titles: Record<DialogMode, string> = {
      'account-create': 'Thêm tài khoản', 'account-view': 'Chi tiết tài khoản',
      'account-edit': 'Chỉnh sửa tài khoản', 'account-lock': 'Thay đổi trạng thái tài khoản',
      'account-reset': 'Đặt lại mật khẩu', 'role-create': 'Thêm vai trò',
      'role-view': 'Chi tiết vai trò', 'role-edit': 'Chỉnh sửa vai trò',
      'role-permissions': 'Phân quyền cho vai trò', 'role-delete': 'Xóa vai trò',
      'permission-create': 'Thêm quyền', 'permission-view': 'Chi tiết quyền',
      'permission-edit': 'Chỉnh sửa quyền', 'permission-delete': 'Xóa quyền'
    };
    return this.dialogMode ? titles[this.dialogMode] : '';
  }

  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 403) return 'Chỉ ADMIN có quyền ACCOUNT_MANAGE mới xem được danh sách tài khoản.';
      if (error.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
      if (error.status === 404) return 'Không tìm thấy API tài khoản. Hãy kiểm tra và khởi động lại backend.';
      if (error.status === 400 && typeof error.error?.loi_chi_tiet === 'string') return error.error.loi_chi_tiet;
    }
    return 'Không tải được danh sách tài khoản. Hãy kiểm tra kết nối tới backend và thử lại.';
  }
}
