import {Feedback} from '../../shared/ui/feedback';
import {HrmButton} from '../../shared/ui/button';
import {LeaveWorkflowGuide} from '../../shared/leave-workflow-guide';
import {Pagination} from '../../shared/pagination';
import {Component,inject,DestroyRef} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {DatePipe} from '@angular/common';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {finalize} from 'rxjs';
import {PageHeader} from '../../shared/page-header';
import {RecordWorkspace} from '../../shared/record-workspace';
import {TranslatePipe} from '../../shared/translate.pipe';
import {HeadRequestsService,HeadRequest,HeadRequestDetail} from '../../services/head-requests.service';
import {errorMessage} from '../../services/employee.service';
@Component({standalone:true,imports:[Feedback,HrmButton,LeaveWorkflowGuide,Pagination,FormsModule,DatePipe,PageHeader,RecordWorkspace,TranslatePipe],templateUrl:'./head-requests.html',host:{class:'management-page'}})
export class HeadRequestsPage {
 private api=inject(HeadRequestsService);private destroy=inject(DestroyRef);
 rows:HeadRequest[]=[];selected:HeadRequest|null=null;detail:HeadRequestDetail|null=null;
 page=0;pages=0;total=0;loading=false;saving=false;error='';message='';note='';detailLoading=false;
 canReview=false;checkingAccess=true;
 constructor(){this.api.capabilities().pipe(takeUntilDestroyed(this.destroy),finalize(()=>this.checkingAccess=false)).subscribe({next:c=>{this.canReview=c.departmentHead;if(this.canReview)this.load();else this.error='Duyệt cấp 1 dành cho trưởng phòng đang có nhiệm kỳ. ADMIN/HR xử lý duyệt cuối tại Quản lý đơn từ.';},error:e=>this.error=errorMessage(e)});}
 load(page=this.page){if(this.loading||this.saving||!this.canReview)return;this.page=page;this.loading=true;this.error='';this.api.list(page).pipe(takeUntilDestroyed(this.destroy),finalize(()=>this.loading=false)).subscribe({next:r=>{this.rows=r.content;this.pages=r.totalPages;this.total=r.totalElements;},error:e=>this.error=errorMessage(e)});}
 open(row:HeadRequest){if(this.saving||this.detailLoading)return;this.selected=row;this.note='';this.detail=null;this.detailLoading=true;this.api.detail(row.requestId).pipe(takeUntilDestroyed(this.destroy),finalize(()=>this.detailLoading=false)).subscribe({next:r=>this.detail=r,error:e=>this.error=errorMessage(e)});}
 close(){if(!this.saving&&!this.detailLoading){this.selected=null;this.detail=null;}}
 decide(approve:boolean){if(!this.selected||!this.detail||this.saving||this.detailLoading)return;if(!approve&&!this.note.trim()){this.error='Nhập lý do từ chối.';return;}this.saving=true;this.error='';this.api.decide(this.selected.requestId,approve,this.note.trim()).pipe(takeUntilDestroyed(this.destroy),finalize(()=>this.saving=false)).subscribe({next:()=>{this.saving=false;this.close();this.message=approve?'Đã duyệt cấp 1. Đơn được chuyển HR/ADMIN xử lý cuối.':'Đã từ chối đơn.';this.load();},error:e=>this.error=errorMessage(e)});}
}
