import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subscription, finalize, timer } from 'rxjs';
import { AttendanceApiService, AttendanceContext, AttendanceRow, AttendanceDetail, AttendanceRequest, RequestDetail, RecordFilter, Stats, Correction } from './attendance-api.service';

@Component({ selector: 'app-attendance', standalone: true, imports: [CommonModule, FormsModule, RouterLink], templateUrl: './attendance.html', styleUrls: ['../work-module.css', './attendance.css'] })
export class AttendancePage implements OnInit {
  private readonly api = inject(AttendanceApiService);
  private readonly destroyRef = inject(DestroyRef);
  context: AttendanceContext | null = null;
  tab: 'mine' | 'manage' | 'requests' = 'manage';
  loading = false; saving = false; detailLoading = false; todayLoading = false; error = ''; actionError = ''; feedback = '';
  rows: AttendanceRow[] = []; todayRows: AttendanceRow[] = []; detail: AttendanceDetail | null = null;
  departments: Array<{departmentId: number; departmentName: string}> = [];
  from = this.date(-30); to = this.date(); q = ''; departmentId = ''; verification = ''; anomaly = '';
  page = 0; total = 0; pages = 0; stats: Stats | null = null;
  private applied: RecordFilter | null = null;
  mode: 'view' | 'manual' | 'edit' | 'proposal' | 'unconfirm' = 'view';
  actualStart = ''; actualEnd = ''; breakMinutes = 0; result = 'DI_LAM'; reason = '';
  requestRows: AttendanceRequest[] = []; requestDetail: RequestDetail | null = null;
  requestsMine = false; requestFrom = this.date(-30); requestTo = this.date(); requestType = ''; requestStatus = 'CHO_XU_LY';
  requestPage = 0; requestPages = 0; requestTotal = 0; requestLoading = false; requestDetailLoading = false;
  acceptedEnd = ''; decisionReason = '';
  private notificationQueue: string[] = [];
  private notificationTimer: Subscription | null = null;
  private notificationStartedAt = 0;
  private notificationBatch = false;
  get pendingNotifications(): number { return this.notificationQueue.length; }
  constructor() {
    this.destroyRef.onDestroy(() => { this.notificationTimer?.unsubscribe(); this.notificationQueue = []; });
  }
  private notify(message: string): void {
    this.notificationQueue.push(message);
    if (!this.feedback) this.nextNotification();
    else if (!this.notificationBatch) {
      this.notificationBatch = true;
      this.scheduleNotification(Math.max(0, 3000 - (Date.now() - this.notificationStartedAt)));
    }
  }
  private scheduleNotification(duration: number): void {
    this.notificationTimer?.unsubscribe();
    this.notificationTimer = timer(duration).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.nextNotification());
  }
  private nextNotification(): void {
    this.notificationTimer?.unsubscribe();
    this.feedback = this.notificationQueue.shift() ?? '';
    if (!this.feedback) { this.notificationBatch = false; return; }
    this.notificationStartedAt = Date.now();
    this.scheduleNotification(this.notificationBatch ? 3000 : 10000);
  }
  employeeName(row: AttendanceRow): string {
    const separator = row.employeeLabel.indexOf(' - ');
    return separator >= 0 ? row.employeeLabel.slice(separator + 3).trim() : row.employeeLabel;
  }
  resultSeverity(row: AttendanceRow): 'normal' | 'notice' | 'warning' | 'danger' | 'pending' {
    if (row.attendanceResult === 'VANG' || row.anomalies.some(a => ['THIEU_VAO','THIEU_RA','CHONG_LAN'].includes(a))) return 'danger';
    if (row.anomalies.some(a => ['DI_MUON','VE_SOM'].includes(a))) return 'warning';
    if (row.anomalies.length) return 'notice';
    return row.attendanceResult === 'DI_LAM' ? 'normal' : 'pending';
  }
  severityLabel(row: AttendanceRow): string {
    return {normal:'Bình thường',notice:'Cần đối chiếu',warning:'Cảnh báo',danger:'Cần xử lý',pending:'Chưa ghi nhận'}[this.resultSeverity(row)];
  }
  private detailVersion = 0;
  private todayVersion = 0;
  private requestVersion = 0;
  get busy(): boolean { return this.saving || this.loading || this.requestLoading; }
  ngOnInit(): void {
    this.loading = true;
    this.api.context().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: context => {
        this.context = context; this.tab = context.manage ? 'manage' : 'mine'; this.requestsMine = !context.manage;
        this.loading = false; this.search();
        if (context.manage) this.api.departments().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next: d => this.departments = d, error: e => this.error = this.api.message(e)});
      }, error: e => {this.loading=false;this.error=this.api.message(e);},
    });
    timer(30000, 30000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => { if (this.tab === 'mine' && !this.saving) this.loadToday(); });
  }
  date(offset = 0): string { const d = new Date(); d.setDate(d.getDate() + offset); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; }
  time(v: string | null | undefined): string { return v ? v.replace('T',' ').slice(0,19) : '—'; }
  utc(v: string | null | undefined): string { if (!v) return '—'; const d = new Date(v.endsWith('Z') ? v : v + 'Z'); return new Intl.DateTimeFormat('vi-VN', {dateStyle:'short', timeStyle:'short', timeZone:'Asia/Ho_Chi_Minh'}).format(d); }
  label(value: string | null | undefined): string { return ({CHUA_XAC_NHAN:'Chưa xác nhận',DA_XAC_NHAN:'Đã xác nhận',DI_LAM:'Đi làm',VANG:'Vắng',DI_MUON:'Đi muộn',VE_SOM:'Về sớm',THIEU_VAO:'Thiếu giờ vào',THIEU_RA:'Thiếu giờ ra',NGOAI_LICH:'Giờ ngoài lịch',CHONG_LAN:'Chồng lấn công khác',CHO_XU_LY:'Chờ xử lý',DANG_XU_LY:'Đang xử lý',DA_CHAP_NHAN:'Đã chấp nhận',TU_CHOI:'Từ chối',MISSING_CHECKOUT:'Bổ sung check-out',CONFIRMED_CORRECTION:'Sửa công đã xác nhận'} as Record<string,string>)[value ?? ''] ?? (value || 'Chưa có công'); }
  actionLabel(action: string): string { return ({ATTENDANCE_CREATE:'Nhập công',ATTENDANCE_CORRECT:'Sửa công',ATTENDANCE_CONFIRM:'Xác nhận',ATTENDANCE_UNCONFIRM:'Hủy xác nhận',ATTENDANCE_SELF_CHECK_IN:'Check-in',ATTENDANCE_SELF_CHECK_OUT:'Check-out',ATTENDANCE_REQUEST_SUBMITTED:'Gửi yêu cầu',ATTENDANCE_REQUEST_RECEIVED:'Tiếp nhận',ATTENDANCE_REQUEST_APPROVED:'Chấp nhận',ATTENDANCE_REQUEST_REJECTED:'Từ chối',ATTENDANCE_PORTAL_REQUEST_APPLIED:'Áp dụng điều chỉnh',ATTENDANCE_CORRECTION_APPLIED:'Áp dụng điều chỉnh'} as Record<string,string>)[action] ?? action; }
  shiftState(r: AttendanceRow): string { if (r.actualEnd) return 'Đã check-out'; if (r.actualStart) return r.anomalies.includes('THIEU_RA') ? 'Thiếu check-out' : 'Đã check-in'; if (r.canCheckIn) return 'Được check-in'; return this.context && r.plannedStart > this.context.serverNow ? 'Chưa đến giờ' : 'Chưa ghi nhận giờ vào'; }
  switchTab(tab: 'mine' | 'manage' | 'requests'): void {
    if (this.busy || !this.context || tab === 'mine' && !this.context.mine || tab === 'manage' && !this.context.manage) return;
    this.tab = tab; this.closeDetail(); this.requestDetail = null; ++this.requestVersion; ++this.todayVersion; this.error = ''; this.stats = null;
    if (tab === 'requests') this.loadRequests(); else this.search();
  }
  private validRange(from: string, to: string): boolean { return !!from && !!to && from <= to && (Date.parse(to) - Date.parse(from)) / 86400000 <= 366; }
  search(): void {
    if (this.busy || !this.context) return;
    if (!this.validRange(this.from, this.to)) { this.error = 'Chọn khoảng ngày hợp lệ, tối đa 366 ngày.'; return; }
    if (/^-\d+(\.\d+)?$/.test(this.q.trim())) { this.error = 'Mã hoặc tên nhân viên không hợp lệ, không được nhập số âm.'; return; }
    this.applied = {mine:this.tab === 'mine', from:this.from, to:this.to, q:this.tab === 'mine' ? '' : this.q.trim(), departmentId:this.tab === 'mine' ? '' : this.departmentId, verification:this.verification, anomaly:this.anomaly, page:0};
    this.loadRecords(0); if (this.tab === 'mine') this.loadToday();
  }
  loadRecords(page = this.page): void {
    if (!this.applied || this.saving || this.loading) return;
    this.loading = true; this.error = '';
    this.api.records({...this.applied, page}).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading = false)).subscribe({
      next: r => {this.rows = r.items; this.page = r.page; this.total = r.totalElements; this.pages = r.totalPages; this.stats = r.stats;},
      error: e => {this.rows = []; this.total = 0; this.pages = 0; this.stats = null; this.error = this.api.message(e);},
    });
  }
  loadToday(): void {
    if (!this.context?.mine || this.tab !== 'mine' || this.todayLoading) return;
    this.todayLoading = true; const version = ++this.todayVersion;
    this.api.records({mine:true,from:this.date(-1),to:this.date(),q:'',departmentId:'',verification:'',anomaly:'',page:0}).pipe(takeUntilDestroyed(this.destroyRef),finalize(()=>this.todayLoading=false)).subscribe({
      next: r => {if (version === this.todayVersion) this.todayRows = r.items.filter(row => row.workDate === this.date() || row.canCheckOut || row.actualStart && !row.actualEnd);}, error: e => this.error = this.api.message(e),
    });
  }
  reset(): void { if(this.busy)return;this.from=this.date(-30);this.to=this.date();this.q='';this.departmentId='';this.verification='';this.anomaly='';this.search(); }
  goPage(page: number): void { if (!this.busy && page >= 0 && page < this.pages) this.loadRecords(page); }
  pagination(total: number, current: number): Array<number | '...'> {
    if (total <= 0) return [];
    if (total <= 4) return Array.from({length: total}, (_, i) => i);
    const indexes = [...new Set([0, current - 1, current, current + 1, total - 1])]
      .filter(i => i >= 0 && i < total).sort((a, b) => a - b);
    const result: Array<number | '...'> = [];
    for (const index of indexes) {
      const previous = result[result.length - 1];
      if (typeof previous === 'number' && index - previous > 1) result.push('...');
      result.push(index);
    }
    return result;
  }
  closeDetail(): void { if(this.saving)return;this.detail=null;this.mode='view';this.actionError='';this.detailLoading=false;++this.detailVersion; }
  view(row: AttendanceRow, mode: 'view' | 'manual' | 'edit' | 'proposal' = 'view'): void {
    if (this.saving) return;
    const version = ++this.detailVersion; this.detailLoading = true; this.detail = null; this.actionError = ''; this.mode = 'view';
    this.api.detail(row.scheduleId, this.tab === 'mine').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: d => {if(version!==this.detailVersion)return;this.detailLoading=false;this.detail=d;if(mode!=='view')this.openForm(mode);},error:e=>{if(version===this.detailVersion){this.detailLoading=false;this.actionError=this.api.message(e);}},
    });
  }
  openForm(mode: 'manual' | 'edit' | 'proposal' | 'unconfirm'): void {
    const row = this.detail?.row; if(!row || this.saving)return;
    if((mode==='manual'||mode==='edit')&&!row.canEdit || mode==='proposal'&&!row.canRequest || mode==='unconfirm'&&!row.canUnconfirm)return;
    this.mode=mode;this.actualStart=row.actualStart ?? '';this.actualEnd=row.actualEnd ?? '';this.breakMinutes=row.actualBreakMinutes;this.result=row.attendanceResult??'DI_LAM';this.reason='';this.actionError='';
  }
  resultChanged(): void { if(this.result==='VANG'){this.actualStart='';this.actualEnd='';this.breakMinutes=0;} }
  get missingCheckoutProposal(): boolean { return this.mode==='proposal'&&this.detail?.row.verificationStatus!=='DA_XAC_NHAN'; }
  private formData(): Correction | null {
    if(!this.reason.trim()||this.reason.length>1000){this.actionError='Nhập lý do, tối đa 1000 ký tự.';return null;}
    if(!Number.isSafeInteger(this.breakMinutes)||this.breakMinutes<0){this.actionError='Phút nghỉ phải là số nguyên không âm.';return null;}
    const start=this.actualStart || null,end=this.actualEnd || null;
    if(this.result==='DI_LAM'&&(!start || end&&end<=start || !end&&this.breakMinutes!==0 || this.mode==='proposal'&&!end)){this.actionError='Giờ vào/ra không hợp lệ. Khi chưa có giờ ra, phút nghỉ phải bằng 0.';return null;}
    return {actualStart:this.result==='VANG'?null:start,actualEnd:this.result==='VANG'?null:end,actualBreakMinutes:this.result==='VANG'?0:this.breakMinutes,attendanceResult:this.result,correctionReason:this.reason.trim()};
  }
  save(): void {
    if(this.saving||!this.detail)return;const row=this.detail.row;
    if(this.mode==='unconfirm'){if(!this.reason.trim()||this.reason.length>1000){this.actionError='Lý do bắt buộc, tối đa 1000 ký tự.';return;}if(window.confirm('Hủy xác nhận bản ghi công này?'))this.mutate(this.api.verify(row.attendanceId!,false,this.reason.trim(),row.version),`Đã hủy xác nhận công ${this.employeeName(row)}.`,row);return;}
    const data=this.formData();if(!data)return;
    if(!window.confirm(this.mode==='proposal'?'Gửi yêu cầu điều chỉnh? Công gốc chỉ thay đổi sau khi được duyệt.':this.mode==='manual'?'Nhập bản ghi công chưa xác nhận cho lịch này?':'Lưu thay đổi công chưa xác nhận?'))return;
    if(this.mode==='proposal')this.mutate(this.api.submitRequest(row,data),'Đã gửi yêu cầu điều chỉnh.',row);
    else if(this.mode==='manual')this.mutate(this.api.manual(row,data),'Đã nhập công, chờ xác nhận.',row);
    else if(this.mode==='edit')this.mutate(this.api.correct(row.attendanceId!,data,row.version),`Đã sửa công ${this.employeeName(row)}.`,row);
  }
  confirm(row: AttendanceRow): void { if(this.saving||!row.canConfirm)return;if(window.confirm(`Xác nhận công của ${row.employeeLabel}, ngày ${row.workDate}?`))this.mutate(this.api.verify(row.attendanceId!,true,'',row.version),`Đã xác nhận công ${this.employeeName(row)}.`,row); }
  clock(row: AttendanceRow, checkIn: boolean): void { if(this.saving||!(checkIn?row.canCheckIn:row.canCheckOut))return;if(window.confirm(`${checkIn?'Check-in':'Check-out'} ca ${row.shift}? Thời gian được ghi nhận từ máy chủ.`))this.mutate(this.api.clock(row.scheduleId,checkIn),`Đã ${checkIn?'check-in':'check-out'}.`,row); }
  private mutate(request: Observable<unknown>, success: string, row?: AttendanceRow, requestId?: number): void {
    this.saving=true;this.actionError='';this.error='';
    request.pipe(takeUntilDestroyed(this.destroyRef),finalize(()=>this.saving=false)).subscribe({next:()=>{
      this.saving=false;this.notify(success);this.mode='view';
      if(this.tab==='requests'){this.loadRequests(false);if(requestId)this.viewRequestId(requestId);}else{this.loadRecords();if(this.tab==='mine')this.loadToday();if(row)this.view(row);}
    },error:e=>this.actionError=this.api.message(e)});
  }
  loadRequests(reset = true): void {
    if(this.saving||this.requestLoading)return;if(!this.validRange(this.requestFrom,this.requestTo)){this.error='Chọn khoảng ngày hợp lệ, tối đa 366 ngày.';return;}
    if(reset)this.requestPage=0;this.requestLoading=true;this.error='';
    this.api.requests(this.requestsMine,this.requestFrom,this.requestTo,this.requestType,this.requestStatus,this.requestPage).pipe(takeUntilDestroyed(this.destroyRef),finalize(()=>this.requestLoading=false)).subscribe({next:r=>{this.requestRows=r.items;this.requestTotal=r.totalElements;this.requestPages=r.totalPages;this.requestPage=r.page;},error:e=>{this.requestRows=[];this.requestTotal=0;this.requestPages=0;this.error=this.api.message(e);}});
  }
  requestScopeChanged(): void {this.requestDetail=null;++this.requestVersion;this.loadRequests();}
  requestGoPage(page:number): void {if(!this.busy&&page>=0&&page<this.requestPages){this.requestPage=page;this.loadRequests(false);}}
  viewRequest(row: AttendanceRequest): void {this.viewRequestId(row.requestId);}
  private viewRequestId(id:number): void {
    if(this.saving)return;const version=++this.requestVersion;this.requestDetailLoading=true;this.actionError='';this.requestDetail=null;
    this.api.request(id,this.requestsMine).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next:d=>{if(version!==this.requestVersion)return;this.requestDetail=d;this.requestDetailLoading=false;this.acceptedEnd=d.request.proposedEnd??'';this.decisionReason='';},error:e=>{if(version===this.requestVersion){this.requestDetailLoading=false;this.actionError=this.api.message(e);}}});
  }
  receive(): void {const r=this.requestDetail?.request;if(!r?.canReceive||this.saving)return;if(window.confirm('Tiếp nhận yêu cầu này để xử lý?'))this.mutate(this.api.receive(r.requestId),'Đã tiếp nhận yêu cầu.',undefined,r.requestId);}
  decide(approve:boolean): void {
    const r=this.requestDetail?.request;if(!r?.canDecide||this.saving)return;
    const end=this.acceptedEnd||null;
    if((!approve||end!==r.proposedEnd)&&!this.decisionReason.trim()||this.decisionReason.length>1000){this.actionError='Từ chối hoặc duyệt giờ ra khác đề xuất phải nhập lý do, tối đa 1000 ký tự.';return;}
    if(window.confirm(approve?'Chấp nhận và áp dụng điều chỉnh công?':'Từ chối yêu cầu? Công gốc sẽ được giữ nguyên.'))this.mutate(this.api.decide(r.requestId,approve,end,this.decisionReason.trim()),approve?'Đã chấp nhận và cập nhật công.':'Đã từ chối yêu cầu.',undefined,r.requestId);
  }
}
