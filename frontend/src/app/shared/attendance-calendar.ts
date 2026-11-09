import {lunarLabel, defaultHoliday} from './vietnamese-calendar';
import {SearchAssistDirective} from './search-assist.directive';
import {ActionDialogService} from './ui/action-dialog';
import {HrmNotice} from './ui/notice';
import {CommonModule} from '@angular/common';
import {Component,DestroyRef,Input,OnInit,inject} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {HttpClient,HttpParams} from '@angular/common/http';
import {firstValueFrom,Subscription,timer,switchMap} from 'rxjs';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {AttendanceApiService,AttendanceRow,AttendanceDetail} from '../pages/attendance/attendance-api.service';
import {API_BASE_URL} from '../services/api.config';
import {DetailFocusDirective} from './detail-focus.directive';
import {OtRow} from '../pages/overtime/overtime-api.service';
import {clockTiming} from './clock-timing';

interface Leave {requestId:number;employeeId:number;leaveTypeId:number;leaveStart:string;leaveEnd:string;withdrawnAt:string|null;}
interface LeaveType {leaveTypeId:number;typeCode:string;typeName:string;}
interface OtSlot {row:OtRow;canCheckIn:boolean;canCheckOut:boolean;}
interface Day {date:string;rows:AttendanceRow[];leaves:Leave[];ot:OtSlot[];outside:boolean;}
type State='normal'|'late'|'absent'|'missing'|'maternity'|'leave'|'holiday'|'ot'|'empty';
@Component({selector:'app-attendance-calendar',standalone:true,imports:[SearchAssistDirective,HrmNotice,CommonModule,FormsModule,DetailFocusDirective],templateUrl:'./attendance-calendar.html',styleUrl:'./attendance-calendar.css'})
export class AttendanceCalendarComponent implements OnInit {
 private actionDialogInPage=inject(ActionDialogService);

 @Input() initialMonth='';
 private api=inject(AttendanceApiService);private http=inject(HttpClient);private destroy=inject(DestroyRef);
 employeeOptions:Array<[number,string]>=[];lookupLoading=false;lookupError='';private lookupVersion=0;private lookupSubscription?:Subscription;
 private version=0;private detailVersion=0;private timer?:ReturnType<typeof setTimeout>;
 readonly timing=clockTiming;
 showLunar=false;readonly lunarLabel=lunarLabel;
 holidayName(date:string){return defaultHoliday(date)||(this.holidays.includes(date)?'Ngày nghỉ theo cấu hình':'');}
 isHoliday(date:string){return !!this.holidayName(date);}
 month=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit'}).format(new Date()).slice(0,7);
 anchor='';mode:'month'|'week'='month';mine=false;canMine=false;employee='';q='';suggesting=false;filter='all';
 holidays:string[]=[];calendarWarning='';leaves:Leave[]=[];types:LeaveType[]=[];leaveError='';
 overtime:OtSlot[]=[];otError='';
 rows:AttendanceRow[]=[];days:Day[]=[];
 loading=false;error='';selected:Day|null=null;detail:AttendanceDetail|null=null;detailLoading=false;
 proposal=false;start='';end='';rest=0;reason='';saving=false;notice='';
 readonly weekdays=['T2','T3','T4','T5','T6','T7','CN'];
 readonly filters=[['all','Tất cả'],['normal','Đi làm'],['late','Muộn / về sớm'],['missing','Thiếu chấm công'],['absent','Vắng'],['maternity','Nghỉ thai sản'],['leave','Nghỉ phép'],['holiday','Ngày lễ'],['ot','Tăng ca (OT)']];
 readonly legend=[['normal','Đi làm bình thường'],['late','Muộn / về sớm'],['absent','Nghỉ không phép'],['missing','Chưa / thiếu chấm công'],['maternity','Nghỉ thai sản'],['leave','Nghỉ phép'],['holiday','Ngày lễ'],['ot','Tăng ca (OT)'],['empty','Không có lịch']];
 constructor(){this.destroy.onDestroy(()=>{++this.version;++this.detailVersion;clearTimeout(this.timer);++this.lookupVersion;this.lookupSubscription?.unsubscribe();});}
 ngOnInit(){if(/^\d{4}-\d{2}$/.test(this.initialMonth))this.month=this.initialMonth;this.anchor=this.month+'-01';this.api.context().pipe(takeUntilDestroyed(this.destroy)).subscribe({next:c=>this.canMine=c.mine,error:e=>this.error=this.api.message(e)});this.load();}
 add(d:string,n:number){const x=new Date(d+'T12:00:00Z');x.setUTCDate(x.getUTCDate()+n);return x.toISOString().slice(0,10);}
 get first(){if(this.mode==='month')return this.month+'-01';return this.add(this.anchor,-((new Date(this.anchor+'T12:00:00Z').getUTCDay()+6)%7));}
 get last(){return this.mode==='week'?this.add(this.first,6):new Date(Date.UTC(+this.month.slice(0,4),+this.month.slice(5,7),0,12)).toISOString().slice(0,10);}
 setMonth(value:string){if(!/^\d{4}-(0[1-9]|1[0-2])$/.test(value)||+value.slice(0,4)<1900||+value.slice(0,4)>9998)return;this.month=value;this.anchor=value+'-01';this.load();}
 move(n:number){if(this.saving)return;if(this.mode==='week'){this.anchor=this.add(this.anchor,7*n);this.month=this.anchor.slice(0,7);}else{const d=new Date(this.first+'T12:00:00Z');d.setUTCMonth(d.getUTCMonth()+n);this.month=d.toISOString().slice(0,7);this.anchor=this.month+'-01';}this.load();}
 setMode(mode:'month'|'week'){this.mode=mode;this.load();}
 private async pages<T>(url:string,params:HttpParams,field:'items'|'content'):Promise<T[]>{const out:T[]=[];let page=0,total=1;do{const r=await firstValueFrom(this.http.get<{items:T[];content:T[];totalPages:number}>(API_BASE_URL+url,{params:params.set('page',page)}).pipe(takeUntilDestroyed(this.destroy)));out.push(...r[field]);total=r.totalPages;page++;}while(page<total);return out;}
 async load(){if(this.saving)return;const v=++this.version;++this.detailVersion;this.loading=true;this.error='';this.overtime=[];this.otError='';this.leaveError='';this.leaves=[];this.types=[];this.rows=[];this.selected=null;this.detail=null;this.proposal=false;this.days=[];
  const first=this.first,last=this.last,mine=this.mine;this.lookupEmployees(this.employee?this.q.replace(/[—–]/g,'-'):this.q);
  const params=new HttpParams().set('mine',mine).set('from',this.add(first,-1)).set('to',last);
  try{const [rows,leaveData,policy,ot]=await Promise.all([this.pages<AttendanceRow>('/attendance-workspace/records',params,'items'),Promise.all([this.pages<Leave>('/employee-requests'+(mine?'/mine':''),new HttpParams().set('type','NGHI_PHEP').set('status','DA_DUYET').set('size',100),'content'),this.pages<LeaveType>('/leave-types',new HttpParams().set('size',100),'content')]).catch(e=>{if(v===this.version)this.leaveError=this.api.message(e);return null;}),firstValueFrom(this.http.get<{configured:boolean;holidays:string[]}>(API_BASE_URL+'/attendance-workspace/calendar-policy').pipe(takeUntilDestroyed(this.destroy))).catch(()=>null),this.pages<OtSlot>('/overtime/workspace/calendar',params,'items').catch(e=>{if(v===this.version)this.otError=this.api.message(e);return [];})]);if(v!==this.version)return;this.rows=rows;this.overtime=ot;this.holidays=policy?.holidays??[];this.calendarWarning='Hiển thị ngày lễ mặc định Việt Nam. Lịch Tết, ngày kèm Quốc khánh và nghỉ bù cần đối chiếu thông báo từng năm; không tự thay đổi công hoặc lương.';this.types=leaveData?.[1]??[];this.leaves=(leaveData?.[0]??[]).filter(l=>!l.withdrawnAt&&this.leaveState(l)!==null&&l.leaveStart<this.add(last,1)+'T00:00:00'&&l.leaveEnd>first+'T00:00:00');this.rebuild();}catch(e){if(v===this.version)this.error=this.api.message(e);}finally{if(v===this.version)this.loading=false;}
 }
 normalize(s:string){return s.normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/đ/g,'d').replace(/Đ/g,'D').toLowerCase();}
 get employees(){return this.employeeOptions;}
 get suggestions(){return this.employeeOptions;}
 lookupEmployees(query:string){
  const version=++this.lookupVersion;this.lookupSubscription?.unsubscribe();this.lookupError='';
  if(this.mine){this.lookupLoading=false;this.employeeOptions=[];return;}
  this.lookupLoading=true;this.employeeOptions=[];
  const params=new HttpParams().set('q',query.trim()).set('date',this.last);
  this.lookupSubscription=timer(250).pipe(switchMap(()=>this.http.get<Array<{employeeId:number;employeeCode:string;fullName:string}>>(API_BASE_URL+'/management/schedule-workspace/employees',{params})),takeUntilDestroyed(this.destroy)).subscribe({
   next:options=>{if(version!==this.lookupVersion)return;this.employeeOptions=options.map(e=>[e.employeeId,e.employeeCode+' - '+e.fullName]);this.lookupLoading=false;
    if(this.employee&&!options.some(e=>e.employeeId===+this.employee)){this.employee='';this.rebuild();}},
   error:e=>{if(version!==this.lookupVersion)return;this.lookupLoading=false;this.lookupError=this.api.message(e);this.employee='';this.rebuild();}
  });
 }
 searchEmployee(value:string){if(this.saving)return;this.q=value;this.employee='';this.suggesting=!!value.trim();this.rebuild();this.lookupEmployees(value);}
 chooseEmployee(value:string){if(this.saving)return;const option=this.employeeOptions.find(e=>e[0]===+value);this.employee=option?value:'';this.suggesting=false;if(option)this.q=option[1];this.rebuild();}
 get scopedRows(){return this.mine?this.rows:this.employee?this.rows.filter(r=>r.employeeId===+this.employee):[];}
 leaveState(l:Leave):'maternity'|'leave'|null{const t=this.types.find(t=>t.leaveTypeId===l.leaveTypeId);const text=this.normalize((t?.typeCode||'')+' '+(t?.typeName||'')).replace(/[_-]/g,' ');if(/\b(thai san|maternity)\b/.test(text))return 'maternity';if(/\b(om|sick)\b/.test(text))return null;return t?'leave':null;}
 leaveName(l:Leave){return this.types.find(t=>t.leaveTypeId===l.leaveTypeId)?.typeName||'';}
 leaveEmployee(l:Leave){return this.rows.find(r=>r.employeeId===l.employeeId)?.employeeLabel||'Nhân viên #'+l.employeeId;}
 get scopedOt(){return this.mine?this.overtime:this.employee?this.overtime.filter(o=>o.row.ot.employeeId===+this.employee):[];}
 get scopedLeaves(){return this.mine?this.leaves:this.employee?this.leaves.filter(l=>l.employeeId===+this.employee):[];}
 rebuild(){this.selected=null;this.detail=null;this.detailLoading=false;this.proposal=false;++this.detailVersion;this.days=[];if(!this.mine&&!this.employee)return;const first=this.first,last=this.last;const start=this.mode==='month'?this.add(first,-((new Date(first+'T12:00:00Z').getUTCDay()+6)%7)):first;const finish=this.mode==='month'?this.add(last,6-((new Date(last+'T12:00:00Z').getUTCDay()+6)%7)):last;
  for(let date=start;date<=finish;date=this.add(date,1))this.days.push({date,outside:date<first||date>last,rows:this.scopedRows.filter(r=>r.plannedStart<this.add(date,1)+'T00:00:00'&&r.plannedEnd>date+'T00:00:00'),ot:this.scopedOt.filter(o=>o.row.ot.requestedStart<this.add(date,1)+'T00:00:00'&&o.row.ot.requestedEnd>date+'T00:00:00'),leaves:this.scopedLeaves.filter(l=>l.leaveStart<this.add(date,1)+'T00:00:00'&&l.leaveEnd>date+'T00:00:00')});
 }
 rowState(r:AttendanceRow):State{if(r.attendanceResult==='VANG')return 'absent';if(r.anomalies.includes('DI_MUON')||r.anomalies.includes('VE_SOM'))return 'late';if(!r.actualStart||!r.actualEnd)return 'missing';return 'normal';}
 indicators(d:Day):State[]{return [...new Set<State>([...d.rows.map(r=>this.rowState(r)),...d.leaves.map(l=>this.leaveState(l)).filter((s):s is 'maternity'|'leave'=>s!==null),...(d.ot.length?['ot' as const]:[])])];}
 state(d:Day):State{if(d.rows.some(r=>this.rowState(r)==='normal'))return 'normal';if(d.leaves.some(l=>this.leaveState(l)==='maternity'))return 'maternity';if(d.leaves.some(l=>this.leaveState(l)==='leave'))return 'leave';if(!d.rows.length&&!d.ot.length&&this.isHoliday(d.date))return 'holiday';if(d.rows.some(r=>this.rowState(r)==='absent'))return 'absent';if(d.rows.some(r=>this.rowState(r)==='late'))return 'late';if(d.rows.some(r=>this.rowState(r)==='missing'))return 'missing';return d.rows.length?'normal':d.ot.length?'ot':'empty';}
 label(state:string){return this.legend.find(l=>l[0]===state)?.[1]||state;}
 matches(d:Day){return this.filter==='all'||(this.filter==='ot'&&d.ot.length>0)||(this.filter==='holiday'&&this.isHoliday(d.date))||d.leaves.some(l=>this.leaveState(l)===this.filter)||d.rows.some(r=>this.rowState(r)===this.filter);}
 get stats(){const rows=this.scopedRows.filter(r=>r.workDate>=this.first&&r.workDate<=this.last);const countLeave=(state:string)=>new Set(this.days.filter(d=>!d.outside).flatMap(d=>d.leaves.filter(l=>this.leaveState(l)===state).map(l=>l.employeeId+':'+d.date))).size;const unique=(rs:AttendanceRow[])=>new Set(rs.map(r=>r.employeeId+':'+r.workDate)).size;return {planned:unique(rows),present:unique(rows.filter(r=>!!r.actualStart&&r.attendanceResult!=='VANG')),late:rows.filter(r=>r.anomalies.includes('DI_MUON')).length,absent:unique(rows.filter(r=>r.attendanceResult==='VANG')),maternity:countLeave('maternity')};}
 select(d:Day){this.selected=d;this.detail=null;this.proposal=false;++this.detailVersion;if(d.rows.length===1)this.open(d.rows[0]);}
 async open(row:AttendanceRow){const v=++this.detailVersion;this.detailLoading=true;this.detail=null;this.proposal=false;try{const d=await firstValueFrom(this.api.detail(row.scheduleId,this.mine).pipe(takeUntilDestroyed(this.destroy)));if(v===this.detailVersion)this.detail=d;}catch(e){if(v===this.detailVersion)this.flash(this.api.message(e));}finally{if(v===this.detailVersion)this.detailLoading=false;}}
 close(){this.selected=null;this.detail=null;++this.detailVersion;this.detailLoading=false;this.proposal=false;}
 flash(message:string){this.notice=message;clearTimeout(this.timer);this.timer=setTimeout(()=>this.notice='',10000);}
 startProposal(){const r=this.detail?.row;if(!this.mine||!r?.canRequest||r.verificationStatus==='DA_XAC_NHAN')return;this.proposal=true;this.start=r.actualStart?.slice(0,16)||'';this.end=r.actualEnd?.slice(0,16)||'';this.rest=r.actualBreakMinutes;this.reason='';}
 async submit(){const r=this.detail?.row;if(!r?.canRequest||this.saving||!this.mine||r.verificationStatus==='DA_XAC_NHAN')return;if(!this.start||!this.end||this.end<=this.start||!Number.isInteger(this.rest)||this.rest<0||!this.reason.trim()){this.flash('Nhập giờ vào/ra hợp lệ, phút nghỉ và lý do.');return;}if(!(await this.actionDialogInPage.confirm('Gửi yêu cầu bổ sung / giải trình công?')))return;this.saving=true;try{await firstValueFrom(this.api.submitRequest(r,{actualStart:this.start,actualEnd:this.end,actualBreakMinutes:this.rest,attendanceResult:'DI_LAM',correctionReason:this.reason.trim()}).pipe(takeUntilDestroyed(this.destroy)));this.saving=false;await this.load();this.flash('Đã gửi yêu cầu.');}catch(e){this.flash(this.api.message(e));}finally{this.saving=false;}}
}
