import {SeniorityReport} from '../../shared/seniority-report';
import {PageHeader} from '../../shared/page-header';
import { auditActionLabel } from '../../shared/audit-action-label';
import {SearchAssistDirective} from '../../shared/search-assist.directive';
import {EditFormDirective} from '../../shared/edit-form.directive';
import {HrmNotice} from '../../shared/ui/notice';
import {RowViewDirective} from '../../shared/row-view.directive';
import {Feedback} from '../../shared/ui/feedback';
import {HrmButton} from '../../shared/ui/button';
import {ManagementPageBase} from '../../shared/management-page.base';
import {TranslatePipe} from '../../shared/translate.pipe';
import {Pagination} from '../../shared/pagination';
import { Component, DestroyRef, ElementRef, ViewChild, inject, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, finalize, timeout } from 'rxjs';
import { EmployeeService, Employee, EmployeeForm, errorMessage } from '../../services/employee.service';
import { AuthService } from '../../services/auth.service';
import { RecordWorkspace } from '../../shared/record-workspace';
import { employeeFormErrors } from './employee-form.validation';
const blank = (): EmployeeForm => ({ employeeCode: '', fullName: '', birthDate: '', email: '', phone: '', identityNumber: '', hireDate: '', address: '', departmentId: null, positionId: null });
@Component({ selector: 'app-employees', standalone: true, imports:[SeniorityReport,SearchAssistDirective,EditFormDirective,HrmNotice,RowViewDirective,Feedback,HrmButton,PageHeader,Pagination,FormsModule, DatePipe, RecordWorkspace,TranslatePipe], templateUrl: './employees.html', styleUrl: './employees.css' })
export class EmployeesPage extends ManagementPageBase implements OnInit {
    profileText(text:string){return this.i18n.current?.()==='en'?({
        'Hồ sơ đã thay đổi':'Employee profile has changed',
        'Hồ sơ đã được người khác thay đổi. Bản đang nhập của bạn vẫn được giữ nguyên.':'Someone else changed this profile. Your current draft has been preserved.',
        'Giữ bản đang nhập để so sánh':'Keep draft for comparison',
        'Tải lại bản mới':'Reload latest version',
        'Hồ sơ đã được người khác thay đổi. Hãy tải lại để xem bản mới nhất.':'Someone else changed this profile. Reload it to see the latest version.',
        'Phải gửi phiên bản hồ sơ đã đọc':'The previously read profile version is required',
        'Phải gửi phiên bản hồ sơ đã đọc hợp lệ':'A valid previously read profile version is required',
        'Phiên bản hồ sơ không hợp lệ':'Invalid profile version',
        'CCCD/CMND phải gồm 9 hoặc 12 chữ số':'National ID must contain 9 or 12 digits'
    } as Record<string,string>)[text]||this.i18n.t(text):text;}
    @ViewChild('profileConflict') private conflictDialog?:ElementRef<HTMLDialogElement>;
    conflictOpen=false;
    keepDraft(){this.conflictOpen=false;this.conflictDialog?.nativeElement.close();}
    reloadConflict(){
        if(!this.selected||this.detailLoading)return;
        const id=this.selected.employeeId;this.detailLoading=true;
        this.detailRequest?.unsubscribe();
        this.detailRequest=this.api.detail(id).pipe(takeUntilDestroyed(this.destroy),finalize(()=>this.detailLoading=false)).subscribe({
            next:p=>{if(this.selected?.employeeId!==id||!['edit','contact'].includes(this.mode||''))return;this.hydrateProfile(p);this.serverFieldErrors={};this.attemptedSave=false;this.detailError='';this.keepDraft();},
            error:e=>this.detailError=errorMessage(e)
        });
    }
    private hydrateProfile(p:Employee){
        this.selected=p;this.form={...blank(),version:p.version,gender:p.gender,employeeCode:p.employeeCode,fullName:p.fullName,
            birthDate:p.birthDate?.slice(0,10)||'',email:p.email||'',phone:p.phone||'',identityNumber:p.identityNumber||'',
            hireDate:p.hireDate?.slice(0,10)||'',address:p.address||'',privateFieldsEditable:!this.ownProfile};
    }
    private api = inject(EmployeeService);
    private auth = inject(AuthService);
    private destroy = inject(DestroyRef);
    private detailRequest?: Subscription;
    private auditRequest?: Subscription;
    auditRows:Record<string,unknown>[]=[];
    auditOpen=false;
    auditLoading=false;
    auditError='';
    auditPage=0;
    auditPages=0;
    auditText(row:Record<string,unknown>,key:string){return String(row[key]??'');}
    auditAction(row: Record<string, unknown>): string { return auditActionLabel(row['action']); }
    private readonly auditDepartments = new Map<number, string>();
    private readonly auditPositions = new Map<number, string>();

    auditActor(entry: Record<string, unknown>): string {
        return this.auditText(entry, 'actorName').trim() ||
            (entry['actorAccountId'] == null ? '—' : '#' + entry['actorAccountId']);
    }

    employeeAuditFields(entry: Record<string, unknown>, key: string): [string, string][] {
        let snapshot: Record<string, unknown>;
        try {
            const data = entry[key];
            const value = typeof data === 'string' ? JSON.parse(data) : data;
            if (!value || typeof value !== 'object' || Array.isArray(value)) return [];
            snapshot = value;
        } catch {
            return [];
        }
        const fields: [string, string][] = [];
        const add = (label: string, value: unknown) => {
            if (value !== undefined && value !== null && value !== '') fields.push([label, String(value)]);
        };
        const name = (field: string, catalog: Map<number, string>) => {
            const id = snapshot[field + 'Id'];
            return snapshot[field + 'Name'] || catalog.get(Number(id)) || (id == null ? null : '#' + id);
        };
        const date = (value: unknown) => {
            const text = String(value ?? '');
            return /^\d{4}-\d{2}-\d{2}$/.test(text) ? text.split('-').reverse().join('/') : text;
        };
        add('Phòng ban', name('department', this.auditDepartments));
        add('Chức vụ', name('position', this.auditPositions));
        add('Mã nhân viên', snapshot['employeeCode']);
        add('Họ tên', snapshot['fullName']);
        add('Ngày sinh', date(snapshot['birthDate']));
        if ('gender' in snapshot) add('Giới tính', this.genderLabel(String(snapshot['gender'] ?? '')));
        add('Ngày vào làm', date(snapshot['hireDate']));
        add('Ngày thôi việc', date(snapshot['terminationDate'] ?? snapshot['endDate']));
        const status = String(snapshot['employmentStatus'] ?? '');
        add('Trạng thái', ({DANG_LAM: 'Đang làm', DA_NGHI: 'Đã nghỉ'} as Record<string, string>)[status] || status);
        for (const [field, label] of [['email', 'Email'], ['phone', 'Số điện thoại']]) {
            if (field in snapshot) add(label, snapshot[field] || 'Chưa cập nhật');
        }
        if (this.canReadPrivate) {
            if ('address' in snapshot) add('Địa chỉ', snapshot['address'] || 'Chưa cập nhật');
            if ('identityNumber' in snapshot) add('CCCD', snapshot['identityNumber'] || 'Chưa cập nhật');
        }
        add('Có hiệu lực từ', date(snapshot['effectiveFrom']));
        add('Đến trước ngày', date(snapshot['effectiveTo']));
        return fields;
    }

    loadAudit(page=0){if(!this.selected||this.auditLoading)return;this.auditOpen=true;this.auditError='';this.auditLoading=true;
        this.auditRequest?.unsubscribe();this.auditRequest=this.api.audit(this.selected.employeeId,page).pipe(takeUntilDestroyed(this.destroy),finalize(()=>this.auditLoading=false)).subscribe({next:r=>{this.auditRows=r.content;this.auditPage=r.number;this.auditPages=r.totalPages;},error:e=>this.auditError=errorMessage(e)});}
    rows: Employee[] = [];
    selected: Employee | null = null;
    page = 0;
    pages = 0;
    total = 0;
    keyword = '';
    employeeFilterId: number | null = null;
    employeeChoices: Employee[] = [];
    employeeChoicesLoading = false;
    employeeChoicesError = '';
    status = '';
    department = '';
    position = '';
    advancedSearch = false;
    departments: {
        id: number;
        name: string;
    }[] = [];
    positions: {
        id: number;
        name: string;
    }[] = [];

    detailLoading = false;


    detailError = '';

    choicesError = '';
    mode: string | null = null;
    form = blank();
    attemptedSave = false;
    serverFieldErrors: Record<string,string> = {};
    get today() { return new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date()); }
    get formErrors() { return employeeFormErrors(this.form,this.mode || '',this.today,!this.ownProfile); }
    get formErrorsCount() { return Object.keys(this.formErrors).length; }
    fieldError(field:string,f:NgForm) {
        const control=f.controls[field];
        if(!(this.attemptedSave || control?.touched))return '';
        return this.profileText(this.serverFieldErrors[field] || this.formErrors[field] || (control?.invalid ? 'Giá trị chưa hợp lệ.' : ''));
    }
    clearFieldError(field:string) { delete this.serverFieldErrors[field]; }
    confirmTermination = false;
    exitDecision={reason:'',lastWorkingDate:'',decisionType:'MUTUAL_AGREEMENT',decisionNumber:''};
    rehireDate = '';
    rehireReason = '';
    genderLabel(value?:string|null){return ({MALE:"Nam",FEMALE:"Nữ",OTHER:"Khác",UNDISCLOSED:"Không muốn cung cấp"} as Record<string,string>)[value||""]||"Chưa cung cấp";}
    seniorityOpen = false;
    get admin() { return this.auth.profile()?.role === 'ADMIN'; }
    get canManage(){return ['ADMIN','HR'].includes(this.auth.profile()?.role||'');}
    get canSearch(){return this.canManage||!!this.auth.profile()?.departmentHead;}
    get canReadPrivate(){return this.ownProfile||this.canSearch;}
    get ownProfile(){return !!this.selected && this.selected.employeeCode===this.auth.profile()?.employeeCode;}
    ngOnInit() { this.auth.loadCurrentUser().pipe(takeUntilDestroyed(this.destroy)).subscribe({ next: () => { this.load(); if (this.canSearch) this.loadEmployeeChoices(); }, error: () => this.load() }); this.loadChoices(); }
    loadEmployeeChoices() {
        this.employeeChoicesLoading = true;
        this.employeeChoicesError = '';
        this.api.all().pipe(takeUntilDestroyed(this.destroy), finalize(() => this.employeeChoicesLoading = false))
            .subscribe({next: rows => this.employeeChoices = rows.sort((a, b) => a.employeeCode.localeCompare(b.employeeCode, 'vi', {numeric: true})),
                error: () => this.employeeChoicesError = 'Không tải được danh sách chọn nhân viên.'});
    }
    loadChoices() { if(!this.canManage)return; this.choicesError = ''; for (const path of ['departments', 'positions'])
        this.api.choices(path).pipe(takeUntilDestroyed(this.destroy)).subscribe({ next: r => { const k = path === 'departments' ? 'department' : 'position'; const catalog = path === 'departments' ? this.auditDepartments : this.auditPositions; catalog.clear(); for (const option of r) catalog.set(Number(option[k + 'Id']), String(option[k + 'Name'])); const c = r.filter(x => x['isActive'] !== false).map(x => ({ id: Number(x[k + 'Id']), name: String(x[k + 'Name']) })); if (path === 'departments')
                this.departments = c;
            else
                this.positions = c; }, error: e => this.choicesError = (path === 'positions' ? 'Danh mục chức vụ: ' : 'Danh mục phòng ban: ') + errorMessage(e) }); }
    load(page = 0) { if (this.loading || this.saving)
        return; this.loading = true; this.error = ''; this.api.list(this.canSearch, page, this.applied.keyword, this.applied.status, this.applied.department, this.applied.position, this.applied.employeeId).pipe(takeUntilDestroyed(this.destroy), finalize(() => this.loading = false)).subscribe({ next: r => { if (Array.isArray(r)) {
            this.rows = r;
            this.page = 0;
            this.pages = r.length ? 1 : 0;
            this.total = r.length;
        }
        else {
            this.rows = r.content;
            this.page = r.number;
            this.pages = r.totalPages;
            this.total = r.totalElements;
        } }, error: e => this.error = errorMessage(e) }); }
    private applied = {keyword:'',status:'',department:'',position:'',employeeId:null as number | null};
    search() { if(this.loading||this.saving)return; this.applied={keyword:this.keyword.trim(),status:this.status,department:this.department,position:this.position,employeeId:this.employeeFilterId};this.close();this.load(0); }
    reset() { this.keyword = ''; this.employeeFilterId = null; this.status = ''; this.department = ''; this.position=''; this.search(); }
    select(row: Employee) { if (this.saving)
        return; this.auditRequest?.unsubscribe();this.auditRows=[];this.auditOpen=false;this.auditError='';this.auditLoading=false; this.detailRequest?.unsubscribe(); this.mode = 'detail'; this.selected = row; this.detailLoading = true; this.detailError = ''; this.detailRequest = this.api.detail(row.employeeId).pipe(takeUntilDestroyed(this.destroy)).subscribe({ next: r => { this.selected = r; this.detailLoading = false; }, error: e => { this.detailError = errorMessage(e); this.detailLoading = false; } }); }
    close() { if (this.saving)
        return; this.detailRequest?.unsubscribe(); this.mode = null; this.selected = null; this.detailLoading = false; this.detailError = ''; }
    edit(mode: string) {
        if (!this.canManage || this.saving || this.detailLoading || mode===this.mode) return;
        this.detailError=''; this.message=''; this.confirmTermination=false;
        this.attemptedSave=false; this.serverFieldErrors={};
        if(mode==='create') {this.selected=null;this.form={...blank(),privateFieldsEditable:true};this.mode=mode;return;}
        if(!this.selected)return;
        if(mode!=='edit' && mode!=='contact'){this.mode=mode;return;}
        // Always hydrate from the full record, never from the summary table response.
        // A failed reload leaves the current record/form intact instead of opening a blank editor.
        const id=this.selected.employeeId;
        this.detailLoading=true;
        this.detailRequest?.unsubscribe();
        this.detailRequest=this.api.detail(id).pipe(takeUntilDestroyed(this.destroy)).subscribe({
            next:p=>{this.hydrateProfile(p);
                this.detailLoading=false;this.mode=mode;},
            error:e=>{this.detailLoading=false;this.detailError=errorMessage(e);}
        });
    }
    save(f: NgForm) { if (this.saving)
        return; this.attemptedSave=true; if (f.invalid || Object.keys(this.formErrors).length) {
        f.control.markAllAsTouched();
        return;
    } this.saving = true; this.message = ''; this.detailError = ''; this.serverFieldErrors={}; this.api.save(this.mode!, this.selected?.employeeId, this.form).pipe(timeout(20000), takeUntilDestroyed(this.destroy), finalize(() => this.saving = false)).subscribe({ next: r => { this.saving = false; this.message = 'Đã lưu hồ sơ thành công.'; this.load(this.page); this.select(r); }, error: e => {
        this.detailError=errorMessage(e);
        if(e.status===409&&/Hồ sơ.*người khác|phiên bản/i.test(this.detailError)){
            this.conflictOpen=true;queueMicrotask(()=>this.conflictDialog?.nativeElement.showModal());
        }
        const fields=e.error?.fieldErrors ?? e.error?.errors ?? e.error?.loi_chi_tiet;
        if(fields && typeof fields==='object' && !Array.isArray(fields))for(const [key,value] of Object.entries(fields))this.serverFieldErrors[key==='reason'?'changeReason':key]=String(value);
        else if(e.status===409 && /mã nhân viên/i.test(this.detailError))this.serverFieldErrors['employeeCode']=this.detailError;
        f.control.markAllAsTouched();
    } }); }
    terminate() { if (this.saving || !this.confirmTermination || !this.selected || !this.exitDecision.reason.trim() || !this.exitDecision.lastWorkingDate || !this.exitDecision.decisionNumber.trim())
        return; const row = this.selected; this.message = ''; this.detailError = ''; this.saving = true; this.api.terminate(row.employeeId,this.exitDecision).pipe(takeUntilDestroyed(this.destroy), finalize(() => this.saving = false)).subscribe({ next: () => { this.saving = false; this.message = 'Đã lưu quyết định. Thôi việc được thực thi sau ngày làm cuối; lịch sử được giữ lại.'; this.load(this.page); this.select(row); }, error: e => this.detailError = errorMessage(e) }); }
    rehire() {
        if (this.saving || !this.selected || !this.admin || !this.rehireDate || !this.rehireReason.trim() || !this.form.departmentId || !this.form.positionId) return;
        const row = this.selected;
        this.saving = true; this.detailError = ''; this.message = '';
        this.api.rehire(row.employeeId, {effectiveDate:this.rehireDate,departmentId:this.form.departmentId,positionId:this.form.positionId,reason:this.rehireReason.trim()})
            .pipe(takeUntilDestroyed(this.destroy), finalize(()=>this.saving=false)).subscribe({
                next:()=>{this.saving=false;this.message='Đã tiếp nhận lại. Cần lập hợp đồng và kiểm tra quyền đăng nhập riêng.';this.load(this.page);this.select(row);},
                error:e=>this.detailError=errorMessage(e)
            });
    }

}
