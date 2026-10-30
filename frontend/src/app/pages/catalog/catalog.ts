import {Component,DestroyRef,inject} from '@angular/core';
import {FormsModule,NgForm} from '@angular/forms';
import {ActivatedRoute} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Subscription,finalize} from 'rxjs';
import {RecordWorkspace} from '../../shared/record-workspace';
import {CatalogService,CatalogRow} from '../../services/catalog.service';
import {errorMessage} from '../../services/employee.service';
const catalogs={
 departments:{path:'departments',title:'Danh mục phòng ban',id:'departmentId',code:'departmentCode',name:'departmentName',paid:false},
 positions:{path:'positions',title:'Chức vụ',id:'positionId',code:'positionCode',name:'positionName',paid:false},
 qualifications:{path:'qualification-levels',title:'Danh mục trình độ',id:'qualificationLevelId',code:'levelCode',name:'levelName',paid:false},
 leaveTypes:{path:'leave-types',title:'Loại nghỉ phép',id:'leaveTypeId',code:'typeCode',name:'typeName',paid:true}
};
@Component({standalone:true,imports:[FormsModule,RecordWorkspace],templateUrl:'./catalog.html'})
export class CatalogPage {
 private api=inject(CatalogService);private route=inject(ActivatedRoute);private destroy=inject(DestroyRef);private request?:Subscription;
 keyword='';active='';config=catalogs.positions; rows:CatalogRow[]=[];selected:CatalogRow|null=null;mode='';consumes:boolean|null=null;policyError='';body:CatalogRow={};page=0;pages=0;total=0;loading=false;saving=false;error='';message='';
 constructor(){this.route.data.pipe(takeUntilDestroyed(this.destroy)).subscribe(data=>{this.request?.unsubscribe();this.keyword='';this.active='';this.config=catalogs[data['catalog'] as keyof typeof catalogs];this.selected=null;this.mode='';this.message='';this.load(0);});}
 load(page=0){if(this.saving)return;this.request?.unsubscribe();this.loading=true;this.error='';this.request=this.api.list(this.config.path,page,this.keyword.trim(),this.active).pipe(takeUntilDestroyed(this.destroy),finalize(()=>this.loading=false)).subscribe({next:r=>{this.rows=r.content;this.page=page;this.pages=r.totalPages;this.total=r.totalElements;},error:e=>this.error=errorMessage(e)});}
 open(row:CatalogRow|null){if(this.saving)return;this.consumes=null;this.policyError='';if(row&&this.config.paid){const id=Number(row[this.config.id]);this.api.policy(id).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{if(this.selected===row)this.consumes=r.consumesEntitlement;},error:e=>{if(this.selected===row)this.policyError=errorMessage(e);}});}this.selected=row;this.mode=row?'detail':'create';this.error='';this.message='';this.body=row?{...row}:{[this.config.code]:'',[this.config.name]:'',description:'',isActive:true,isPaid:false};}
 close(){if(!this.saving){this.mode='';this.selected=null;}}
 save(form:NgForm){if(this.saving||form.invalid)return;this.saving=true;this.error='';const body:CatalogRow={[this.config.code]:String(this.body[this.config.code]).trim(),[this.config.name]:String(this.body[this.config.name]).trim(),isActive:this.body['isActive']};if(this.config.paid)body['isPaid']=this.body['isPaid'];else body['description']=this.body['description']||null;
 this.api.save(this.config.path,this.selected?Number(this.selected[this.config.id]):null,body).pipe(takeUntilDestroyed(this.destroy),finalize(()=>this.saving=false)).subscribe({next:row=>{this.saving=false;this.selected=row;this.body={...row};this.mode='detail';this.message='Đã lưu danh mục.';this.load(this.page);},error:e=>this.error=errorMessage(e)});}
 savePolicy(){if(this.saving||!this.selected||this.consumes===null)return;this.saving=true;this.policyError='';this.api.savePolicy(Number(this.selected[this.config.id]),this.consumes).pipe(takeUntilDestroyed(this.destroy),finalize(()=>this.saving=false)).subscribe({next:()=>this.message='Đã cập nhật chính sách trừ quỹ phép.',error:e=>this.policyError=errorMessage(e)});}
 deactivate(){if(this.saving||!this.selected||!confirm('Ngừng sử dụng danh mục này? Lịch sử vẫn được giữ.'))return;this.saving=true;this.error='';this.api.deactivate(this.config.path,Number(this.selected[this.config.id])).pipe(takeUntilDestroyed(this.destroy),finalize(()=>this.saving=false)).subscribe({next:()=>{this.saving=false;this.close();this.message='Đã ngừng sử dụng.';this.load(this.page);},error:e=>this.error=errorMessage(e)});}
}
