import { clockTiming } from './clock-timing';
import {AttendanceActualBreaksComponent} from './attendance-actual-breaks';
import {Component,DestroyRef,Input,OnInit,inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {HttpClient,HttpParams} from '@angular/common/http';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Subscription,firstValueFrom} from 'rxjs';
import {OvertimeCalendarComponent} from './overtime-calendar';
import {DetailFocusDirective} from './detail-focus.directive';
import {OtSupplementPanelComponent} from './ot-supplement-panel';
import {AttendanceApiService,AttendanceRow,AttendanceDetail,Page} from '../pages/attendance/attendance-api.service';
import {API_BASE_URL} from '../services/api.config';
interface Entry {key:string;start:string;end:string;regular?:AttendanceRow;ot?:OvertimeCalendarComponent['items'][number];}
@Component({selector:'app-personal-calendar',standalone:true,imports:[AttendanceActualBreaksComponent,CommonModule,FormsModule,DetailFocusDirective,OtSupplementPanelComponent],templateUrl:'./personal-calendar.html',styleUrl:'./personal-calendar.css'})
export class PersonalCalendarComponent extends OvertimeCalendarComponent implements OnInit {
    readonly clockTiming = clockTiming;
 @Input() attendance=false;
 private readonly client=inject(HttpClient);private readonly lifecycle=inject(DestroyRef);private readonly attendanceApi=inject(AttendanceApiService);private requestVersion=0;
 private selectionVersion=0;private selectedDetailKey='';private regularSubscription?:Subscription;
 constructor(){super();this.lifecycle.onDestroy(()=>this.cancelRegularDetail());}
 private cancelRegularDetail(){++this.selectionVersion;this.selectedDetailKey='';this.regularSubscription?.unsubscribe();this.regularSubscription=undefined;}
 closeRegularDetail(){if(this.saving)return;this.cancelRegularDetail();this.regularDetail=null;this.proposal=false;}
 override closeView(){this.cancelRegularDetail();super.closeView();}
 override view(slot:OvertimeCalendarComponent['items'][number]){this.closeRegularDetail();super.view(slot);}
 override checkout(slot:OvertimeCalendarComponent['items'][number]){this.closeRegularDetail();super.closeView();super.checkout(slot);}
 anchor=this.localDate();weekly=true;kind='all';entries:Entry[]=[];days:{date:string;entries:Entry[]}[]=[];regularDetail:AttendanceDetail|null=null;proposal=false;proposalStart='';proposalEnd='';proposalBreak=0;proposalReason='';proposalResult='DI_LAM';
 ngOnInit(){this.mine=true;this.load();}
 override ngOnChanges(){} // The personal calendar owns its day/week range.
 localDate(d=new Date()){return new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(d);}
 add(date:string,n:number){const d=new Date(date+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+n);return d.toISOString().slice(0,10);}
 get first(){const d=new Date(this.anchor+'T12:00:00Z');return this.weekly?this.add(this.anchor,-((d.getUTCDay()+6)%7)):this.anchor;}
 get last(){return this.add(this.first,this.weekly?6:0);}
 get week(){const d=new Date(this.first+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+3);const year=new Date(Date.UTC(d.getUTCFullYear(),0,4,12));return 1+Math.round((d.getTime()-year.getTime())/604800000);}
 move(n:number){if(this.saving)return;this.anchor=this.add(this.anchor,n*(this.weekly?7:1));this.load();}
 setMode(weekly:boolean){if(this.saving)return;this.weekly=weekly;this.load();}
 setKind(kind:string){if(this.saving)return;this.kind=kind;this.load();}
 today(){if(!this.saving){this.anchor=this.localDate();this.load();}}
 private async all<T>(url:string,from:string,to:string):Promise<T[]>{let page=0,total=1;const rows:T[]=[];do{const params=new HttpParams().set('mine',true).set('from',from).set('to',to).set('page',page);const r=await firstValueFrom(this.client.get<Page<T>>(API_BASE_URL+url,{params}).pipe(takeUntilDestroyed(this.lifecycle)));rows.push(...r.items);total=r.totalPages;page++;}while(page<total);return rows;}
 override load(_page=0){if(this.saving)return;const version=++this.requestVersion;this.loading=true;this.error='';this.entries=[];this.days=[];this.closeView();this.selected=null;this.regularDetail=null;this.proposal=false;const first=this.first,last=this.last,kind=this.kind;
  Promise.all([kind==='ot'?Promise.resolve([]):this.all<AttendanceRow>('/attendance-workspace/records',this.add(first,-1),last),kind==='regular'?Promise.resolve([]):this.all<OvertimeCalendarComponent['items'][number]>('/overtime/workspace/calendar',this.add(first,-1),last)]).then(([regular,ot])=>{if(version!==this.requestVersion)return;this.entries=[...regular.map(r=>({key:'r'+r.scheduleId,start:r.plannedStart,end:r.plannedEnd,regular:r})),...ot.map(o=>({key:'o'+o.row.ot.overtimeId,start:o.row.ot.requestedStart,end:o.row.ot.requestedEnd,ot:o}))].sort((a,b)=>a.start.localeCompare(b.start));for(let date=first;date<=last;date=this.add(date,1))this.days.push({date,entries:this.entries.filter(e=>e.start<this.add(date,1)+'T00:00:00'&&e.end>date+'T00:00:00')});this.loading=false;}).catch(e=>{if(version===this.requestVersion){this.loading=false;this.error=this.attendanceApi.message(e);}});
 }
 title(e:Entry){return e.regular?.shift||e.ot?.row.ot.requestCode||'Ca làm việc';}
 state(r:AttendanceRow){return r.verificationStatus==='DA_XAC_NHAN'?'Đã xác nhận':r.attendanceId?'Chờ xác nhận':'Chưa có công';}
 open(e:Entry){if(this.saving)return;this.selected=null;this.supplementId=null;if(e.ot){this.view(e.ot);return;}if(!e.regular)return;this.closeView();this.regularDetail=null;this.proposal=false;const scope=this.requestVersion,version=++this.selectionVersion,key='r'+e.regular.scheduleId;this.selectedDetailKey=key;this.regularSubscription=this.attendanceApi.detail(e.regular.scheduleId,true).pipe(takeUntilDestroyed(this.lifecycle)).subscribe({next:d=>{if(scope===this.requestVersion&&version===this.selectionVersion&&this.selectedDetailKey===key&&d.row.scheduleId===e.regular!.scheduleId)this.regularDetail=d;},error:err=>{if(scope===this.requestVersion&&version===this.selectionVersion&&this.selectedDetailKey===key)this.error=this.attendanceApi.message(err);}});}
 regularClock(r:AttendanceRow,checkIn:boolean){if(this.saving||!(checkIn?r.canCheckIn:r.canCheckOut)||!window.confirm((checkIn?'Check-in':'Check-out')+' ca '+r.shift+'? Giờ ghi nhận lấy từ máy chủ.'))return;this.saving=true;this.attendanceApi.clock(r.scheduleId,checkIn).pipe(takeUntilDestroyed(this.lifecycle)).subscribe({next:()=>{this.saving=false;this.message='Đã '+(checkIn?'check-in.':'check-out.');this.load();},error:e=>{this.saving=false;this.error=this.attendanceApi.message(e);}});}
 startProposal(){const r=this.regularDetail?.row;if(!r?.canRequest)return;this.proposal=true;this.proposalStart=r.actualStart||'';this.proposalEnd=r.actualEnd||'';this.proposalBreak=r.actualBreakMinutes;this.proposalResult=r.attendanceResult||'DI_LAM';this.proposalReason='';}
 sendProposal(){const r=this.regularDetail?.row;if(!r?.canRequest||this.saving)return;if((this.proposalResult!=='VANG'&&(!this.proposalStart||!this.proposalEnd||this.proposalEnd<=this.proposalStart))||!Number.isInteger(this.proposalBreak)||this.proposalBreak<0||!this.proposalReason.trim()){this.error='Nhập giờ ra sau giờ vào, phút nghỉ hợp lệ và lý do.';return;}if(!window.confirm('Gửi yêu cầu điều chỉnh công cá nhân để xét duyệt?'))return;this.saving=true;this.attendanceApi.submitRequest(r,{actualStart:this.proposalResult==='VANG'?null:this.proposalStart,actualEnd:this.proposalResult==='VANG'?null:this.proposalEnd,actualBreakMinutes:this.proposalResult==='VANG'?0:this.proposalBreak,attendanceResult:this.proposalResult,correctionReason:this.proposalReason.trim()}).pipe(takeUntilDestroyed(this.lifecycle)).subscribe({next:()=>{this.saving=false;this.message='Đã gửi yêu cầu điều chỉnh công.';this.load();},error:e=>{this.saving=false;this.error=this.attendanceApi.message(e);}});}
}
