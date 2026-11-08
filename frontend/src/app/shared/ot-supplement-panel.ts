import {SearchAssistDirective} from './search-assist.directive';
import {ActionDialogService} from './ui/action-dialog';
import {HrmNotice} from './ui/notice';
import {Pagination} from './pagination';
import {DetailFocusDirective} from './detail-focus.directive';
import {RowViewDirective} from './row-view.directive';
import {CommonModule} from '@angular/common';
import {Component,DestroyRef,Input,OnChanges,inject} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {HttpClient,HttpParams} from '@angular/common/http';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {API_BASE_URL} from '../services/api.config';
import {OtRow,OtBreak,OvertimeApiService} from '../pages/overtime/overtime-api.service';
interface Candidate {row:OtRow;eligible:boolean;unavailableReason:string;deadline:string;}
interface Request {requestId:number;overtimeId:number;employeeId:number;requestCode:string;employeeLabel:string;status:string;start:string;end:string;reason:string;sender:string;submittedAt:string;processor:string|null;processedAt:string|null;decisionReason:string|null;breaks:OtBreak[];canDecide:boolean;}
@Component({selector:'app-ot-supplement-panel',standalone:true,imports:[SearchAssistDirective,HrmNotice,Pagination,DetailFocusDirective,RowViewDirective,CommonModule,FormsModule],templateUrl:'./ot-supplement-panel.html'})
export class OtSupplementPanelComponent implements OnChanges {
 private actionDialogInPage=inject(ActionDialogService);

 @Input() mineAllowed=false;@Input() manage=false;@Input() overtimeId:number|null=null;
 private http=inject(HttpClient);private destroy=inject(DestroyRef);private api=inject(OvertimeApiService);private url=API_BASE_URL+'/overtime/workspace/supplements';private version=0;
 private date(offset=0){const d=new Date();d.setDate(d.getDate()+offset);return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;}
 detailFocusVersion=0;
 showDetail(r:Request){this.detail=r;this.decisionReason='';++this.detailFocusVersion;}
 mine=true;from=this.date(-7);to=this.date();loading=false;saving=false;error='';message='';open=false;candidates:Candidate[]=[];rows:Request[]=[];page=0;pages=0;candidatePage=0;candidatePages=0;selectedId='';selected:Candidate|null=null;start='';end='';rests:OtBreak[]=[];noRest=false;reason='';detail:Request|null=null;decisionReason='';
 ngOnChanges(){this.mine=this.mineAllowed;this.load();if(this.overtimeId&&this.mineAllowed)this.create();}
 private params(page:number){return new HttpParams().set('mine',this.mine).set('from',this.from).set('to',this.to).set('page',page);}
 private valid(){if(!this.from||!this.to||this.from>this.to){this.error='Chọn khoảng ngày ca OT hợp lệ.';return false;}return true;}
 load(page=0){if(this.saving||!this.valid())return;this.loading=true;this.error='';const version=++this.version;this.http.get<{items:Request[];page:number;totalPages:number}>(this.url,{params:this.params(page)}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{if(version!==this.version)return;this.rows=r.items;this.page=r.page;this.pages=r.totalPages;this.loading=false;},error:e=>{if(version===this.version){this.loading=false;this.error=this.api.message(e);}}});}
 create(){if(!this.mineAllowed||this.saving)return;this.mine=true;this.open=true;this.detail=null;this.loadCandidates();}
 loadCandidates(page=0){if(this.saving||!this.valid())return;this.loading=true;this.error='';this.selected=null;this.selectedId='';this.http.get<{items:Candidate[];page:number;totalPages:number}>(this.url+'/candidates',{params:this.params(page)}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{this.candidates=r.items;this.candidatePage=r.page;this.candidatePages=r.totalPages;this.loading=false;if(this.overtimeId){const item=r.items.find(c=>c.row.ot.overtimeId===this.overtimeId);if(item){this.selectedId=String(item.row.ot.overtimeId);this.select();}}},error:e=>{this.loading=false;this.error=this.api.message(e);}});}
 select(){this.selected=this.candidates.find(c=>String(c.row.ot.overtimeId)===String(this.selectedId))??null;this.error='';this.start=this.selected?.row.ot.actualStart??'';this.end=this.selected?.row.ot.actualEnd??'';this.rests=[];this.noRest=false;this.reason='';}
 async submit(){const c=this.selected;if(this.saving||!c?.eligible)return;if(!this.start||!this.end||this.end<=this.start||!this.reason.trim()||!this.rests.length&&!this.noRest){this.error='Nhập đủ giờ vào/ra, lý do và khoảng nghỉ thực tế (hoặc xác nhận không nghỉ).';return;}if(!(await this.actionDialogInPage.confirm('Gửi yêu cầu bổ sung công OT? Giờ gốc chưa thay đổi cho đến khi được duyệt.')))return;this.saving=true;this.error='';this.http.post(this.url+'/overtime/'+c.row.ot.overtimeId,{expectedVersion:c.row.version,start:this.start,end:this.end,breaks:this.rests,noBreaksConfirmed:this.noRest,reason:this.reason.trim()}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:()=>{this.saving=false;this.open=false;this.mine=true;this.message='Đã gửi yêu cầu bổ sung công OT, chờ xét duyệt.';this.load();},error:e=>{this.saving=false;this.error=this.api.message(e);}});}
 async decide(approve:boolean){const r=this.detail;if(this.saving||!r?.canDecide)return;if(!this.decisionReason.trim()){this.error='Nhập lý do quyết định.';return;}if(!(await this.actionDialogInPage.confirm(approve?'Duyệt đề nghị và bổ sung giờ OT thực tế? Giờ này vẫn chưa được xác nhận tính công.':'Từ chối yêu cầu? Công OT gốc được giữ nguyên.')))return;this.saving=true;this.error='';this.http.post(this.url+'/'+r.requestId+'/decision',{approve,reason:this.decisionReason.trim()}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:()=>{this.saving=false;this.detail=null;this.message=approve?'Đã duyệt bổ sung công OT, chờ xác nhận thực tế.':'Đã từ chối yêu cầu bổ sung công OT.';this.load();},error:e=>{this.saving=false;this.error=this.api.message(e);}});}
 label(status:string){return ({CHO_XU_LY:'Chờ xử lý',DA_CHAP_NHAN:'Đã chấp nhận',TU_CHOI:'Từ chối'} as Record<string,string>)[status]??status;}
}
