import {SearchAssistDirective} from './search-assist.directive';
import {ActionDialogService} from './ui/action-dialog';
import {HrmNotice} from './ui/notice';
import {DetailFocusDirective} from './detail-focus.directive';
import {OtSupplementPanelComponent} from './ot-supplement-panel';
import {RowViewDirective} from './row-view.directive';
import {CommonModule} from '@angular/common';
import {Component,DestroyRef,Input,OnChanges,inject} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {HttpClient,HttpParams} from '@angular/common/http';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {API_BASE_URL} from '../services/api.config';
import {OtDetail,OtRow,OtBreak,OvertimeApiService} from '../pages/overtime/overtime-api.service';
interface Slot {row:OtRow;canCheckIn:boolean;canCheckOut:boolean;}
@Component({selector:'app-overtime-calendar',standalone:true,imports:[SearchAssistDirective,HrmNotice,DetailFocusDirective,OtSupplementPanelComponent,RowViewDirective,CommonModule,FormsModule],templateUrl:'./overtime-calendar.html'})
export class OvertimeCalendarComponent implements OnChanges {
 protected actionDialogInPage=inject(ActionDialogService);

 @Input() mine=false;@Input() initialFrom='';@Input() initialTo='';@Input() employeeId:number|null=null;@Input() departmentId='';
 private http=inject(HttpClient);private destroy=inject(DestroyRef);private api=inject(OvertimeApiService);private version=0;
 private date(offset:number){const d=new Date();d.setDate(d.getDate()+offset);return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;}
 from=this.date(-7);to=this.date(30);items:Slot[]=[];page=0;pages=0;total=0;loading=false;saving=false;error='';message='';selected:Slot|null=null;rests:OtBreak[]=[];noRest=false;
 supplementId:number|null=null;
 viewDetail:OtDetail|null=null;viewLoading=false;private viewVersion=0;
 view(slot:Slot){if(this.saving)return;const version=++this.viewVersion;this.viewLoading=true;this.viewDetail=null;this.error='';this.api.detail(slot.row.ot.overtimeId).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:d=>{if(version===this.viewVersion){this.viewDetail=d;this.viewLoading=false;}},error:e=>{if(version===this.viewVersion){this.error=this.api.message(e);this.viewLoading=false;}}});}
 closeView(){++this.viewVersion;this.viewDetail=null;this.viewLoading=false;}
 ngOnChanges(){this.from=this.initialFrom||this.date(-7);this.to=this.initialTo||this.date(30);this.load(0);}
 load(page=0){if(this.saving)return;if(!this.from||!this.to||this.from>this.to){this.error='Chọn khoảng ngày OT hợp lệ.';return;}const version=++this.version;this.closeView();this.selected=null;this.error='';this.loading=true;
  let params=new HttpParams().set('mine',this.mine).set('from',this.from).set('to',this.to).set('page',page);if(this.employeeId)params=params.set('employeeId',this.employeeId);if(this.departmentId)params=params.set('departmentId',this.departmentId);
  this.http.get<{items:Slot[];page:number;totalPages:number;totalElements:number}>(`${API_BASE_URL}/overtime/workspace/calendar`,{params}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{if(version!==this.version)return;this.items=r.items;this.page=r.page;this.pages=r.totalPages;this.total=r.totalElements;this.loading=false;},error:e=>{if(version===this.version){this.items=[];this.loading=false;this.error=this.api.message(e);}}});
 }
 checkout(slot:Slot){if(this.saving)return;this.selected=slot;this.rests=[];this.noRest=false;this.error='';this.message='';}
 addRest(){this.noRest=false;this.rests.push({breakStart:'',breakEnd:''});}
 async clock(slot:Slot,checkIn:boolean){if(this.saving||!this.mine)return;if(!checkIn&&(!this.rests.length&&!this.noRest||this.rests.some(b=>!b.breakStart||!b.breakEnd||b.breakEnd<=b.breakStart))){this.error='Nhập khoảng nghỉ thực tế hoặc xác nhận không nghỉ.';return;}
  if(!(await this.actionDialogInPage.confirm(`${checkIn?'Check-in':'Check-out'} OT ${slot.row.ot.requestCode}? Giờ ghi nhận lấy từ máy chủ.`)))return;
  this.saving=true;this.error='';this.message='';this.http.post(`${API_BASE_URL}/overtime/workspace/calendar/${slot.row.ot.overtimeId}/${checkIn?'check-in':'check-out'}`,{expectedVersion:slot.row.version,breaks:checkIn?[]:this.rests,noBreaksConfirmed:!checkIn&&this.noRest}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:()=>{this.saving=false;this.message=checkIn?'Đã check-in OT.':'Đã check-out OT; chờ người có thẩm quyền xác nhận thực tế.';this.selected=null;this.load(this.page);},error:e=>{this.saving=false;this.error=this.api.message(e);}});
 }
}
