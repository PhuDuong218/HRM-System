import {TranslatePipe} from '../../shared/translate.pipe';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, OnInit, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, Observable, finalize } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { Employee, errorMessage } from '../../services/employee.service';
import { toListPage } from '../../models/page.model';
import { Account, AccountFilters, AccountRole, AccountService, AccessPermission } from '../../services/account.service';

type Section = 'accounts' | 'roles' | 'permissions';
type DialogMode = 'account-create' | 'account-view' | 'account-edit' | 'account-lock' | 'account-reset'
  | 'role-create' | 'role-view' | 'role-edit' | 'role-permissions' | 'role-delete'
  | 'permission-create' | 'permission-view' | 'permission-edit' | 'permission-delete';

@Component({
  selector: 'app-accounts',
  standalone: true,
  imports: [TranslatePipe, FormsModule],
  templateUrl: './accounts.html',
  styleUrl: './accounts.css'
})
export class AccountsPage implements OnInit {
  @ViewChild('actionDialog') private actionDialog?: ElementRef<HTMLDialogElement>;
  private api = inject(AccountService);
  private destroyRef = inject(DestroyRef);
  private pending?: Subscription;
  private filters: AccountFilters = { keyword: '', active: '', roleId: '' };

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
  actionError = '';
  message = '';
  employees: Employee[] = [];
  employeesLoading = false;
  draft = this.blankDraft();

  private blankDraft() {
    return {employeeId:null as number|null,roleId:null as number|null,username:'',active:true,password:'',confirmPassword:'',
      roleCode:'',roleName:'',permissionCode:'',permissionName:'',description:'',permissionIds:[] as number[]};
  }

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
        const list=toListPage(result);
        this.rows = list.rows;
        this.page = list.page;
        this.pages = list.pages;
        this.total = list.total;
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
    this.selectedAccount = item && 'accountId' in item ? item : null;
    this.selectedRole = item && 'roleId' in item && !('accountId' in item) ? item : null;
    this.selectedPermission = item && 'permissionId' in item ? item : null;
    this.dialogMode = mode;
    this.actionError='';
    this.draft={...this.blankDraft(),
      employeeId:this.selectedAccount?.employeeId??null,roleId:this.selectedAccount?.roleId??null,
      username:this.selectedAccount?.username??'',active:this.selectedAccount?.active??true,
      roleCode:this.selectedRole?.roleCode??'',roleName:this.selectedRole?.roleName??'',
      permissionIds:this.selectedRole?.permissions.map(p=>p.permissionId)??[],
      permissionCode:this.selectedPermission?.permissionCode??'',permissionName:this.selectedPermission?.permissionName??'',
      description:this.selectedPermission?.description??''};
    if(mode==='role-create'||mode==='role-permissions')this.loadPermissions();
    if(mode==='account-create'){
      this.employeesLoading=true;
      this.api.employees().pipe(takeUntilDestroyed(this.destroyRef),finalize(()=>this.employeesLoading=false)).subscribe({
        next:rows=>this.employees=rows.filter(row=>row.employmentStatus==='DANG_LAM'),
        error:e=>this.actionError=errorMessage(e)});
    }
    this.actionDialog?.nativeElement.showModal();
  }

  closeDialog(): void { if(!this.saving)this.actionDialog?.nativeElement.close(); }
  cancelDialog(event: Event): void { if(this.saving)event.preventDefault(); }
  togglePermission(id: number,checked: boolean): void {
    this.draft.permissionIds=checked?[...new Set([...this.draft.permissionIds,id])]:this.draft.permissionIds.filter(value=>value!==id);
  }
  saveDialog(): void {
    if(this.saving||!this.dialogMode)return;
    const mode=this.dialogMode,d=this.draft;
    this.actionError='';
    if(mode==='account-create'||mode==='account-reset'){
      if(d.password.length<8||new TextEncoder().encode(d.password).length>72||d.password!==d.confirmPassword){
        this.actionError='Mật khẩu phải từ 8 đến 72 ký tự và xác nhận phải trùng khớp.';return;
      }
    }
    let command:Observable<unknown>|undefined;
    const account=this.selectedAccount,role=this.selectedRole,permission=this.selectedPermission;
    if(mode==='account-create'||mode==='account-edit'){
      if(!d.roleId||!d.username.trim()||(mode==='account-create'&&!d.employeeId)){this.actionError='Vui lòng nhập đầy đủ thông tin bắt buộc.';return;}
      const body={roleId:d.roleId,username:d.username.trim(),active:d.active};
      command=mode==='account-create'?this.api.create({...body,employeeId:d.employeeId!,password:d.password,confirmPassword:d.confirmPassword}):this.api.update(account!.accountId,body);
    }else if(mode==='account-lock'&&account)command=this.api.update(account.accountId,{roleId:account.roleId,username:account.username,active:!account.active});
    else if(mode==='account-reset'&&account)command=this.api.resetPassword(account.accountId,d.password,d.confirmPassword);
    else if(mode==='role-create')command=this.api.createRole(d.roleCode.trim(),d.roleName.trim(),d.permissionIds);
    else if(mode==='role-edit'&&role)command=this.api.updateRole(role.roleId,d.roleName.trim());
    else if(mode==='role-permissions'&&role){if(this.permissionsLoading||this.permissionsError)return;command=this.api.replacePermissions(role.roleId,d.permissionIds);}
    else if(mode==='role-delete'&&role&&!this.isSystemRole(role))command=this.api.deleteRole(role.roleId);
    else if(mode==='permission-create')command=this.api.createPermission(d.permissionCode.trim(),d.permissionName.trim(),d.description.trim());
    else if(mode==='permission-edit'&&permission)command=this.api.updatePermission(permission.permissionId,d.permissionName.trim(),d.description.trim());
    else if(mode==='permission-delete'&&permission&&permission.permissionCode!=='ACCOUNT_MANAGE')command=this.api.deletePermission(permission.permissionId);
    if(!command)return;
    this.saving=true;
    command.pipe(takeUntilDestroyed(this.destroyRef),finalize(()=>this.saving=false)).subscribe({
      next:()=>{this.saving=false;this.closeDialog();this.message='Đã lưu thay đổi thành công.';
        if(mode.startsWith('account-'))this.load();else if(mode.startsWith('role-'))this.loadRoles();else{this.loadPermissions();this.loadRoles();}},
      error:e=>this.actionError=errorMessage(e)});
  }
  clearDialog(): void {
    this.draft=this.blankDraft();
    this.actionError='';
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
