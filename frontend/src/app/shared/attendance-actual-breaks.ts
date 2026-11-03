import {Component,DestroyRef,EventEmitter,Input,OnChanges,Output,inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {HttpClient,HttpParams} from '@angular/common/http';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {AttendanceApiService,AttendanceRow} from '../pages/attendance/attendance-api.service';
import {API_BASE_URL} from '../services/api.config';
interface Rest {start:string;end:string;}
@Component({selector:'app-attendance-actual-breaks',standalone:true,imports:[CommonModule,FormsModule],template:`
<section class="border rounded p-3 my-3"><h4 class="h6">Khoảng nghỉ thực tế đã đối soát</h4>
@if(error){<p class="text-danger" role="alert">{{error}}</p>}@if(loading){<p role="status">Đang tải…</p>}
@if(!editing){<ul>@for(b of rests;track $index){<li>{{b.start|date:'dd/MM/yyyy HH:mm:ss'}} → {{b.end|date:'dd/MM/yyyy HH:mm:ss'}}</li>}@empty{<li>{{verified?'Không có khoảng nghỉ thực tế.':'Chưa xác minh chi tiết nghỉ thực tế; không tự dùng giờ nghỉ theo lịch.'}}</li>}</ul>
@if(!mine&&row.canEdit&&row.attendanceId&&row.actualStart&&row.actualEnd&&row.verificationStatus==='CHUA_XAC_NHAN'){<button class="btn btn-sm btn-outline-primary" [disabled]="loading||saving" (click)="editing=true;reason=''">Đối soát khoảng nghỉ thực tế</button>}}
@if(editing){<form (ngSubmit)="save()"><fieldset [disabled]="saving">@for(b of rests;track $index;let i=$index){<div class="row g-2 mb-2"><label class="col-sm-5">Bắt đầu nghỉ {{i+1}}<input class="form-control" [name]="'restStart'+i" type="datetime-local" step="any" [(ngModel)]="b.start" required></label><label class="col-sm-5">Kết thúc nghỉ {{i+1}}<input class="form-control" [name]="'restEnd'+i" type="datetime-local" step="any" [(ngModel)]="b.end" required></label><div class="col-sm-2 align-self-end"><button type="button" class="btn btn-outline-danger" (click)="rests.splice(i,1)">Xóa</button></div></div>}
<button type="button" class="btn btn-sm btn-outline-primary mb-2" (click)="rests.push({start:'',end:''})">Thêm khoảng nghỉ</button><p class="small text-muted">Để trống danh sách nghĩa là xác nhận không nghỉ. Tổng phút nghỉ sẽ tính từ các khoảng thực tế, không sao chép lịch dự kiến.</p><label class="d-block">Lý do đối soát<textarea class="form-control" name="restReason" [(ngModel)]="reason" required maxlength="1000"></textarea></label><div class="d-flex gap-2 mt-2"><button class="btn btn-primary">Lưu đối soát</button><button type="button" class="btn btn-outline-secondary" (click)="editing=false;load()">Hủy</button></div></fieldset></form>}
</section>`})
export class AttendanceActualBreaksComponent implements OnChanges {
 @Input({required:true}) row!:AttendanceRow;@Input() mine=false;@Output() saved=new EventEmitter<void>();
 private http=inject(HttpClient);private api=inject(AttendanceApiService);private destroy=inject(DestroyRef);private version=0;
 rests:Rest[]=[];verified=false;loading=false;saving=false;editing=false;reason='';error='';
 ngOnChanges(){this.editing=false;this.load();}
 load(){const v=++this.version;this.error='';this.rests=[];this.loading=true;this.http.get<{verified:boolean;breaks:Rest[]}>(API_BASE_URL+'/attendance-workspace/schedules/'+this.row.scheduleId+'/actual-breaks',{params:new HttpParams().set('mine',this.mine)}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:d=>{if(v===this.version){this.rests=d.breaks.map(b=>({...b}));this.verified=d.verified;this.loading=false;}},error:e=>{if(v===this.version){this.error=this.api.message(e);this.loading=false;}}});}
 save(){if(this.saving||!this.row.attendanceId||this.mine||!this.row.canEdit)return;if(!this.reason.trim()||this.rests.some(b=>!b.start||!b.end||b.end<=b.start)){this.error='Nhập đủ khoảng nghỉ hợp lệ và lý do.';return;}if(!window.confirm('Lưu giờ nghỉ thực tế đã đối soát? '+(!this.rests.length?'Bạn xác nhận không có khoảng nghỉ.':'')))return;this.saving=true;this.error='';this.http.post(API_BASE_URL+'/attendance-workspace/records/'+this.row.attendanceId+'/actual-breaks',{expectedVersion:this.row.version,breaks:this.rests,reason:this.reason.trim()}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:()=>{this.saving=false;this.editing=false;this.saved.emit();},error:e=>{this.saving=false;this.error=this.api.message(e);}});}
}
