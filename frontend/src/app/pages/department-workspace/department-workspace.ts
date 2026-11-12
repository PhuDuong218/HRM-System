import {timer} from 'rxjs';
import {DepartmentCalendar} from './department-calendar';
import {Component,DestroyRef,inject} from '@angular/core';import {FormsModule} from '@angular/forms';import {RouterLink,ActivatedRoute} from '@angular/router';import {HttpClient} from '@angular/common/http';import {takeUntilDestroyed} from '@angular/core/rxjs-interop';import {API_BASE_URL} from '../../services/api.config';import {AuthService} from '../../services/auth.service';import {errorMessage} from '../../services/employee.service';import {PageHeader} from '../../shared/page-header';import {RecordWorkspace} from '../../shared/record-workspace';import {Pagination} from '../../shared/pagination';import {ActionDialogService} from '../../shared/ui/action-dialog';
type Row=Record<string,any>;
const definitions:Record<string,{title:string;columns:[string,string][]}>={
 assignments:{title:'Phân công trong phòng ban',columns:[['employeeCode','Mã nhân viên'],['fullName','Họ tên'],['department','Phòng ban'],['position','Chức vụ'],['effectiveFrom','Hiệu lực từ'],['effectiveTo','Đến trước ngày']]},
 schedules:{title:'Lịch làm việc phòng ban',columns:[['employeeCode','Mã nhân viên'],['fullName','Họ tên'],['shift','Ca'],['workDate','Ngày'],['plannedStart','Bắt đầu'],['plannedEnd','Kết thúc'],['breakMinutes','Nghỉ (phút)'],['scheduleStatus','Trạng thái']]},
 attendance:{title:'Chấm công phòng ban',columns:[['employeeCode','Mã nhân viên'],['fullName','Họ tên'],['workDate','Ngày'],['shift','Ca'],['actualStart','Check-in'],['actualEnd','Check-out'],['status','Xác nhận'],['result','Kết quả']]},
 overtime:{title:'Tăng ca phòng ban',columns:[['code','Mã đơn'],['employeeCode','Mã nhân viên'],['fullName','Họ tên'],['plannedStart','Bắt đầu'],['plannedEnd','Kết thúc'],['status','Trạng thái'],['actualStart','Giờ vào'],['actualEnd','Giờ ra']]},
 reviews:{title:'Duyệt OT và giải trình công cấp phòng',columns:[['code','Mã đơn'],['kind','Loại'],['employeeCode','Mã nhân viên'],['fullName','Họ tên'],['plannedStart','Bắt đầu'],['submittedAt','Ngày gửi'],['sourceStatus','Trạng thái đơn'],['status','Cấp phòng']]},
 summary:{title:'Tổng hợp công phòng ban',columns:[['employeeCode','Mã nhân viên'],['employeeName','Họ tên'],['department','Phòng ban'],['workDate','Ngày công'],['presentRecords','Lượt đi làm'],['absentRecords','Lượt vắng'],['netMinutes','Phút công thuần'],['unresolvedRecords','Cần đối soát']]},
 reports:{title:'Báo cáo công phòng ban',columns:[['employeeCode','Mã nhân viên'],['employeeName','Họ tên'],['department','Phòng ban'],['workDate','Ngày công'],['presentRecords','Lượt đi làm'],['absentRecords','Lượt vắng'],['netMinutes','Phút công thuần'],['unresolvedRecords','Cần đối soát']]}
};
@Component({standalone:true,imports:[DepartmentCalendar,FormsModule,RouterLink,PageHeader,RecordWorkspace,Pagination],templateUrl:'./department-workspace.html',styleUrl:'./department-workspace.css'})
export class DepartmentWorkspacePage {
 private http=inject(HttpClient);private route=inject(ActivatedRoute);private destroy=inject(DestroyRef);private dialog=inject(ActionDialogService);auth=inject(AuthService);private listVersion=0;private detailVersion=0;
 showCalendar=false;
 area='assignments';get config(){return definitions[this.area];}get periodSummary(){return this.area==='summary'||this.area==='reports';}rows:Row[]=[];options:{employees:Row[];shifts:Row[]}={employees:[],shifts:[]};selected:Row|null=null;detail:Row|null=null;editing=false;loading=false;saving=false;detailLoading=false;error='';detailError='';message='';from=this.monthStart();to=this.date(new Date());q='';status='';employeeId:number|null=null;page=0;pages=0;total=0;netMinutes=0;unresolved=0;note='';form:{employeeId:number|null;shiftId:number|null;workDate:string;note:string}={employeeId:null,shiftId:null,workDate:this.date(new Date()),note:''};private applied={from:this.from,to:this.to,q:'',status:'',employeeId:null as number|null};
 constructor(){timer(15000,15000).pipe(takeUntilDestroyed(this.destroy)).subscribe(()=>{if(this.area==='attendance'&&!document.hidden&&!this.loading&&!this.saving&&!this.selected)this.load(this.page);});this.route.data.pipe(takeUntilDestroyed(this.destroy)).subscribe(data=>{this.area=data['departmentArea']||'assignments';this.selected=null;this.detail=null;this.editing=false;this.status='';this.from=this.monthStart();this.to=this.date(new Date());this.search();this.loadOptions();});this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroy)).subscribe(params=>{const id=Number(params.get('notificationRequest'));const kind=params.get('notificationKind');if(this.area==='reviews'&&id>0&&['OT','ATTENDANCE'].includes(kind||'')){const notificationVersion=++this.detailVersion;this.http.get<Row>(API_BASE_URL+'/department-workspace/reviews/'+kind+'/'+id).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:d=>{if(notificationVersion!==this.detailVersion)return;this.open({...d,kind,id,employeeId:d['employee_id'],employeeCode:d['employeeCode']||'',sourceStatus:d['sourceStatus'],status:d['review']?.length?d['review'][0]['decision']:'PENDING',plannedStart:d['starts'],plannedEnd:d['ends']});},error:e=>{if(notificationVersion===this.detailVersion)this.error=errorMessage(e);}});}});}
 keys(row:Row){return Object.keys(row);}
 date(d:Date){return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');}monthStart(){const d=new Date();d.setDate(1);return this.date(d);}
 loadOptions(){this.http.get<any>(API_BASE_URL+'/department-workspace/options').pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>this.options=r,error:e=>this.error=errorMessage(e)});}
 search(){if(this.saving)return;this.applied={from:this.from,to:this.to,q:this.q,status:this.status,employeeId:this.employeeId};this.close();this.load(0);}
 reset(){this.q='';this.status='';this.employeeId=null;this.from=this.monthStart();this.to=this.date(new Date());this.search();}
 load(page=0){if(this.saving)return;const a=this.applied;if(!a.from||!a.to||a.from>a.to){this.error='Kiểm tra khoảng ngày';return;}const v=++this.listVersion;this.loading=true;this.error='';const params:Record<string,string|number>={from:a.from,to:a.to,page};if(a.q)params['q']=a.q;if(a.status)params['status']=a.status;if(a.employeeId)params['employeeId']=a.employeeId;const path=this.periodSummary?'summary':'list/'+this.area;this.http.get<any>(API_BASE_URL+'/department-workspace/'+path,{params}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{if(v!==this.listVersion)return;this.loading=false;if(this.periodSummary){this.rows=r.days;this.netMinutes=r.netMinutes;this.unresolved=r.unresolvedRecords;this.total=this.rows.length;this.pages=0;}else{this.rows=r.items;this.total=r.totalElements;this.pages=r.totalPages;}this.page=page;},error:e=>{if(v!==this.listVersion)return;this.loading=false;this.rows=[];this.total=this.pages=0;this.error=errorMessage(e);}});}
 display(value:any,key=''):string{if(value===null||value===undefined||value==='')return '—';const labels:Record<string,string>={PENDING:'Chờ cấp phòng',APPROVED:'Đồng ý cấp phòng',REJECTED:'Không đồng ý cấp phòng',CHO_DUYET:'Chờ duyệt cuối',DA_DUYET:'Đã duyệt',TU_CHOI:'Từ chối',CHO_XU_LY:'Chờ xử lý',DANG_XU_LY:'Đã tiếp nhận',DA_CHAP_NHAN:'Đã chấp thuận',DA_XAC_NHAN:'Đã xác nhận',CHUA_XAC_NHAN:'Chưa xác nhận',CHUA_CO_CONG:'Chưa có công',ACTIVE:'Đang hiệu lực',CANCELLED:'Đã hủy',DI_LAM:'Đi làm',VANG:'Vắng',OT:'Tăng ca',ATTENDANCE:'Giải trình công'};if(labels[value])return labels[value];if(typeof value==='string'&&/^\d{4}-\d{2}-\d{2}/.test(value)){if(/Z$|[+-]\d\d:\d\d$/.test(value)){const d=new Date(value);return key==='workDate'?d.toLocaleDateString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh'}):d.toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh'});}const [day,time]=value.split('T');return day.split('-').reverse().join('/')+(time?' '+time.substring(0,8):'');}return String(value);}
 open(row:Row){if(this.saving)return;const v=++this.detailVersion;this.selected=row;this.detail=null;this.detailError='';this.editing=false;this.note='';if(this.periodSummary)return;let path=this.area==='assignments'?'personnel/'+row['employeeId']:this.area==='reviews'?'reviews/'+row['kind']+'/'+row['id']:'detail/'+this.area+'/'+row['id'];this.detailLoading=true;this.http.get<Row>(API_BASE_URL+'/department-workspace/'+path).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{if(v!==this.detailVersion)return;this.detail=r;this.detailLoading=false;},error:e=>{if(v!==this.detailVersion)return;this.detailLoading=false;this.detailError=errorMessage(e);}});}
 close(){if(this.saving)return;++this.detailVersion;this.selected=null;this.detail=null;this.editing=false;this.detailLoading=false;}
 get canDecide(){return this.area==='reviews'&&this.selected?.['status']==='PENDING'&&['CHO_DUYET','CHO_XU_LY','DANG_XU_LY'].includes(this.selected?.['sourceStatus'])&&this.selected?.['employeeCode']!==this.auth.profile()?.employeeCode&&!this.detailLoading&&!!this.detail;}
 async decide(approve:boolean){const r=this.selected;if(!r||!this.canDecide||this.saving)return;if(!this.note.trim()){this.detailError='Nhập lý do cho quyết định cấp phòng';return;}if(!(await this.dialog.confirm(approve?'Đồng ý đơn ở cấp phòng và chuyển ADMIN/HR duyệt cuối?':'Xác nhận không đồng ý đề xuất ở cấp phòng?')))return;this.saving=true;this.http.post(API_BASE_URL+'/department-workspace/reviews/'+r['kind']+'/'+r['id'],{approve,reason:this.note.trim(),expectedVersion:this.selected?.['fingerprint']}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:()=>{this.saving=false;this.message='Đã ghi nhận quyết định cấp phòng.';this.load(this.page);this.open({...r,status:approve?'APPROVED':'REJECTED'});},error:e=>{this.saving=false;this.detailError=errorMessage(e);}});}
 startEdit(row?:Row){if(this.saving)return;this.selected=row||{};this.detail=null;this.editing=true;this.detailError='';this.form={employeeId:row?.['employeeId']||null,shiftId:row?.['shiftId']||null,workDate:row?.['workDate']||this.date(new Date()),note:row?.['note']||''};}
 async cancelSchedule(){const id=this.selected?.['id'];if(!id||this.saving)return;const reason=await this.dialog.prompt('Nhập lý do hủy lịch trước khi ca bắt đầu');if(!reason?.trim())return;this.saving=true;this.http.post(API_BASE_URL+'/department-workspace/schedules/'+id+'/cancel',{reason}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:()=>{this.saving=false;this.close();this.message='Đã hủy lịch làm việc.';this.load(this.page);},error:e=>{this.saving=false;this.detailError=errorMessage(e);}});}
 get chosenShift(){return this.options.shifts.find(s=>s['shiftId']===this.form.shiftId);}
 async save() {
  if (this.saving) return;
  const body = {...this.form};
  const employee = this.options.employees.find(e => e['employeeId'] === body.employeeId);
  const shift = this.chosenShift;
  if (!employee || !shift || !body.workDate) {
   this.detailError = 'Chọn nhân viên, ca và ngày làm việc';
   return;
  }
  const id = this.selected?.['id'];
  const confirmation = [
   id ? 'Bạn có chắc muốn lưu thay đổi lịch?' : 'Bạn có chắc muốn thêm lịch làm việc?',
   `Nhân viên: ${employee['employeeCode']} - ${employee['fullName']}`,
   `Ca: ${shift['shiftCode']} - ${shift['shiftName']}`,
   `Ngày làm: ${body.workDate.split('-').reverse().join('/')}`,
  ].join('\n');
  if (!(await this.dialog.confirm(confirmation))) return;

  this.saving = true;
  this.http.request(id ? 'PUT' : 'POST', API_BASE_URL + '/department-workspace/schedules' + (id ? '/' + id : ''), {body})
   .pipe(takeUntilDestroyed(this.destroy))
   .subscribe({
    next: () => {
     this.saving = false;
     this.close();
     this.message = 'Đã lưu lịch làm việc.';
     this.load(0);
    },
    error: e => {
     this.saving = false;
     this.detailError = errorMessage(e);
    },
   });
 }
}
