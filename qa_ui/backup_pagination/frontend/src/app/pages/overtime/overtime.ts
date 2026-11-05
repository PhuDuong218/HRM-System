import { clockTiming } from '../../shared/clock-timing';
import {DetailFocusDirective} from '../../shared/detail-focus.directive';
import {OtSupplementPanelComponent} from '../../shared/ot-supplement-panel';
import {RowViewDirective} from '../../shared/row-view.directive';
import { CommonModule } from '@angular/common';
import { Component, DestroyRef, ElementRef, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, finalize } from 'rxjs';
import { TranslatePipe } from '../../shared/translate.pipe';
import { OvertimeApiService, OtTab, OtContext, OtRow, OtStats, OtEmployee, OtDetail, OtFilter, OtBreak, OtWorkflow, OtRegistration, OtActual, OtWindow } from './overtime-api.service';

@Component({selector:'app-overtime',standalone:true,imports: [DetailFocusDirective,OtSupplementPanelComponent,RowViewDirective,CommonModule,FormsModule,TranslatePipe],templateUrl:'./overtime.html',styleUrls:['../work-module.css','./overtime.css']})
export class OvertimePage implements OnInit {
    readonly clockTiming = clockTiming;
  private readonly api=inject(OvertimeApiService);private readonly destroyRef=inject(DestroyRef);
  private readonly host=inject(ElementRef) as ElementRef<HTMLElement>;
  private alignSelected(key:string){
    requestAnimationFrame(()=>{
      const row=this.host.nativeElement.querySelector<HTMLElement>('[data-ot-key="'+key+'"]');
      row?.scrollIntoView({block:'start',behavior:'instant'});
    });
  }
  context:OtContext|null=null;tab:OtTab='employees';loading=false;saving=false;detailLoading=false;employeeLoading=false;
  private actionMessage='';private actionTimer:ReturnType<typeof setTimeout>|undefined;
  invalidField='';
  get actionError(){return this.actionMessage;}
  set actionError(value:string){
    if(this.actionTimer!==undefined)clearTimeout(this.actionTimer);
    this.actionMessage=value;
    if(!value)this.invalidField='';
    this.actionTimer=value?setTimeout(()=>{this.actionMessage='';this.actionTimer=undefined;},5000):undefined;
  }
  constructor(){this.destroyRef.onDestroy(()=>{if(this.actionTimer!==undefined)clearTimeout(this.actionTimer);if(this.feedbackTimer!==undefined)clearTimeout(this.feedbackTimer);});}
  private invalid(message:string,id:string):false {
    this.actionError=message;this.invalidField=id;
    const field=this.host.nativeElement.querySelector<HTMLElement>('#'+id);
    field?.focus();field?.scrollIntoView({block:'center',behavior:'smooth'});
    return false;
  }
  clearFieldError(){this.invalidField='';}
  private feedbackMessage='';private feedbackTimer:ReturnType<typeof setTimeout>|undefined;
  get feedback(){return this.feedbackMessage;}
  set feedback(value:string){
    if(this.feedbackTimer!==undefined)clearTimeout(this.feedbackTimer);
    this.feedbackMessage=value;
    this.feedbackTimer=value?setTimeout(()=>{this.feedbackMessage='';this.feedbackTimer=undefined;},10000):undefined;
  }
  error='';stats:OtStats|null=null;
  from=this.date(-30);to=this.date(30);q='';departmentId='';status='';actual='';registration='';
  departments:Array<{departmentId:number;departmentName:string}>=[];
  rows:OtRow[]=[];employees:OtEmployee[]=[];page=0;pages=0;total=0;
  detail:OtDetail|null=null;selectedEmployee:OtEmployee|null=null;employeeRows:OtRow[]=[];employeePage=0;employeePages=0;
  mode:'view'|'create'|'edit'|'actual'|'amend'|'reject'|'cancel'|'cancelRequest'|'overflow'|'reopen'='view';
  start='';end='';reason='';workContent='';breaks:OtBreak[]=[];windows:OtWindow[]=[];
  workflows:OtWorkflow[]=[];selectedWorkflow:OtWorkflow|null=null;inboxMine=false;workflowType='';workflowStatus='CHO_DUYET';
  decisionReason='';accepted:Array<{intervalId:number;approvedStart:string;approvedEnd:string;selected:boolean}>=[];
  private applied:OtFilter|null=null;private appliedInbox:{mine:boolean;from:string;to:string;type:string;status:string}|null=null;
  private detailVersion=0;private employeeVersion=0;
  get busy(){return this.loading||this.saving;}
  get panelOpen(){return this.detailLoading||this.detail!==null||this.mode==='create'||this.selectedEmployee!==null||this.selectedWorkflow!==null;}
  ngOnInit(){this.loading=true;this.api.context().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next:c=>{this.context=c;this.tab=c.manage?'employees':'mine';this.inboxMine=!c.manage;this.loading=false;this.search();if(c.manage)this.api.departments().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next:d=>this.departments=d,error:e=>this.error=this.api.message(e)});},error:e=>{this.loading=false;this.error=this.api.message(e);}});}
  date(offset=0){const d=new Date();d.setDate(d.getDate()+offset);return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;}
  time(value:string|null|undefined){return value?value.replace('T',' ').slice(0,16):'—';}
  utc(value:string|null|undefined){return value?new Intl.DateTimeFormat('vi-VN',{dateStyle:'short',timeStyle:'short',timeZone:'Asia/Ho_Chi_Minh'}).format(new Date(value.endsWith('Z')?value:value+'Z')):'—';}
  hours(minutes:number|null|undefined){return minutes==null?'Chưa xác minh':`${Math.floor(minutes/60)} giờ ${minutes%60} phút`;}
  label(value:string|null|undefined){return ({CHO_DUYET:'Chờ duyệt',DA_DUYET:'Đã duyệt',TU_CHOI:'Từ chối',DA_HUY:'Đã hủy',HUY_OT:'Hủy OT đã duyệt',OT_VUOT:'Xem xét OT vượt',HR:'Nhân sự (HR)',EMPLOYEE:'Nhân viên',OT_SELF_CHECK_IN:'Tự check-in OT',OT_SELF_CHECK_OUT:'Tự check-out OT',OT_SUPPLEMENT_SUBMIT:'Gửi yêu cầu bổ sung công OT',OT_SUPPLEMENT_APPROVE:'Duyệt bổ sung công OT',OT_SUPPLEMENT_REJECT:'Từ chối bổ sung công OT',OT_REGISTER:'Gửi đơn',OT_EDIT:'Sửa đơn',OT_APPROVE:'Duyệt đơn',OT_REJECT:'Từ chối đơn',OT_ACTUAL:'Ghi nhận thực tế',OT_ACTUAL_AMEND:'Điều chỉnh OT sau mở lại',OT_CONFIRM:'Xác nhận thực tế',OT_CANCEL:'Hủy đơn chờ',OT_REOPEN_CONFIRMATION:'Mở lại xác nhận',OT_REQUEST_APPROVE:'Duyệt yêu cầu',OT_REQUEST_REJECT:'Từ chối yêu cầu',OT_HUY_OT_SUBMIT:'Gửi yêu cầu hủy',OT_OT_VUOT_SUBMIT:'Gửi yêu cầu phần vượt'}as Record<string,string>)[value??'']??value??'—';}
  employeeNotes(e:OtEmployee):string {
    const notes:string[]=[];
    if(e.unresolved)notes.push(e.unresolved+' đơn cần đối soát dữ liệu giờ/nghỉ.');
    if(e.pending)notes.push(e.pending+' đơn chờ duyệt.');
    const actualPending=e.needsAction-e.pending;
    if(actualPending>0)notes.push(actualPending+' đơn chờ ghi nhận/xác nhận thực tế.');
    return notes.join('\n')||'—';
  }
  requestNotes(r:OtRow):string {
    const notes:string[]=[];
    if(r.plannedBreakStatus!=='VERIFIED')notes.push('Chưa xác minh chi tiết nghỉ dự kiến.');
    if(r.warning)notes.push(r.warning);
    if(r.overflowMinutes)notes.push('Phần thực tế chưa được công nhận: '+this.hours(r.overflowMinutes)+'.');
    if(r.ot.rejectionReason)notes.push('Lý do từ chối: '+r.ot.rejectionReason);
    return notes.join('\n')||'—';
  }
  actualLabel(r:OtRow){return r.ot.confirmedBy?'Đã xác nhận':r.ot.actualStart?(r.ot.actualEnd?'Cần xác nhận':'Đã check-in'):r.ot.status==='DA_DUYET'?'Cần ghi nhận':'Chưa ghi nhận';}
  retroactive(r:OtRow){return Date.parse(r.submittedAt.endsWith('Z')?r.submittedAt:r.submittedAt+'Z')>Date.parse(r.ot.requestedStart+'+07:00');}
  switchTab(tab:OtTab){if(this.busy||!this.context||tab==='mine'&&!this.context.mine||['employees','approval','actual'].includes(tab)&&!this.context.manage)return;this.tab=tab;this.closePanel();this.error='';this.stats=null;this.status=tab==='approval'?'CHO_DUYET':tab==='actual'?'DA_DUYET':'';this.actual=tab==='actual'?'UNFINISHED':'';if(tab==='other')this.searchInbox();else this.search();}
  private range(){if(!this.from||!this.to||this.from>this.to||(Date.parse(this.to)-Date.parse(this.from))/86400000>366){this.error='Khoảng ngày phải hợp lệ và không quá 366 ngày.';return false;}return true;}
  search(){if(this.busy||!this.context||!this.range())return;if(/^-\d+(\.\d+)?$/.test(this.q.trim())){this.error='Mã hoặc tên nhân viên không hợp lệ.';return;}this.applied={mine:this.tab==='mine',from:this.from,to:this.to,q:this.tab==='mine'?'':this.q.trim(),departmentId:this.tab==='mine'?'':this.departmentId,status:this.status,actual:this.actual,registration:this.registration,page:0};this.closePanel();this.load(0);}
  load(page=this.page){if(!this.applied||this.saving||this.loading)return;this.loading=true;this.error='';const done=()=>this.loading=false;
    if(this.tab==='employees')this.api.employees({...this.applied,page}).pipe(takeUntilDestroyed(this.destroyRef),finalize(done)).subscribe({next:r=>{this.employees=r.items;if(this.selectedEmployee)this.selectedEmployee=r.items.find(e=>e.employeeId===this.selectedEmployee!.employeeId)??this.selectedEmployee;this.page=r.page;this.pages=r.totalPages;this.total=r.totalElements;this.stats=r.stats??null;},error:e=>{this.employees=[];this.stats=null;this.error=this.api.message(e);}});
    else this.api.search({...this.applied,page}).pipe(takeUntilDestroyed(this.destroyRef),finalize(done)).subscribe({next:r=>{this.rows=r.items;this.page=r.page;this.pages=r.totalPages;this.total=r.totalElements;this.stats=r.stats??null;},error:e=>{this.rows=[];this.stats=null;this.error=this.api.message(e);}});
  }
  reset(){if(this.busy)return;this.from=this.date(-30);this.to=this.date(30);this.q='';this.departmentId='';this.status=this.tab==='approval'?'CHO_DUYET':this.tab==='actual'?'DA_DUYET':'';this.actual=this.tab==='actual'?'UNFINISHED':'';this.registration='';this.search();}
  pagination(total:number,current:number):Array<number|'...'>{if(total<=4)return Array.from({length:Math.max(0,total)},(_,i)=>i);const result:Array<number|'...'>=[];for(const p of [...new Set([0,current-1,current,current+1,total-1])].filter(p=>p>=0&&p<total).sort((a,b)=>a-b)){const prev=result[result.length-1];if(typeof prev==='number'&&p-prev>1)result.push('...');result.push(p);}return result;}
  goPage(page:number){if(!this.busy&&page>=0&&page<this.pages){if(this.tab==='other')this.loadInbox(page);else this.load(page);}}
  employeeDetail(employee:OtEmployee,page=0){if(this.saving||!this.applied)return;this.selectedEmployee=employee;this.detail=null;this.selectedWorkflow=null;this.mode='view';++this.detailVersion;this.detailLoading=false;const version=++this.employeeVersion;this.employeeLoading=true;this.alignSelected('employee-'+employee.employeeId);
    this.api.search({...this.applied,employeeId:employee.employeeId,page}).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next:r=>{if(version!==this.employeeVersion)return;this.employeeRows=r.items;this.employeePage=r.page;this.employeePages=r.totalPages;this.employeeLoading=false;},error:e=>{if(version===this.employeeVersion){this.employeeLoading=false;this.actionError=this.api.message(e);}}});}
  employeeGoPage(page:number){if(this.selectedEmployee&&!this.employeeLoading&&page>=0&&page<this.employeePages)this.employeeDetail(this.selectedEmployee,page);}
  view(row:OtRow,mode:typeof this.mode='view'){this.viewId(row.ot.overtimeId,mode);}
  viewId(id:number,mode:typeof this.mode='view'){if(this.saving)return;const version=++this.detailVersion;this.detailLoading=true;this.detail=null;this.mode='view';this.actionError='';this.alignSelected(this.selectedWorkflow?'workflow-'+this.selectedWorkflow.requestId:this.tab==='employees'&&this.selectedEmployee?'employee-'+this.selectedEmployee.employeeId:'request-'+id);this.api.detail(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next:d=>{if(version!==this.detailVersion)return;this.detail=d;this.detailLoading=false;if(mode!=='view')this.openForm(mode);},error:e=>{if(version===this.detailVersion){this.detailLoading=false;this.actionError=this.api.message(e);}}});}
  closePanel(){if(this.saving)return;++this.detailVersion;++this.employeeVersion;this.detail=null;this.selectedEmployee=null;this.selectedWorkflow=null;this.detailLoading=false;this.employeeLoading=false;this.mode='view';this.actionError='';}
  create(){if(!this.context?.canRegister||this.busy)return;this.closePanel();this.mode='create';this.start='';this.end='';this.reason='';this.workContent='';this.breaks=[];}
  get actualMode(){return this.mode==='actual'||this.mode==='amend';}
  get actualRecorded():boolean{return this.detail?.audit.some(a=>['OT_ACTUAL','OT_ACTUAL_AMEND'].includes(a.action))??false;}
  openForm(mode:typeof this.mode){const r=this.detail?.row;if(!r||this.saving)return;const allowed={view:true,create:false,edit:r.actions.edit,actual:r.actions.recordActual,amend:r.actions.amendActual,reject:r.actions.approve,cancel:r.actions.cancel,cancelRequest:r.actions.cancelRequest,overflow:r.actions.overflowRequest,reopen:r.actions.reopen};if(!allowed[mode])return;this.mode=mode;this.reason=mode==='edit'?r.ot.reason:'';this.workContent=r.workContent??'';this.windows=[];
    this.start=(mode==='actual'||mode==='amend')?r.ot.actualStart??'':r.ot.requestedStart;this.end=(mode==='actual'||mode==='amend')?r.ot.actualEnd??'':r.ot.requestedEnd;this.breaks=((mode==='actual'||mode==='amend')?this.detail!.actualBreaks:this.detail!.plannedBreaks).map(b=>({...b}));this.actionError='';}
  addBreak(){if(!this.saving)this.breaks.push({breakStart:'',breakEnd:''});}removeBreak(index:number){if(!this.saving)this.breaks.splice(index,1);}
  addWindow(){if(!this.saving)this.windows.push({workDate:'',start:'',end:''});}removeWindow(index:number){if(!this.saving)this.windows.splice(index,1);}
  breakTotal():number{return Math.floor(this.breaks.reduce((sum,b)=>{const diff=(Date.parse(b.breakEnd)-Date.parse(b.breakStart))/60000;return sum+(Number.isFinite(diff)&&diff>0?diff:0);},0));}
  private validTimes(){
    const start=Date.parse(this.start),end=Date.parse(this.end);
    if(!Number.isFinite(start))return this.invalid('Vui lòng chọn ngày và giờ bắt đầu.','otStart');
    if(!Number.isFinite(end))return this.invalid('Vui lòng chọn ngày và giờ kết thúc.','otEnd');
    if(end<=start)return this.invalid('Giờ kết thúc phải sau giờ bắt đầu.','otEnd');
    for(const [i,b] of this.breaks.entries()){
      const begin=Date.parse(b.breakStart),finish=Date.parse(b.breakEnd);
      if(!Number.isFinite(begin))return this.invalid('Vui lòng chọn thời điểm bắt đầu khoảng nghỉ '+(i+1)+'.','breakStart'+i);
      if(!Number.isFinite(finish))return this.invalid('Vui lòng chọn thời điểm kết thúc khoảng nghỉ '+(i+1)+'.','breakEnd'+i);
      if(begin<start||begin>=end)return this.invalid('Bắt đầu khoảng nghỉ phải nằm trong thời gian OT.','breakStart'+i);
      if(finish<=begin||finish>end)return this.invalid('Kết thúc khoảng nghỉ phải sau bắt đầu nghỉ và nằm trong OT.','breakEnd'+i);
      for(let j=0;j<i;j++){const other=this.breaks[j];if(begin<Date.parse(other.breakEnd)&&finish>Date.parse(other.breakStart))return this.invalid('Khoảng nghỉ '+(i+1)+' chồng lấn khoảng nghỉ '+(j+1)+'.','breakStart'+i);}
    }return true;
  }
  save(){if(this.saving||this.mode==='create'&&!this.context?.canRegister)return;const r=this.detail?.row;if(['create','edit','actual','amend'].includes(this.mode)&&!this.validTimes())return;
    if(this.workContent.length>2000){this.invalid('Nội dung công việc tối đa 2000 ký tự.','otWork');return;}
    if(!this.reason.trim()||this.reason.length>1000){this.invalid('Lý do bắt buộc, tối đa 1000 ký tự.','otReason');return;}
    if(['create','edit','actual','amend'].includes(this.mode)){
      this.actionError='';
      if(!window.confirm(this.actualMode?'Lưu giờ OT thực tế đã xác minh? Giờ đề nghị sẽ không được tự dùng thay thế.':this.mode==='create'?'Gửi đơn OT để xét duyệt?':'Lưu thay đổi đơn OT chờ duyệt?'))return;
      if(this.actualMode&&r){const data:OtActual={actualStart:this.start,actualEnd:this.end,actualBreakMinutes:this.breakTotal(),breaks:this.breaks.map(b=>({...b})),reason:this.reason.trim()};this.mutate(this.api.action(r,this.mode==='amend'?'amend-actual':'actual',this.reason.trim(),data),'Đã ghi nhận giờ thực tế.',r.ot.overtimeId);}
      else{const body:OtRegistration={requestedStart:this.start,requestedEnd:this.end,reason:this.reason.trim(),workContent:this.workContent.trim(),breaks:this.breaks.map(b=>({...b}))};if(this.mode==='create')this.mutate(this.api.register(body),'Đã gửi đơn OT, chờ duyệt.');else if(r)this.mutate(this.api.edit(r,body),'Đã cập nhật đơn OT.',r.ot.overtimeId);}return;
    }
    if(!r)return;
    if(this.mode==='overflow'&&(!this.windows.length||this.windows.some(w=>!w.workDate||!w.start||!w.end||w.end<=w.start))){this.actionError='Thêm ít nhất một khoảng vượt theo từng ngày thực tế.';return;}
    if(!window.confirm(this.mode==='cancelRequest'?'Gửi yêu cầu hủy? Đơn gốc vẫn đã duyệt trong khi chờ quyết định.':this.mode==='overflow'?'Gửi đề nghị xem xét phần OT vượt?':'Xác nhận thao tác với đơn OT này?'))return;
    if(this.mode==='cancelRequest'||this.mode==='overflow')this.mutate(this.api.submitWorkflow(r,this.mode==='overflow'?'overflow':'cancel',this.reason.trim(),this.windows),'Đã gửi yêu cầu riêng, chưa thay đổi hiệu lực đơn gốc.',r.ot.overtimeId);
    else if(['reject','cancel','reopen'].includes(this.mode))this.mutate(this.api.action(r,this.mode,this.reason.trim()),this.mode==='reject'?'Đã từ chối đơn OT.':this.mode==='cancel'?'Đã hủy đơn chờ duyệt.':'Đã mở lại xác nhận để đối soát.',r.ot.overtimeId);
  }
  approve(){const r=this.detail?.row;if(!r?.actions.approve||this.saving)return;if(window.confirm(`Duyệt OT của ${r.fullName}? Backend sẽ kiểm tra chồng lấn và giới hạn 12 giờ/ngày.`))this.mutate(this.api.action(r,'approve',''),'Đã duyệt đơn OT. Chưa xác nhận giờ thực tế.',r.ot.overtimeId);}
  confirmActual(){const r=this.detail?.row;if(!r?.actions.confirm||this.detail?.reconciliationIssue||this.saving)return;if(window.confirm(`Xác nhận giờ OT thực tế của ${r.fullName}? Chỉ phút hợp lệ được công nhận.`))this.mutate(this.api.action(r,'confirm',''),'Đã xác nhận giờ OT thực tế.',r.ot.overtimeId);}
  private mutate(request:Observable<unknown>,message:string,id?:number){this.saving=true;this.actionError='';this.feedback='';request.pipe(takeUntilDestroyed(this.destroyRef),finalize(()=>this.saving=false)).subscribe({next:()=>{this.saving=false;this.feedback=message;this.mode='view';if(this.tab==='other'){this.loadInbox(this.page);this.selectedWorkflow=null;}else this.load();if(id)this.viewId(id);else this.closePanel();},error:e=>this.actionError=this.api.message(e)});}
  searchInbox(){if(this.busy||!this.range())return;this.appliedInbox={mine:this.inboxMine,from:this.from,to:this.to,type:this.workflowType,status:this.workflowStatus};this.closePanel();this.loadInbox(0);}
  loadInbox(page=this.page){if(this.busy||!this.appliedInbox)return;this.loading=true;this.error='';const f=this.appliedInbox;this.api.inbox(f.mine,f.from,f.to,f.type,f.status,page).pipe(takeUntilDestroyed(this.destroyRef),finalize(()=>this.loading=false)).subscribe({next:r=>{this.workflows=r.items;this.page=r.page;this.pages=r.totalPages;this.total=r.totalElements;},error:e=>{this.workflows=[];this.error=this.api.message(e);}});}
  viewWorkflow(w:OtWorkflow){if(this.saving)return;this.selectedWorkflow=w;this.selectedEmployee=null;this.decisionReason='';this.accepted=w.intervals.map(i=>({intervalId:i.interval_id,approvedStart:i.requested_start,approvedEnd:i.requested_end,selected:true}));this.viewId(w.overtimeId);}
  decide(approve:boolean){const w=this.selectedWorkflow;if(!w?.canDecide||this.saving)return;if(!approve&&!this.decisionReason.trim()){this.actionError='Từ chối bắt buộc lý do.';return;}const accepted=this.accepted.filter(i=>i.selected).map(({selected,...i})=>i);if(approve&&w.type==='OT_VUOT'&&!accepted.length){this.actionError='Chọn ít nhất một khoảng vượt được duyệt.';return;}if(!window.confirm(approve?'Duyệt yêu cầu này theo thẩm quyền?':'Từ chối yêu cầu? Đơn gốc được giữ nguyên.'))return;this.mutate(this.api.decide(w,approve,this.decisionReason.trim(),accepted),approve?'Đã duyệt yêu cầu.':'Đã từ chối yêu cầu.');}
}

