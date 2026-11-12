import {Component,Input,Output,EventEmitter,inject,DestroyRef,OnChanges} from '@angular/core';
import {ActionDialogService} from './ui/action-dialog';
import {FormsModule} from '@angular/forms';import {HttpClient} from '@angular/common/http';import {firstValueFrom} from 'rxjs';import {takeUntilDestroyed} from '@angular/core/rxjs-interop';import {API_BASE_URL} from '../services/api.config';import {errorMessage} from '../services/employee.service';
interface LeaveType {leaveTypeId:number;typeCode:string;typeName:string;evidenceRequired:boolean}
@Component({selector:'app-personal-request-form',standalone:true,imports:[FormsModule],template:`
<div class="d-flex justify-content-end gap-2 mb-3"><button type="button" class="btn btn-primary" [disabled]="saving" (click)="open(false)">{{requestType==='NGHI_VIEC'?'Nộp đơn nghỉ việc':'Nộp đơn nghỉ phép'}}</button>@if(requestType==='NGHI_PHEP'){<button type="button" class="btn btn-outline-primary" [disabled]="saving" (click)="open(true)">Bổ sung phép</button>}</div>
@if(opened){<section class="surface mb-3"><h2>{{requestType==='NGHI_VIEC'?'Nộp đơn nghỉ việc của tôi':retroactive?'Bổ sung phép':'Nộp đơn nghỉ phép của tôi'}}</h2><p class="small text-muted">Đơn cá nhân được chuyển đến người có thẩm quyền độc lập xét duyệt.</p>@if(error){<div class="alert alert-danger" role="alert">{{error}}</div>}<form (ngSubmit)="submit()" class="row g-3">
@if(requestType==='NGHI_PHEP'){<label class="col-md-12">Loại nghỉ<select class="form-select" name="leaveType" required [(ngModel)]="typeId"><option [ngValue]="null">Chọn loại nghỉ</option>@for(t of types;track t.leaveTypeId){<option [ngValue]="t.leaveTypeId">{{t.typeName}}</option>}</select></label><label class="col-md-6">Bắt đầu nghỉ<input class="form-control" type="datetime-local" name="start" required [(ngModel)]="start"></label><label class="col-md-6">Kết thúc nghỉ (không gồm thời điểm này)<input class="form-control" type="datetime-local" name="end" required [(ngModel)]="end"></label><label class="col-12">Minh chứng {{evidenceRequired?'(bắt buộc)':'(nếu có)'}}<input class="form-control" type="file" accept="image/jpeg,image/png,application/pdf" [required]="evidenceRequired" [disabled]="saving" (change)="choose($event)"><small>Ảnh JPG, PNG hoặc PDF, tối đa 3 MB. Nghỉ ốm cần giấy khám bệnh hoặc giấy tờ minh chứng.</small></label>}
@else{<label class="col-12">Ngày làm việc cuối đề nghị<input class="form-control" type="date" name="last" required [(ngModel)]="last"></label>}
<label class="col-12">Lý do<textarea class="form-control" name="reason" required maxlength="1000" [(ngModel)]="reason"></textarea></label><div class="col-12 d-flex gap-2"><button class="btn btn-primary" [disabled]="saving">{{saving?'Đang gửi…':'Gửi đơn'}}</button><button class="btn btn-outline-secondary" type="button" [disabled]="saving" (click)="opened=false">Hủy</button></div></form></section>}`})
export class PersonalRequestForm implements OnChanges {
 @Input() requestType='NGHI_PHEP';@Output() saved=new EventEmitter<void>();private dialog=inject(ActionDialogService);private http=inject(HttpClient);private destroy=inject(DestroyRef);
 types:LeaveType[]=[];typeId:number|null=null;start='';end='';last='';reason='';file:File|null=null;opened=false;saving=false;error='';
 retroactive=false;
 open(retroactive:boolean){
   this.opened=true;
   this.retroactive=retroactive;
   this.start='';
   this.end='';
   this.error='';
 }
 get evidenceRequired(){return !!this.types.find(t=>t.leaveTypeId===this.typeId)?.evidenceRequired;}
 ngOnChanges(){this.opened=false;this.error='';this.reason='';this.file=null;this.http.get<{types:LeaveType[]}>(API_BASE_URL+'/me/leave-options').pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>this.types=r.types,error:e=>this.error=errorMessage(e)});}
 choose(e:Event){this.file=(e.target as HTMLInputElement).files?.[0]??null;this.error='';if(this.file&&this.file.size>3*1024*1024){this.error='Minh chứng tối đa 3 MB';this.file=null;(e.target as HTMLInputElement).value='';}}
 async submit(){if(this.saving)return;this.error='';if(!this.reason.trim()){this.error='Nhập lý do';return;}if(this.requestType==='NGHI_PHEP'&&(!this.typeId||!this.start||!this.end||this.end<=this.start||this.evidenceRequired&&!this.file)){this.error='Kiểm tra thời gian và đính kèm minh chứng bắt buộc.';return;}if(this.requestType==='NGHI_PHEP'){
   const today=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Ho_Chi_Minh'}).format(new Date());
   if((this.start.slice(0,10)<today)!==this.retroactive){
     this.error=this.retroactive?'Chọn ngày nghỉ trước hôm nay.':'Ngày đã qua. Hãy chọn Bổ sung phép.';
     return;
   }
 }this.saving=true;if(!(await this.dialog.confirm('Xác nhận gửi đơn của bạn để xét duyệt?'))){this.saving=false;return;}try{let reference='';if(this.file){const data=await new Promise<string>((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(String(reader.result).split(',')[1]);reader.onerror=reject;reader.readAsDataURL(this.file!);});reference=(await firstValueFrom(this.http.post<{reference:string}>(API_BASE_URL+'/employee-requests/evidence',{content:data}).pipe(takeUntilDestroyed(this.destroy)))).reference;}
 const path=this.requestType==='NGHI_VIEC'?'resignation':'leave';const body=this.requestType==='NGHI_VIEC'?{requestedLastWorkingDate:this.last,reason:this.reason.trim()}:{leaveTypeId:this.typeId,leaveStart:this.start,leaveEnd:this.end,reason:this.reason.trim(),evidenceReference:reference,retroactive:this.retroactive};await firstValueFrom(this.http.post(API_BASE_URL+'/employee-requests/'+path,body).pipe(takeUntilDestroyed(this.destroy)));this.opened=false;this.reason='';this.file=null;this.saved.emit();}catch(e){this.error=errorMessage(e);}finally{this.saving=false;}}
}
