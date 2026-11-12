import { Pagination } from './pagination';
import {Component,Input,Output,EventEmitter,OnInit,DestroyRef,inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {HttpClient} from '@angular/common/http';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {API_BASE_URL} from '../services/api.config';
interface Contract {contractId:number;contractNumber:string;contractTypeName:string;signedDate:string;startDate:string;endDate:string|null;status:string;}
interface Decision {decisionId:number;decisionNumber:string;decisionType:string;content:string;issuedDate:string;approvalStatus:string;payrollEffect:string;amount:number|null;applyDate:string|null;disciplineForm:string|null;violationDate:string|null;salaryIncreaseDelayMonths:number|null;}
interface ContractDetail {contract:Contract;salaryTerms:Array<{salaryTermId:number;effectiveFrom:string;effectiveTo:string|null;baseSalary:number;payBasis:string}>;}
@Component({selector:'app-my-employment',standalone:true,imports: [Pagination,CommonModule],templateUrl:'./my-employment.html'})
export class MyEmployment implements OnInit {
 @Input() kind:'contracts'|'decisions'='contracts';@Output() back=new EventEmitter<void>();
 private http=inject(HttpClient);private destroy=inject(DestroyRef);private version=0;private detailVersion=0;
 loading=false;detailLoading=false;error='';detailError='';page=0;pages=0;total=0;
 contracts:Contract[]=[];decisions:Decision[]=[];contract:ContractDetail|null=null;decision:Decision|null=null;
 readonly labels:Record<string,string>={DA_KY:'Đã ký',DA_KET_THUC:'Đã kết thúc',DA_HUY:'Đã hủy',KHEN_THUONG:'Khen thưởng',KY_LUAT:'Kỷ luật',BOI_THUONG:'Bồi thường',DA_DUYET:'Đã duyệt',NONE:'Không ảnh hưởng lương',ADD:'Cộng vào lương',DEDUCT:'Khấu trừ',THANG:'Tháng',GIO:'Giờ',KHIEN_TRACH:'Khiển trách',KEO_DAI_NANG_LUONG:'Kéo dài thời hạn nâng lương',CACH_CHUC:'Cách chức',SA_THAI:'Sa thải'};
 ngOnInit(){this.destroy.onDestroy(()=>{++this.version;++this.detailVersion;});this.load();}
 load(page=0){const v=++this.version;++this.detailVersion;this.loading=true;this.error='';this.close();
  this.http.get<{items:Array<Contract|Decision>;page:number;totalPages:number;totalElements:number}>(`${API_BASE_URL}/me/${this.kind}`,{params:{page,size:10}}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{if(v!==this.version)return;this.contracts=this.kind==='contracts'?r.items as Contract[]:[];this.decisions=this.kind==='decisions'?r.items as Decision[]:[];this.page=r.page;this.pages=r.totalPages;this.total=r.totalElements;this.loading=false;},error:()=>{if(v!==this.version)return;this.contracts=[];this.decisions=[];this.total=0;this.pages=0;this.loading=false;this.error='Không tải được dữ liệu cá nhân. Vui lòng thử lại.';}});
 }
 openContract(row:Contract){const v=++this.detailVersion;this.contract=null;this.detailLoading=true;this.detailError='';this.http.get<ContractDetail>(`${API_BASE_URL}/me/contracts/${row.contractId}`).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{if(v===this.detailVersion){this.contract=r;this.detailLoading=false;}},error:()=>{if(v===this.detailVersion){this.detailLoading=false;this.detailError='Không tải được chi tiết hợp đồng.';}}});}
 openDecision(row:Decision){++this.detailVersion;this.decision=row;this.detailError='';}
 close(){++this.detailVersion;this.contract=null;this.decision=null;this.detailError='';this.detailLoading=false;}
}
