import {SearchAssistDirective} from '../../shared/search-assist.directive';
import {ActionDialogService} from '../../shared/ui/action-dialog';
import {EditFormDirective} from '../../shared/edit-form.directive';
import {HrmNotice} from '../../shared/ui/notice';
import {Component,DestroyRef,EventEmitter,Input,OnChanges,Output,inject} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {forkJoin,Observable,Subscription,finalize} from 'rxjs';
import {TranslatePipe} from '../../shared/translate.pipe';
import {LanguageService} from '../../services/language.service';
import {errorMessage} from '../../services/employee.service';
import {HrScope,HrScopeService,ScopeDepartment,scopeInstant,scopeLocalInput,scopeUtcInput} from '../../services/hr-scope.service';
@Component({selector:'app-hr-scopes',standalone:true,imports:[SearchAssistDirective,EditFormDirective,HrmNotice,FormsModule,TranslatePipe],templateUrl:'./hr-scopes.html',styleUrl:'./hr-scopes.css'})
export class HrScopes implements OnChanges {
 private actionDialogInPage=inject(ActionDialogService);

 @Input({required:true}) accountId!:number;@Input() active=true;@Output() busyChange=new EventEmitter<boolean>();
 private api=inject(HrScopeService);private destroy=inject(DestroyRef);readonly language=inject(LanguageService);private pending?:Subscription;
 rows:HrScope[]=[];departments:ScopeDepartment[]=[];loading=false;busy=false;error='';message='';attempted=false;
 editorOpen=false;
 mode:'grant'|'update'|'revoke'='grant';selected:HrScope|null=null;departmentId:number|null=null;from='';to='';reason='';
 ngOnChanges(){this.pending?.unsubscribe();this.reset();this.load();}
 load(){if(this.busy)return;this.pending?.unsubscribe();this.loading=true;this.error='';this.pending=forkJoin({scopes:this.api.all(),departments:this.api.departments()}).pipe(takeUntilDestroyed(this.destroy),finalize(()=>this.loading=false)).subscribe({next:r=>{this.rows=r.scopes.filter(s=>s.hrAccountId===this.accountId);this.departments=r.departments;},error:e=>this.error=errorMessage(e)});}
 reset(){if(this.busy)return;this.editorOpen=false;this.mode='grant';this.selected=null;this.departmentId=null;this.from=scopeLocalInput(new Date().toISOString());this.to='';this.reason='';this.attempted=false;this.error='';}
 get fromLocked(){return !!this.selected&&scopeInstant(this.selected.effectiveFrom).getTime()<=Date.now();}
 state(row:HrScope){const now=Date.now();return row.revokedAt?'Đã thu hồi':row.effectiveTo&&scopeInstant(row.effectiveTo).getTime()<=now?'Đã hết hiệu lực':scopeInstant(row.effectiveFrom).getTime()>now?'Chưa đến hiệu lực':'Đang hiệu lực';}
 editable(row:HrScope){return !row.revokedAt&&(!row.effectiveTo||scopeInstant(row.effectiveTo).getTime()>Date.now());}
 department(id:number){const d=this.departments.find(x=>x.departmentId===id);return d?d.departmentCode+' — '+d.departmentName:String(id);}
 date(value:string|null){return value?new Intl.DateTimeFormat(this.language.current()==='vi'?'vi-VN':'en-GB',{day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit',hour12:false}).format(scopeInstant(value)):'—';}
 startGrant(){this.reset();this.editorOpen=true;}
 start(row:HrScope,mode:'update'|'revoke'){if(this.busy)return;this.editorOpen=true;this.selected=row;this.mode=mode;this.departmentId=row.departmentId;this.from=scopeLocalInput(row.effectiveFrom);this.to=row.effectiveTo?scopeLocalInput(row.effectiveTo):'';this.reason='';this.attempted=false;this.error='';this.message='';}
 async save(){if(this.busy||this.loading)return;this.attempted=true;this.error='';this.message='';if(this.mode==='update'&&this.selected&&this.from===scopeLocalInput(this.selected.effectiveFrom)&&this.to===(this.selected.effectiveTo?scopeLocalInput(this.selected.effectiveTo):'')){this.error='Chưa có thay đổi. Hãy cập nhật thông tin trước khi lưu.';return;}if(!this.reason.trim()||this.reason.trim().length>1000){this.error='Nhập lý do hợp lệ, tối đa 1000 ký tự.';return;}
 let request:Observable<HrScope>;
 if(this.mode==='revoke'&&this.selected){if(!(await this.actionDialogInPage.confirm(this.language.t('Thu hồi phạm vi này? HR sẽ mất quyền truy cập phòng ban tương ứng.'))))return;request=this.api.revoke(this.selected.scopeId,this.reason.trim());}
 else {
  let from:string,to:string|null;try{from=this.fromLocked?this.selected!.effectiveFrom:scopeUtcInput(this.from);to=this.to?scopeUtcInput(this.to):null;}catch{this.error='Chọn thời gian hiệu lực hợp lệ.';return;}
  if(to&&scopeInstant(to).getTime()<=scopeInstant(from).getTime()){this.error='Thời điểm kết thúc phải sau thời điểm bắt đầu.';return;}
  const body={effectiveFrom:from,effectiveTo:to,reason:this.reason.trim()};
  if(this.mode==='update'&&this.selected)request=this.api.update(this.selected.scopeId,body);
  else {if(!this.active||!this.departments.some(d=>d.departmentId===this.departmentId&&d.isActive)){this.error='Chọn phòng ban đang hoạt động.';return;}request=this.api.grant(this.accountId,this.departmentId!,body);}
 }
 this.busy=true;this.busyChange.emit(true);request.pipe(takeUntilDestroyed(this.destroy),finalize(()=>{this.busy=false;this.busyChange.emit(false);})).subscribe({next:()=>{this.busy=false;this.busyChange.emit(false);this.reset();this.message='Đã lưu phạm vi phụ trách và lịch sử.';this.load();},error:e=>this.error=errorMessage(e)});
 }
}
