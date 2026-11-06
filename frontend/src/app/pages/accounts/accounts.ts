import {Breadcrumb} from '../../shared/breadcrumb';
import {Pagination} from '../../shared/pagination';
import {TranslatePipe} from '../../shared/translate.pipe';
import {HrScopes} from './hr-scopes';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize, Subscription } from 'rxjs';
import { Account, AccountContact, AccountFilters, AccountRole, AccountService, AccessPermission, ActiveDepartment, CreateAccount, EligibleEmployee, ScopeDepartmentOption } from '../../services/account.service';
import { RecordWorkspace } from '../../shared/record-workspace';
import { AuthService } from '../../services/auth.service';

type Section = 'accounts' | 'roles' | 'permissions';
type DialogMode = 'account-create' | 'account-view' | 'account-edit' | 'account-lock' | 'account-reset'
  | 'role-view'
  | 'permission-view' | 'permission-edit';

@Component({
  selector: 'app-accounts',
  standalone: true,
  imports:[Breadcrumb,Pagination,FormsModule, RecordWorkspace, TranslatePipe, HrScopes],
  templateUrl: './accounts.html',
  styleUrl: './accounts.css'
})
export class AccountsPage implements OnInit {
  private api = inject(AccountService);
  private auth = inject(AuthService);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);
  private pending?: Subscription;
  private scopeRequest?: Subscription;
  private contactRequest?: Subscription;
  private employeeRequest?: Subscription;
  filters: AccountFilters = { keyword: '', active: '', roleId: '' };

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
  saving = false;
  createError = '';
  actionError = '';
  actionMessage = '';
  resetPassword = '';
  resetConfirm = '';
  editUsername = '';
  editRoleId = 0;
  editPermissionName = '';
  editPermissionDescription = '';
  message = '';
  createForm: CreateAccount = this.emptyCreateForm();
  hrDepartments: ActiveDepartment[] = [];
  hrDepartmentsLoading = false;
  hrDepartmentsError = '';
  scopeEditing = false;
  scopeOptions: ScopeDepartmentOption[] = [];
  scopeOptionsReady = false;
  scopeSelected = new Set<number>();
  scopeExpected: number[] = [];
  contact: AccountContact | null = null;
  contactLoading = false;
  contactError = '';
  employeeKeyword = '';
  searchedEmployeeKeyword = '';
  eligibleEmployees: EligibleEmployee[] = [];
  selectedEmployee: EligibleEmployee | null = null;
  eligibleLoading = false;
  eligibleError = '';
  eligiblePage = 0;
  eligiblePages = 0;

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

  filterSelectionChanged(active: string, roleId: string): void {
    this.filters = {
      ...this.filters,
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
    if (this.saving) return;
    this.clearDialog();
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
    this.createError = '';
    this.actionError = '';
    this.actionMessage = '';
    this.scopeEditing = false;
    this.resetPassword = this.resetConfirm = '';
    if (mode === 'account-create') {
      this.createForm = this.emptyCreateForm();
      this.message = '';
      if (this.rolesError && !this.rolesLoading) this.loadRoles();
      this.selectedEmployee = null;
      this.employeeKeyword = this.searchedEmployeeKeyword = '';
      this.loadEligibleEmployees(0);
    } else {
      this.employeeRequest?.unsubscribe();
      this.eligibleLoading = false;
    }
    this.selectedAccount = item && 'accountId' in item ? item : null;
    this.selectedRole = item && 'roleId' in item && !('accountId' in item) ? item : null;
    this.selectedPermission = item && 'permissionId' in item ? item : null;
    this.dialogMode = mode;
    this.contactRequest?.unsubscribe();
    this.contact = null;
    this.contactLoading = false;
    this.contactError = '';
    if (mode === 'account-view' && this.selectedAccount) this.loadAccountContact(this.selectedAccount.accountId);
    this.scopeRequest?.unsubscribe();
    this.hrDepartments = [];
    this.hrDepartmentsError = '';
    this.hrDepartmentsLoading = false;
    if (mode === 'account-view' && this.selectedAccount?.roleCode === 'HR') this.loadHrDepartments(this.selectedAccount.accountId);
  }

  loadAccountContact(accountId: number): void {
    this.contactRequest?.unsubscribe();
    this.contact = null;
    this.contactLoading = true;
    this.contactError = '';
    this.contactRequest = this.api.contact(accountId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: contact => { this.contact = contact; this.contactLoading = false; },
      error: () => { this.contactError = 'Không tải được thông tin liên hệ.'; this.contactLoading = false; }
    });
  }

  searchEmployees(): void {
    if (this.saving) return;
    this.searchedEmployeeKeyword = this.employeeKeyword.trim();
    this.selectedEmployee = null;
    this.createForm.employeeId = 0;
    this.loadEligibleEmployees(0);
  }

  loadEligibleEmployees(page: number): void {
    this.employeeRequest?.unsubscribe();
    this.eligibleLoading = true;
    this.eligibleError = '';
    this.eligibleEmployees = [];
    this.employeeRequest = this.api.eligibleEmployees(this.searchedEmployeeKeyword, page).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => { this.eligibleEmployees = result.items; this.eligiblePage = result.page; this.eligiblePages = result.totalPages; this.eligibleLoading = false; },
      error: () => { this.eligibleError = 'Không tải được danh sách nhân viên. Vui lòng thử lại.'; this.eligibleLoading = false; }
    });
  }

  selectEmployee(employee: EligibleEmployee): void {
    this.selectedEmployee = employee;
    this.createForm.employeeId = employee.employeeId;
  }

  loadHrDepartments(accountId: number): void {
    this.scopeRequest?.unsubscribe();
    this.hrDepartmentsLoading = true;
    this.hrDepartmentsError = '';
    this.scopeRequest = this.api.activeHrDepartments(accountId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: departments => { this.hrDepartments = departments; this.hrDepartmentsLoading = false; },
      error: () => { this.hrDepartmentsError = 'Không tải được phạm vi phòng ban.'; this.hrDepartmentsLoading = false; }
    });
  }

  editHrDepartments(): void {
    const account = this.selectedAccount;
    if (!account || !this.editRoleIsHr || this.dialogMode !== 'account-edit' || this.saving) return;
    this.scopeEditing = true;
    this.scopeOptions = [];
    this.scopeOptionsReady = false;
    this.scopeExpected = [];
    this.scopeSelected = new Set();
    this.hrDepartmentsLoading = true;
    this.hrDepartmentsError = '';
    this.scopeRequest?.unsubscribe();
    this.scopeRequest = this.api.hrDepartmentOptions(account.accountId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: options => {
        this.scopeOptions = options;
        this.scopeExpected = options.filter(item => item.assigned).map(item => item.departmentId);
        this.scopeSelected = new Set(options.filter(item => item.assigned).map(item => item.departmentId));
        this.scopeOptionsReady = true;
        this.hrDepartmentsLoading = false;
      },
      error: () => { this.hrDepartmentsError = 'Không tải được danh sách phòng ban. Vui lòng thử lại.'; this.hrDepartmentsLoading = false; }
    });
  }

  toggleScopeDepartment(id: number, checked: boolean): void {
    if (checked) this.scopeSelected.add(id); else this.scopeSelected.delete(id);
  }

  cancelScopeEdit(): void {
    if (this.saving) return;
    this.scopeRequest?.unsubscribe();
    this.hrDepartmentsLoading = false;
    this.hrDepartmentsError = '';
    this.scopeEditing = false;
    this.scopeOptionsReady = false;
  }

  get editRoleIsHr(): boolean { return this.roles.some(role => role.roleId === this.editRoleId && role.roleCode === 'HR'); }
  get scopeChanged(): boolean {
    return this.scopeEditing && this.scopeOptionsReady && this.editRoleIsHr &&
      (this.scopeExpected.length !== this.scopeSelected.size || this.scopeExpected.some(id => !this.scopeSelected.has(id)));
  }

  onEditRoleChange(): void { this.cancelScopeEdit(); }

  dateOnly(value: string | null): string {
    if (!value) return 'Chưa có thông tin';
    const parsed = new Date(/[zZ]|[+-]\d{2}:?\d{2}$/.test(value) ? value : `${value}Z`);
    return Number.isNaN(parsed.getTime()) ? 'Chưa có thông tin' :
      new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric' }).format(parsed);
  }

  showDetailAction(mode: DialogMode): void {
    this.actionError = '';
    this.actionMessage = '';
    this.resetPassword = this.resetConfirm = '';
    if (mode === 'account-edit' && this.selectedAccount) {
      this.editUsername = this.selectedAccount.username;
      this.editRoleId = this.selectedAccount.roleId;
      this.cancelScopeEdit();
    }
    if (mode === 'permission-edit' && this.selectedPermission) {
      this.editPermissionName = this.selectedPermission.permissionName;
      this.editPermissionDescription = this.selectedPermission.description ?? '';
    }
    this.dialogMode = mode;
  }

  savePermissionDetails(): void {
    if (this.saving || !this.selectedPermission) return;
    const permission = this.selectedPermission;
    const name = this.editPermissionName.trim();
    const description = this.editPermissionDescription.trim() || null;
    if (!name || name.length > 200 || this.editPermissionDescription.length > 1000) {
      this.actionError = 'Vui lòng nhập tên quyền tối đa 200 ký tự và mô tả tối đa 1.000 ký tự.'; return;
    }
    if (name === permission.permissionName && description === permission.description) {
      this.actionError = 'Thông tin quyền chưa thay đổi.'; return;
    }
    this.saving = true;
    this.actionError = '';
    this.api.editPermission(permission.permissionId, { expectedName: permission.permissionName,
      expectedDescription: permission.description, permissionName: name, description })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving = false)).subscribe({
        next: updated => {
          this.selectedPermission = updated;
          this.permissions = this.permissions.map(item => item.permissionId === updated.permissionId ? updated : item);
          this.roles = this.roles.map(role => ({ ...role, permissions: role.permissions.map(item => item.permissionId === updated.permissionId ? updated : item) }));
          this.dialogMode = 'permission-view';
          this.actionMessage = 'Đã cập nhật thông tin quyền.';
        },
        error: error => this.actionError = this.actionErrorMessage(error)
      });
  }

  isOwnAccount(account: Account): boolean {
    return !!this.auth.username() && this.auth.username().toLowerCase() === account.username.toLowerCase();
  }

  saveAccountIdentity(): void {
    if (this.saving || !this.selectedAccount) return;
    const account = this.selectedAccount;
    const username = this.editUsername.trim();
    if (!/^[A-Za-z0-9][A-Za-z0-9._@-]{0,99}$/.test(username)) {
      this.actionError = 'Tên đăng nhập phải bắt đầu bằng chữ hoặc số và chỉ chứa chữ, số, dấu chấm, gạch dưới, @ hoặc gạch nối.'; return;
    }
    if (!this.roles.some(role => role.roleId === this.editRoleId && this.isSystemRole(role))) {
      this.actionError = 'Vui lòng chọn vai trò hợp lệ.'; return;
    }
    if (this.scopeEditing && (this.hrDepartmentsLoading || !this.scopeOptionsReady)) {
      this.actionError = 'Vui lòng đợi tải phòng ban hoặc hủy phân công.'; return;
    }
    if (this.scopeChanged && !this.scopeOptions.some(item => item.active && this.scopeSelected.has(item.departmentId))) {
      this.actionError = 'Vui lòng chọn ít nhất một phòng ban đang hoạt động.'; return;
    }
    const identityChanged = username !== account.username || this.editRoleId !== account.roleId;
    if (!identityChanged && !this.scopeChanged) {
      this.actionError = 'Thông tin tài khoản chưa thay đổi.'; return;
    }
    const ownAccount = this.isOwnAccount(account);
    this.saving = true;
    this.actionError = '';
    this.api.editIdentity(account.accountId, {
      expectedUsername: account.username, expectedRoleId: account.roleId, username, roleId: this.editRoleId,
      ...(this.scopeChanged ? { expectedDepartmentIds: this.scopeExpected, departmentIds: [...this.scopeSelected] } : {})
    }).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving = false)).subscribe({
      next: updated => {
        if (ownAccount) {
          this.auth.clear();
          void this.router.navigateByUrl('/login', { state: { usernameChanged: true } });
          return;
        }
        this.selectedAccount = updated;
        this.dialogMode = 'account-view';
        this.actionMessage = identityChanged ? 'Đã cập nhật tài khoản. Các phiên đăng nhập cũ đã bị thu hồi.' : 'Đã cập nhật phạm vi phòng ban.';
        this.scopeEditing = false;
        this.scopeRequest?.unsubscribe();
        this.hrDepartments = [];
        this.hrDepartmentsError = '';
        this.hrDepartmentsLoading = false;
        if (updated.roleCode === 'HR') this.loadHrDepartments(updated.accountId);
        this.load();
      },
      error: error => this.actionError = this.actionErrorMessage(error)
    });
  }

  openRowByKey(event: Event, mode: DialogMode, item: Account | AccountRole | AccessPermission): void {
    if (event.target !== event.currentTarget) return;
    event.preventDefault();
    this.openDialog(mode, item);
  }

  backToDetail(): void {
    this.actionError = '';
    this.resetPassword = this.resetConfirm = '';
    if (this.selectedAccount) this.dialogMode = 'account-view';
    else if (this.selectedRole) this.dialogMode = 'role-view';
    else if (this.selectedPermission) this.dialogMode = 'permission-view';
  }

  closeDialog(): void { if (!this.saving) this.clearDialog(); }
  clearDialog(): void {
    this.contactRequest?.unsubscribe();
    this.contact = null;
    this.contactLoading = false;
    this.contactError = '';
    this.employeeRequest?.unsubscribe();
    this.eligibleLoading = false;
    this.eligibleEmployees = [];
    this.selectedEmployee = null;
    this.eligibleError = '';
    this.scopeRequest?.unsubscribe();
    this.scopeEditing = false;
    this.hrDepartments = [];
    this.hrDepartmentsLoading = false;
    this.hrDepartmentsError = '';
    this.dialogMode = null;
    this.selectedAccount = null;
    this.selectedRole = null;
    this.selectedPermission = null;
    this.createError = '';
    this.actionError = '';
    this.actionMessage = '';
    this.resetPassword = this.resetConfirm = '';
    this.createForm = this.emptyCreateForm();
  }

  submitPasswordReset(): void {
    if (this.saving || !this.selectedAccount) return;
    if (this.resetPassword.length < 8 || this.resetPassword.length > 72 || !this.resetPassword.trim() ||
        new TextEncoder().encode(this.resetPassword).length > 72) {
      this.actionError = 'Mật khẩu cần từ 8 đến 72 ký tự và không quá 72 byte UTF-8.'; return;
    }
    if (this.resetPassword !== this.resetConfirm) { this.actionError = 'Mật khẩu xác nhận không khớp.'; return; }
    this.saving = true;
    this.actionError = '';
    const accountId = this.selectedAccount.accountId;
    this.api.resetPassword(accountId, this.resetPassword, this.resetConfirm)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving = false)).subscribe({
        next: () => {
          this.resetPassword = this.resetConfirm = '';
          this.dialogMode = 'account-view';
          this.actionMessage = 'Đã đặt lại mật khẩu. Các phiên đăng nhập cũ của tài khoản đã bị thu hồi.';
          this.api.get(accountId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
            next: account => { if (this.selectedAccount?.accountId === accountId) this.selectedAccount = account; },
            error: () => {}
          });
          this.load();
        },
        error: error => this.actionError = this.actionErrorMessage(error)
      });
  }

  submitStatusChange(): void {
    if (this.saving || !this.selectedAccount) return;
    const account = this.selectedAccount;
    this.saving = true;
    this.actionError = '';
    this.api.changeStatus(account.accountId, account.active, !account.active)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving = false)).subscribe({
        next: updated => {
          this.selectedAccount = updated;
          this.dialogMode = 'account-view';
          this.actionMessage = updated.active ? 'Đã mở khóa tài khoản.' : 'Đã khóa tài khoản và thu hồi các phiên đăng nhập cũ.';
          this.load();
        },
        error: error => this.actionError = this.actionErrorMessage(error)
      });
  }

  private actionErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if ([400, 403, 404, 409].includes(error.status) && typeof error.error?.loi_chi_tiet === 'string') return error.error.loi_chi_tiet;
      if (error.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
      if (error.status === 403) return 'Bạn không có quyền thực hiện thao tác này.';
    }
    return 'Không nhận được kết quả. Vui lòng tải lại và kiểm tra trạng thái trước khi thử lại.';
  }

  createAccount(): void {
    if (this.saving) return;
    const input = this.createForm;
    const username = input.username.trim();
    if (!this.selectedEmployee || this.selectedEmployee.employeeId !== input.employeeId || !this.roles.some(role => role.roleId === input.roleId)) {
      this.createError = 'Vui lòng chọn nhân viên và vai trò.'; return;
    }
    if (!/^[A-Za-z0-9][A-Za-z0-9._@-]{0,99}$/.test(username)) {
      this.createError = 'Tên đăng nhập phải bắt đầu bằng chữ hoặc số và chỉ chứa chữ, số, dấu chấm, gạch dưới, @ hoặc gạch nối.'; return;
    }
    if (input.password.length < 8 || input.password.length > 72 || new TextEncoder().encode(input.password).length > 72 || !input.password.trim()) {
      this.createError = 'Mật khẩu cần từ 8 đến 72 ký tự và không quá 72 byte UTF-8.'; return;
    }
    if (input.password !== input.confirmPassword) { this.createError = 'Mật khẩu xác nhận không khớp.'; return; }
    this.saving = true;
    this.createError = '';
    this.api.create({ ...input, username }).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving = false)).subscribe({
      next: account => {
        this.clearDialog();
        this.message = `Đã tạo tài khoản ${account.username}. Danh sách bên dưới vẫn áp dụng bộ lọc hiện tại.`;
        this.load(0);
      },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && [400, 404, 409].includes(error.status) && typeof error.error?.loi_chi_tiet === 'string') {
          this.createError = error.error.loi_chi_tiet;
        } else if (error instanceof HttpErrorResponse && error.status === 403) {
          this.createError = 'Bạn không có quyền tạo tài khoản.';
        } else if (error instanceof HttpErrorResponse && error.status === 401) {
          this.createError = 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
        } else {
          this.createError = 'Không nhận được kết quả tạo tài khoản. Hãy kiểm tra danh sách trước khi thử lại.';
        }
      }
    });
  }

  private emptyCreateForm(): CreateAccount {
    return { employeeId: 0, roleId: 0, username: '', password: '', confirmPassword: '', active: true };
  }

  isSystemRole(role: AccountRole): boolean { return ['ADMIN', 'HR', 'EMPLOYEE'].includes(role.roleCode); }

  get dialogTitle(): string {
    const titles: Record<DialogMode, string> = {
      'account-create': 'Thêm tài khoản', 'account-view': 'Chi tiết tài khoản',
      'account-edit': 'Chỉnh sửa tài khoản', 'account-lock': 'Thay đổi trạng thái tài khoản',
      'account-reset': 'Đặt lại mật khẩu',
      'role-view': 'Chi tiết vai trò',
      'permission-view': 'Chi tiết quyền',
      'permission-edit': 'Chỉnh sửa quyền'
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
