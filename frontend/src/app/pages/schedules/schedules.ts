import {Pagination} from '../../shared/pagination';
import {PersonalCalendarComponent} from '../../shared/personal-calendar';
import {DetailFocusDirective} from '../../shared/detail-focus.directive';
import {RowViewDirective} from '../../shared/row-view.directive';
import {OvertimeCalendarComponent} from '../../shared/overtime-calendar';
import { TranslatePipe } from '../../shared/translate.pipe';
import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, finalize, forkJoin, switchMap, timer } from 'rxjs';
import { API_BASE_URL } from '../../services/api.config';

interface ScheduleHistory {id:number;action:string;actor:string;occurredAt:string;reason:string|null;beforeJson:string|null;afterJson:string|null;}
interface Employee { employeeId: number; employeeCode: string; fullName: string; }
interface Shift { shiftId: number; shiftCode: string; shiftName: string; startTime: string; endTime: string; endDayOffset: number; breakMinutes: number; active: boolean; }
interface ShiftOption { shift: Shift; breaksVerified: boolean; }
interface Schedule { scheduleId: number; employeeId: number; shiftId: number | null; workDate: string; plannedStart: string; plannedEnd: string; plannedBreakMinutes: number; note: string | null; }
interface Row { schedule: Schedule; employeeLabel: string; shiftLabel: string; breakDetailStatus: string; state: string; cancellationReason?: string | null; }
interface Page<T> { items: T[]; page: number; size: number; totalElements: number; totalPages: number; }
interface RequestRow { requestId: number; employeeId: number; requestCode: string; requestType: string; status: string; reason: string; submittedAt: string; createdBy: number | null; processedBy: number | null; }
interface ChangeDetail { request: RequestRow; scheduleId: number; originalStart: string; originalEnd: string; proposedShiftId: number | null; proposedStart: string; proposedEnd: string; employeeLabel: string; shiftLabel: string; breaks: Array<{ start: string; end: string }>; }
interface EmployeeLookup { open?:boolean; text: string; selected: Employee | null; options: Employee[]; error: string; loading: boolean; subscription?: Subscription; }
interface ShiftLookup { text: string; selected: ShiftOption | null; error: string; }
const employeeLookup = (): EmployeeLookup => ({ text: '', selected: null, options: [], error: '', loading: false });
const shiftLookup = (): ShiftLookup => ({ text: '', selected: null, error: '' });

@Component({ selector: 'app-schedules', standalone: true, imports:[Pagination,PersonalCalendarComponent,DetailFocusDirective,RowViewDirective,OvertimeCalendarComponent, CommonModule, FormsModule, TranslatePipe], templateUrl: './schedules.html', styleUrl: '../work-module.css' })
export class SchedulesPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly url = `${API_BASE_URL}/management/schedule-workspace`;
  loading = false; saving = false; lookupLoading = false;
  error = ''; lookupError = ''; sortDirection:'asc'|'desc'='desc';
  private successMessage='';private successQueue:string[]=[];private successTimer:Subscription|null=null;private successStarted=0;private successBatch=false;
  get success(){return this.successMessage;}
  set success(message:string){
    if(!message){this.successTimer?.unsubscribe();this.successQueue=[];this.successMessage='';this.successBatch=false;return;}
    this.successQueue.push(message);
    if(!this.successMessage)this.nextSuccess();
    else if(!this.successBatch){this.successBatch=true;this.scheduleSuccess(Math.max(0,5000-(Date.now()-this.successStarted)));}
  }
  constructor(){this.destroyRef.onDestroy(()=>{this.successTimer?.unsubscribe();this.successQueue=[];});}
  private nextSuccess(){this.successMessage=this.successQueue.shift()??'';if(!this.successMessage){this.successBatch=false;return;}this.successStarted=Date.now();this.scheduleSuccess(this.successBatch?5000:10000);}
  private scheduleSuccess(delay:number){this.successTimer?.unsubscribe();this.successTimer=timer(delay).subscribe(()=>this.nextSuccess());}
  sortSchedules(direction:'asc'|'desc'){if(this.busy)return;this.sortDirection=direction;this.closeDetails();this.load();}
  chooseEmployee(state:EmployeeLookup,employee:Employee){state.subscription?.unsubscribe();state.selected=employee;state.text=this.employeeLabel(employee);state.options=[];state.open=false;state.error='';state.loading=false;}

  detailFocusVersion=0;
  personal=false;templateBreaks:Array<{start:string;end:string}>=[];templateBreakError='';
  switchScheduleScope(personal:boolean):void {if(this.busy)return;this.personal=personal;this.closeDetails();this.filterEmployee=employeeLookup();this.departmentId='';this.load();}
  saveTemplateBreaks():void {
    const option=this.formShift.selected;if(!option||this.busy)return;const shift=option.shift;const start=this.toMinute(shift.startTime);const duration=this.toMinute(shift.endTime)+shift.endDayOffset*1440-start;
    const offsets=this.templateBreaks.map(b=>{let a=this.toMinute(b.start)-start,z=this.toMinute(b.end)-start;if(shift.endDayOffset===1){if(a<0)a+=1440;if(z<=0)z+=1440;}return {startMinute:a,endMinute:z};}).sort((a,b)=>a.startMinute-b.startMinute);
    let previous=0;for(const b of offsets){if(!Number.isFinite(b.startMinute)||!Number.isFinite(b.endMinute)||b.startMinute<previous||b.endMinute<=b.startMinute||b.endMinute>duration){this.templateBreakError='Giờ nghỉ phải nằm trong ca, kết thúc sau bắt đầu và không chồng lấn.';return;}previous=b.endMinute;}
    if(!offsets.length||offsets.reduce((n,b)=>n+b.endMinute-b.startMinute,0)!==shift.breakMinutes){this.templateBreakError='Tổng các khoảng nghỉ phải bằng '+shift.breakMinutes+' phút của ca.';return;}
    if(!window.confirm('Lưu giờ nghỉ chi tiết cho ca '+shift.shiftCode+'? Mẫu ca sẽ dùng giờ nghỉ này cho các lịch tạo mới.'))return;
    this.saving=true;this.templateBreakError='';this.http.put<Shift>(API_BASE_URL+'/management/shifts/'+shift.shiftId,{shift,breaks:offsets}).pipe(takeUntilDestroyed(this.destroyRef),finalize(()=>this.saving=false)).subscribe({next:saved=>{const updated={shift:saved,breaksVerified:true};this.shifts=this.shifts.map(s=>s.shift.shiftId===saved.shiftId?updated:s);this.formShift.selected=updated;this.formShift.text=this.shiftLabel(updated);this.success='Đã bổ sung giờ nghỉ cho ca. Bạn có thể lưu lịch làm việc.';this.templateBreaks=[];},error:e=>this.templateBreakError=this.message(e)});
  }
  private toMinute(value:string):number {const match=value.match(/^(\d{2}):(\d{2})/);return match&&+match[1]<24&&+match[2]<60?+match[1]*60+(+match[2]):NaN;}
  canManage = false; canSubmitOwn = false; ownEmployeeId: number | null = null;
  departments: Array<{ departmentId: number; departmentName: string }> = [];
  shifts: ShiftOption[] = [];
  records: Row[] = []; selected: Row | null = null;
  page = 0; totalPages = 0; totalElements = 0;
  from = ''; to = ''; departmentId = '';
  filterEmployee = employeeLookup(); formEmployee = employeeLookup();
  filterShift = shiftLookup(); formShift = shiftLookup(); changeShift = shiftLookup();
  mode: 'view' | 'create' | 'edit' | 'lock' | 'unlock' | 'cancel' = 'view';
  private stateReturnMode: 'view' | 'edit' = 'view';
  formDate = this.today(); formNote = ''; formError = '';
  history: ScheduleHistory[] = []; historyLoading=false; historyError='';
  loadHistory(): void {
    if(!this.selected)return;const id=this.selected.schedule.scheduleId;
    this.history=[];this.historyError='';this.historyLoading=true;
    this.http.get<ScheduleHistory[]>(this.url+'/schedules/'+id+'/history').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next:rows=>{if(this.selected?.schedule.scheduleId===id){this.history=rows;this.historyLoading=false;}},
      error:e=>{if(this.selected?.schedule.scheduleId===id){this.historyError=this.message(e);this.historyLoading=false;}}
    });
  }
  historyLabel(action:string):string {return ({WORK_SCHEDULE_CREATE:'Tạo lịch',WORK_SCHEDULE_UPDATE:'Sửa lịch',WORK_SCHEDULE_EDIT_LOCKED:'Khóa chỉnh sửa',WORK_SCHEDULE_EDIT_UNLOCKED:'Mở khóa chỉnh sửa',WORK_SCHEDULE_CANCELLED:'Hủy lịch'} as Record<string,string>)[action]??action;}
  historyChanges(item:ScheduleHistory):Array<{label:string;before:string;after:string}> {
    const labels:Record<string,string>={employeeId:'ID nhân viên',shiftId:'ID ca',plannedStart:'Bắt đầu',plannedEnd:'Kết thúc',plannedBreakMinutes:'Phút nghỉ',note:'Ghi chú',state:'Trạng thái'};
    try {const before=JSON.parse(item.beforeJson||'{}')||{},after=JSON.parse(item.afterJson||'{}')||{};
      return Object.entries(labels).filter(([key])=>Object.hasOwn(after,key)&&JSON.stringify(before[key])!==JSON.stringify(after[key])).map(([key,label])=>({label,before:key==='state'?this.statusLabel(before[key]??'—'):String(before[key]??'—'),after:key==='state'?this.statusLabel(after[key]??'—'):String(after[key]??'—')}));
    }catch{return [];}
  }
  stateLoading = false; scheduleState = ''; stateError = ''; actionReason = '';
  private stateVersion = 0;
  private currentTime = Date.now();
  requestsOpen = false; requestLoading = false; requestBusy = false; requestError = ''; requestFeedback = '';
  requestStatus = ''; requestPage = 0; requestPages = 0; requestTotal = 0;
  requests: RequestRow[] = []; requestDetail: ChangeDetail | null = null;
  requestMode: 'list' | 'new' | 'detail' = 'list';
  rejectionReason = ''; changeReason = ''; changeDate = this.today(); ownScheduleId = '';
  ownSchedules: Schedule[] = []; ownPage = 0; ownPages = 0; ownLoading = false;
  get busy(): boolean { return this.loading || this.saving || this.requestBusy; }
  get activeShifts(): ShiftOption[] { return this.shifts.filter(s => s.shift.active); }
  get formBreakMinutes(): number { return this.formShift.selected?.shift.breakMinutes ?? 0; }
  get formStart(): string { return this.shiftTime(this.formShift, this.formDate, false); }
  get formEnd(): string { return this.shiftTime(this.formShift, this.formDate, true); }
  get changeStart(): string { return this.shiftTime(this.changeShift, this.changeDate, false); }
  get changeEnd(): string { return this.shiftTime(this.changeShift, this.changeDate, true); }

  ngOnInit(): void {
    this.loadLookups(); this.load();
    timer(0, 1000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.currentTime = Date.now());
  }
  scheduleHasStarted(row: Row): boolean {
    // plannedStart is a business-local timestamp in UTC+07:00.
    const start = Date.parse(`${row.schedule.plannedStart}+07:00`);
    return !Number.isFinite(start) || start <= this.currentTime;
  }
  get canLockSelected(): boolean {
    return !!this.selected && this.canManage && !this.busy && !this.stateLoading && !this.stateError
      && this.scheduleState === 'ACTIVE';
  }
  get canCancelSelected(): boolean {
    return !!this.selected && this.canManage && !this.busy && !this.stateLoading && !this.stateError
      && ['ACTIVE', 'EDIT_LOCKED'].includes(this.scheduleState) && !this.scheduleHasStarted(this.selected);
  }
  cancellationDisabledReason(row: Row): string {
    if (row.state === 'CANCELLED') return 'Lịch đã được hủy.';
    if (this.scheduleHasStarted(row)) return `Không thể hủy: lịch đã bắt đầu lúc ${this.displayTime(row.schedule.plannedStart)}. Chỉ được hủy trước giờ bắt đầu.`;
    if (!this.canManage) return 'Tài khoản không có quyền hủy lịch.';
    return '';
  }
  scheduleNotes(row: Row): string {
    const notes = [row.schedule.note?.trim()];
    if (row.state === 'CANCELLED') notes.push(row.cancellationReason?.trim() ? 'Lý do hủy: ' + row.cancellationReason.trim() : 'Lịch đã hủy.');
    else notes.push(this.cancellationDisabledReason(row));
    return notes.filter(Boolean).join('\n') || '—';
  }
  get allVisibleSchedulesStarted(): boolean {
    return this.records.length > 0 && this.records.every(row => row.state === 'CANCELLED' || this.scheduleHasStarted(row));
  }
  paginationPages(total: number, current: number): Array<number | '...'> {
    if (total <= 0) return [];
    if (total <= 7) return Array.from({ length: total }, (_, i) => i);
    let start = Math.max(1, current - 2), end = Math.min(total - 2, current + 2);
    if (current <= 2) end = Math.min(total - 2, 4);
    if (current >= total - 3) start = Math.max(1, total - 5);
    const indexes = [0, ...Array.from({ length: Math.max(0, end - start + 1) }, (_, i) => start + i), total - 1];
    const pages: Array<number | '...'> = [];
    for (const index of indexes) {
      const previous = pages[pages.length - 1];
      if (typeof previous === 'number' && index - previous === 2) pages.push(previous + 1);
      else if (typeof previous === 'number' && index - previous > 2) pages.push('...');
      pages.push(index);
    }
    return pages;
  }
  today(): string { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; }
  private normalize(value: string): string { return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').trim().toLowerCase(); }
  employeeLabel(e: Employee): string { return `${e.employeeCode} - ${e.fullName}`; }
  shiftLabel(s: ShiftOption): string { return `${s.shift.shiftCode} - ${s.shift.shiftName}`; }
  displayTime(value: string): string { return value ? value.replace('T', ' ').slice(0, 16) : '—'; }
  statusClass(status: string): string {
    const colors: Record<string, string> = {
      ACTIVE: 'bg-success-subtle text-success-emphasis border-success-subtle',
      EDIT_LOCKED: 'bg-warning-subtle text-warning-emphasis border-warning-subtle',
      CANCELLED: 'bg-danger-subtle text-danger-emphasis border-danger-subtle',
      CHO_DUYET: 'bg-warning-subtle text-warning-emphasis border-warning-subtle',
      DA_DUYET: 'bg-success-subtle text-success-emphasis border-success-subtle',
      TU_CHOI: 'bg-danger-subtle text-danger-emphasis border-danger-subtle'
    };
    return 'badge border rounded-pill px-3 py-2 ' + (colors[status] ?? 'bg-secondary-subtle text-secondary-emphasis border-secondary-subtle');
  }
  statusLabel(status: string): string { return ({ CHO_DUYET: 'Chờ duyệt', DA_DUYET: 'Đã duyệt', TU_CHOI: 'Từ chối', HET_HIEU_LUC: 'Hết hiệu lực', ACTIVE: 'Đang hiệu lực', EDIT_LOCKED: 'Khóa chỉnh sửa', CANCELLED: 'Đã hủy' } as Record<string, string>)[status] ?? status; }
  private shiftTime(state: ShiftLookup, date: string, end: boolean): string {
    if (!state.selected || !/^\d{4}-\d{2}-\d{2}$/.test(date)) return '';
    const shift = state.selected.shift;
    const d = new Date(`${date}T12:00:00`);
    if (end) d.setDate(d.getDate() + shift.endDayOffset);
    const day = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    return `${day}T${(end ? shift.endTime : shift.startTime).slice(0, 8)}`;
  }
  loadLookups(): void {
    this.lookupLoading = true; this.lookupError = '';
    forkJoin({
      context: this.http.get<{ canManage: boolean; canSubmitOwn: boolean; employeeId: number | null }>(`${this.url}/context`),
      shifts: this.http.get<ShiftOption[]>(`${this.url}/shifts`),
      departments: this.http.get<Array<{ departmentId: number; departmentName: string }>>(`${API_BASE_URL}/management/schedules/filter-departments`),
    }).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.lookupLoading = false)).subscribe({
      next: result => { this.canManage = result.context.canManage; this.canSubmitOwn = result.context.canSubmitOwn; this.ownEmployeeId = result.context.employeeId; this.shifts = result.shifts; this.departments = result.departments; },
      error: e => this.lookupError = this.message(e),
    });
  }
  employeeChanged(state: EmployeeLookup, text: string): void {
    state.subscription?.unsubscribe(); state.text = text; state.error = ''; state.loading = false;
    const q = this.normalize(text);state.open=!!q;
    if(!q){state.selected=null;state.options=[];return;}
    state.selected = state.options.find(e => this.normalize(this.employeeLabel(e)) === q) ?? null;
    if (state.selected) return;
    if (/^-\d+(\.\d+)?$/.test(text.trim())) { state.options = []; state.error = 'Nhân viên không hợp lệ. Không được nhập số âm.'; return; }
    if (text.length > 200) { state.options = []; state.error = 'Mã hoặc tên nhân viên quá dài.'; return; }
    state.options=[];state.loading = true;
    let params = new HttpParams().set('q', text.trim());
    if (state === this.formEmployee) params = params.set('date', this.formDate);
    state.subscription = timer(200).pipe(
      switchMap(() => this.http.get<Employee[]>(`${this.url}/employees`, { params })),
      takeUntilDestroyed(this.destroyRef), finalize(() => state.loading = false),
    ).subscribe({
      next: options => {
        state.options = options;
        const exact = options.filter(e => [this.employeeLabel(e), e.employeeCode, e.fullName].some(v => this.normalize(v) === q));
        state.selected = exact.length === 1 ? exact[0] : null;
        if (text.trim() && !options.length) state.error = 'Mã hoặc tên nhân viên không hợp lệ hoặc ngoài phạm vi quản lý.';
      },
      error: e => { state.options = []; state.error = this.message(e); },
    });
  }
  employeeFocus(state: EmployeeLookup): void { state.open=!!state.text.trim();if (!state.selected && !state.options.length && !state.loading) this.employeeChanged(state, state.text); }
  validateEmployee(state: EmployeeLookup, required: boolean): boolean {
    if (!state.text.trim() && !required) { state.error = ''; return true; }
    if (!state.selected) { state.error = state.error || 'Nhân viên không hợp lệ. Hãy chọn mã - tên từ danh sách gợi ý.'; return false; }
    state.text = this.employeeLabel(state.selected); state.error = ''; return true;
  }
  shiftChanged(state: ShiftLookup, text: string, onlyActive: boolean): void {
    state.text = text; state.error = '';
    const options = onlyActive ? this.activeShifts : this.shifts;
    const q = this.normalize(text);
    const exact = options.filter(s => [this.shiftLabel(s), s.shift.shiftCode, s.shift.shiftName].some(v => this.normalize(v) === q));
    state.selected = exact.length === 1 ? exact[0] : null;
    if (text.trim() && !state.selected) state.error = 'Ca không hợp lệ. Hãy chọn mã - tên ca từ danh sách.';
  }
  validateShift(state: ShiftLookup, required: boolean): boolean {
    if (!state.text.trim() && !required) { state.error = ''; return true; }
    if (!state.selected) { state.error = 'Ca không hợp lệ. Hãy chọn ca trong danh sách.'; return false; }
    state.text = this.shiftLabel(state.selected); state.error = ''; return true;
  }
  formDateChanged(): void { if (this.formEmployee.text) this.employeeChanged(this.formEmployee, this.formEmployee.text); }
  load(reset = true): void {
    if (this.loading || this.saving) return;
    if (!this.validateEmployee(this.filterEmployee, false) || !this.validateShift(this.filterShift, false)) return;
    if (this.from && this.to && this.from > this.to) { this.error = 'Khoảng ngày không hợp lệ.'; return; }
    if (reset) this.page = 0;
    this.loading = true; this.error = '';
    let params = new HttpParams().set('from', this.from || '2000-01-01').set('to', this.to || '2100-12-31').set('page', this.page).set('sort',this.sortDirection);
    if (this.filterEmployee.selected) params = params.set('employeeId', this.filterEmployee.selected.employeeId);
    if (this.filterShift.selected) params = params.set('shiftId', this.filterShift.selected.shift.shiftId);
    if (this.departmentId) params = params.set('departmentId', this.departmentId);
    this.http.get<Page<Row>>(`${this.url}/${this.personal?'my-search':'search'}`, { params }).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading = false)).subscribe({
      next: result => { this.records = result.items; this.page = result.page; this.totalPages = result.totalPages; this.totalElements = result.totalElements; },
      error: e => this.error = this.message(e),
    });
  }
  clearFilters(): void {
    this.filterEmployee.subscription?.unsubscribe(); this.filterEmployee = employeeLookup(); this.filterShift = shiftLookup();
    this.from = ''; this.to = ''; this.departmentId = ''; this.load();
  }
  goPage(page: number): void { if (!this.busy && page >= 0 && page < this.totalPages) { this.page = page; this.load(false); } }
  createForm(): void {
    if (this.personal || !this.canManage || this.busy) return;
    this.formEmployee.subscription?.unsubscribe(); this.formEmployee = employeeLookup(); this.formShift = shiftLookup();
    this.templateBreaks=[];this.templateBreakError='';this.mode = 'create'; this.selected = null; this.formDate = this.today(); this.formNote = ''; this.formError = ''; this.stateError = '';
  }
  view(row: Row, action?: 'lock' | 'unlock' | 'cancel'): void {
    if (this.busy) return;
    ++this.detailFocusVersion;this.selected = row; this.mode = 'view'; this.formError = ''; this.scheduleState = ''; this.stateError = ''; this.actionReason = '';
    if(this.personal){this.scheduleState=row.state;this.stateLoading=false;return;}
    this.loadHistory();
    const version = ++this.stateVersion; this.stateLoading = true;
    this.http.get<{ scheduleId: number; state: string }>(`${API_BASE_URL}/management/schedules/${row.schedule.scheduleId}/state`)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: r => {
          if (version === this.stateVersion) {
            this.scheduleState = r.state; row.state = r.state; this.stateLoading = false;
            if (!action && this.mode === 'edit' && r.state !== 'ACTIVE') { this.mode = 'view'; this.formError = 'Lịch đã khóa sửa hoặc đã hủy, không được chỉnh sửa.'; }
            if (action) this.startStateAction(action);
          }
        }, error: e => { if (version === this.stateVersion) { this.stateError = this.message(e); this.stateLoading = false; } }
      });
  }
  editForm(row: Row): void {
    if (row.state !== 'ACTIVE') { this.view(row); this.formError = row.state === 'CANCELLED' ? 'Lịch đã hủy, không thể sửa.' : 'Lịch đã khóa sửa. Cần mở khóa trước khi chỉnh sửa.'; return; }
    this.view(row); if (this.selected !== row || this.busy) return;
    this.mode = 'edit'; this.formDate = row.schedule.workDate; this.formNote = row.schedule.note ?? '';
    const pieces = row.employeeLabel.split(' - ');
    const employee = { employeeId: row.schedule.employeeId, employeeCode: pieces[0] ?? '', fullName: pieces.slice(1).join(' - ') };
    this.formEmployee.subscription?.unsubscribe(); this.formEmployee = { ...employeeLookup(), text: row.employeeLabel, selected: employee, options: [employee] };
    const shift = this.shifts.find(s => s.shift.shiftId === row.schedule.shiftId) ?? null;
    this.formShift = { text: shift ? this.shiftLabel(shift) : row.shiftLabel, selected: shift, error: shift ? '' : 'Ca của lịch không còn trong danh sách.' };
  }
  submitForm(): void {
    if (this.busy || !this.canManage || !['create', 'edit'].includes(this.mode)) return;
    if (this.mode === 'edit' && (this.stateLoading || this.stateError || this.scheduleState !== 'ACTIVE')) { this.formError = 'Lịch đã khóa sửa, đã hủy hoặc chưa xác minh được trạng thái; không thể lưu.'; return; }
    const employeeValid = this.validateEmployee(this.formEmployee, true), shiftValid = this.validateShift(this.formShift, true);
    if (!employeeValid || !shiftValid || !this.formEmployee.selected || !this.formShift.selected) return;
    if (!this.formShift.selected.breaksVerified) { this.formError = 'Ca chưa có đủ giờ nghỉ chi tiết. Hãy bổ sung khoảng nghỉ tại Danh mục ca trước.'; return; }
    if (!/^\d{4}-\d{2}-\d{2}$/.test(this.formDate) || this.formNote.length > 1000) { this.formError = 'Ngày làm hoặc ghi chú không hợp lệ.'; return; }
    const creating = this.mode === 'create';
    if (!window.confirm(`${creating ? 'Thêm lịch làm việc' : 'Lưu thay đổi lịch'}?\n${this.formEmployee.text}\n${this.formShift.text}\nNgày: ${this.formDate}\nNghỉ: ${this.formBreakMinutes} phút`)) return;
    const body = { employeeId: this.formEmployee.selected.employeeId, shiftId: this.formShift.selected.shift.shiftId, workDate: this.formDate, note: this.formNote.trim() };
    const request = creating ? this.http.post<Schedule>(`${this.url}/schedules`, body) : this.http.put<Schedule>(`${this.url}/schedules/${this.selected!.schedule.scheduleId}`, body);
    this.saving = true; this.formError = '';
    request.pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving = false)).subscribe({
      next: () => { this.mode = 'view'; this.selected = null; this.success = creating ? 'Đã thêm lịch làm việc.' : 'Đã cập nhật lịch làm việc.'; if(creating)this.sortDirection='desc';this.saving = false; this.load(); },
      error: e => this.formError = this.message(e),
    });
  }
  cancelForm(): void { if (!this.saving) { this.mode = ['lock', 'unlock', 'cancel'].includes(this.mode) ? this.stateReturnMode : 'view'; this.formError = ''; } }
  closeDetails(): void { if (!this.saving) { this.selected = null; this.mode = 'view'; this.stateLoading = false; ++this.stateVersion; } }
  startStateAction(action: 'lock' | 'unlock' | 'cancel'): void {
    this.currentTime = Date.now();
    if (action === 'lock' && !this.canLockSelected) {
      this.formError = 'Trạng thái lịch không cho phép khóa sửa.';
      return;
    }
    if (action === 'cancel' && !this.canCancelSelected) { this.formError = 'Chỉ được hủy lịch còn hiệu lực và chưa bắt đầu.'; return; }
    if (action === 'unlock' && this.scheduleState !== 'EDIT_LOCKED') { this.formError = 'Lịch không ở trạng thái khóa sửa.'; return; }
    if (this.selected && !this.busy && !this.stateLoading && !this.stateError) {
      this.stateReturnMode = this.mode === 'edit' ? 'edit' : 'view'; this.mode = action; this.actionReason = ''; this.formError = '';
    }
  }
  submitStateAction(): void {
    if (!this.selected || this.busy || !['lock', 'unlock', 'cancel'].includes(this.mode)) return;
    this.currentTime = Date.now();
    if (this.mode === 'lock' && !this.canLockSelected) { this.formError = 'Trạng thái không còn cho phép khóa sửa.'; return; }
    if (this.mode === 'cancel' && !this.canCancelSelected) { this.formError = 'Lịch đã bắt đầu hoặc đã hủy, không thể hủy lịch.'; return; }
    const reason = this.actionReason.trim(); if (!reason || reason.length > 1000) { this.formError = 'Lý do bắt buộc, tối đa 1000 ký tự.'; return; }
    const action = this.mode;
    const verb = action === 'cancel' ? 'Hủy lịch' : action === 'lock' ? 'Khóa sửa lịch' : 'Mở khóa sửa lịch';
    const explanation = action === 'cancel' ? 'Lịch sẽ mất hiệu lực, bản ghi vẫn được giữ trong lịch sử.' : action === 'lock' ? 'ADMIN/HR sẽ không thể sửa cho tới khi lịch được mở khóa. Các thay đổi chưa lưu không được lưu.' : 'Lịch được phép chỉnh sửa lại theo điều kiện nghiệp vụ.';
    if (!window.confirm(`${verb} #${this.selected.schedule.scheduleId}?\n\n${explanation}\nLý do: ${reason}`)) return;
    this.currentTime = Date.now();
    if (action === 'cancel' && !this.canCancelSelected) { this.formError = 'Lịch đã bắt đầu trong lúc chờ xác nhận, không được hủy.'; return; }
    this.saving = true;
    const endpoint = action === 'cancel' ? 'cancel' : action === 'lock' ? 'lock-edit' : 'unlock-edit';
    const id = this.selected.schedule.scheduleId;
    this.http.post<{ state: string }>(`${API_BASE_URL}/management/schedules/${id}/${endpoint}`, { reason })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving = false)).subscribe({
        next: r => {
          this.scheduleState = r.state; if (this.selected) { this.selected.state = r.state; if (action === 'cancel') this.selected.cancellationReason = reason; }
          this.records = this.records.map(row => row.schedule.scheduleId === id ? { ...row, state: r.state, ...(action === 'cancel' ? { cancellationReason: reason } : {}) } : row);
          this.mode = 'view'; this.formError = ''; this.success = `Đã ${verb.toLowerCase()} #${id}.`; this.loadHistory();
        }, error: e => this.formError = this.message(e)
      });
  }

  openRequests(): void { if (this.busy) return; this.requestsOpen = true; this.requestMode = 'list'; this.requestDetail = null; this.requestFeedback = ''; this.loadRequests(); }
  closeRequests(): void { if (!this.requestBusy) { this.requestsOpen = false; this.requestDetail = null; } }
  loadRequests(reset = true): void {
    if (this.requestLoading || this.requestBusy) return;
    if (reset) this.requestPage = 0;
    this.requestLoading = true; this.requestError = '';
    let params = new HttpParams().set('page', this.requestPage); if (this.requestStatus) params = params.set('status', this.requestStatus);
    this.http.get<Page<RequestRow>>(`${this.url}/requests`, { params }).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.requestLoading = false)).subscribe({
      next: r => { this.requests = r.items; this.requestPages = r.totalPages; this.requestTotal = r.totalElements; }, error: e => this.requestError = this.message(e),
    });
  }
  requestGoPage(page: number): void { if (page >= 0 && page < this.requestPages && !this.requestLoading) { this.requestPage = page; this.loadRequests(false); } }
  viewRequest(row: RequestRow): void {
    if (this.requestBusy || this.requestLoading) return;
    this.requestLoading = true; this.requestError = ''; this.rejectionReason = ''; this.requestDetail = null;
    this.http.get<ChangeDetail>(`${this.url}/requests/${row.requestId}`).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.requestLoading = false)).subscribe({ next: r => { this.requestDetail = r; this.requestMode = 'detail'; }, error: e => this.requestError = this.message(e) });
  }
  get canDecide(): boolean { return !!this.requestDetail && this.requestDetail.request.status === 'CHO_DUYET' && this.requestDetail.request.employeeId !== this.ownEmployeeId; }
  decideRequest(approve: boolean): void {
    if (!this.canDecide || !this.requestDetail || this.requestBusy) return;
    const reason = this.rejectionReason.trim(); if (!approve && !reason) { this.requestError = 'Từ chối yêu cầu cần có lý do.'; return; }
    if (!window.confirm(`${approve ? 'Duyệt' : 'Từ chối'} yêu cầu ${this.requestDetail.request.requestCode}?`)) return;
    const id = this.requestDetail.request.requestId; this.requestBusy = true; this.requestError = '';
    this.http.post<void>(`${this.url}/requests/${id}/decision`, { approve, rejectionReason: reason })
      .pipe(switchMap(() => this.http.get<ChangeDetail>(`${this.url}/requests/${id}`)), takeUntilDestroyed(this.destroyRef), finalize(() => this.requestBusy = false)).subscribe({
        next: r => { this.requestDetail = r; this.requestFeedback = `Trạng thái yêu cầu: ${this.statusLabel(r.request.status)}.`; this.requestBusy = false; this.loadRequests(false); this.load(false); }, error: e => this.requestError = this.message(e),
      });
  }
  newRequest(): void {
    if (!this.canSubmitOwn || this.requestBusy) return;
    this.requestMode = 'new'; this.requestError = ''; this.changeShift = shiftLookup(); this.changeDate = this.today(); this.changeReason = ''; this.ownScheduleId = ''; this.ownPage = 0; this.loadOwnSchedules();
  }
  loadOwnSchedules(): void {
    this.ownLoading = true;
    this.http.get<Page<Schedule>>(`${API_BASE_URL}/me/schedules`, { params: new HttpParams().set('from', this.today()).set('to', '2100-12-31').set('page', this.ownPage) })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.ownLoading = false)).subscribe({ next: r => { this.ownSchedules = r.items; this.ownPages = r.totalPages; }, error: e => this.requestError = this.message(e) });
  }
  ownGoPage(page: number): void { if (page >= 0 && page < this.ownPages && !this.ownLoading) { this.ownPage = page; this.ownScheduleId = ''; this.loadOwnSchedules(); } }
  submitChange(): void {
    if (this.requestBusy || !this.canSubmitOwn || !this.validateShift(this.changeShift, true) || !this.changeShift.selected) return;
    const id = Number(this.ownScheduleId);
    if (!this.ownSchedules.some(s => s.scheduleId === id) || !this.changeDate || !this.changeReason.trim() || this.changeReason.length > 1000) { this.requestError = 'Hãy chọn lịch của bạn, ngày mới và nhập lý do tối đa 1000 ký tự.'; return; }
    if (!this.changeShift.selected.breaksVerified) { this.requestError = 'Ca đề xuất chưa có đủ khoảng nghỉ chi tiết.'; return; }
    if (!window.confirm('Gửi yêu cầu đổi lịch để chờ phê duyệt?')) return;
    this.requestBusy = true; this.requestError = '';
    this.http.get<{ breaks: Array<{ startMinute: number; endMinute: number }> }>(`${API_BASE_URL}/management/shifts/${this.changeShift.selected.shift.shiftId}/details`).pipe(
      switchMap(detail => {
        const start = this.changeStart;
        const at = (offset: number) => { const date = new Date(start); date.setMinutes(date.getMinutes() + offset); const pad = (n: number) => String(n).padStart(2, '0'); return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`; };
        return this.http.post<{ requestId: number }>(`${this.url}/requests`, { scheduleId: id, proposedShiftId: this.changeShift.selected!.shift.shiftId, proposedStart: start, proposedEnd: this.changeEnd, reason: this.changeReason.trim(), proposedBreaks: detail.breaks.map(b => ({ start: at(b.startMinute), end: at(b.endMinute) })) });
      }), takeUntilDestroyed(this.destroyRef), finalize(() => this.requestBusy = false),
    ).subscribe({ next: () => { this.requestMode = 'list'; this.requestFeedback = 'Đã gửi yêu cầu đổi lịch.'; this.requestBusy = false; this.loadRequests(); }, error: e => this.requestError = this.message(e) });
  }
  private message(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) return 'Không thể xử lý yêu cầu.';
    const data = error.error as Record<string, unknown> | null;
    const detail = data && typeof data === 'object' ? (data['detail'] ?? data['loi_chi_tiet'] ?? data['message']) : null;
    return typeof detail === 'string' ? detail : error.status === 403 ? 'Không có quyền hoặc ngoài phạm vi quản lý.' : error.status === 401 ? 'Phiên đăng nhập không hợp lệ.' : `Không thể xử lý yêu cầu (HTTP ${error.status || 'network'}).`;
  }
}
