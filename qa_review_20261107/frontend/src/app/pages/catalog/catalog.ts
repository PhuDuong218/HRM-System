import {SearchAssistDirective} from '../../shared/search-assist.directive';
import {EditFormDirective} from '../../shared/edit-form.directive';
import {HrmNotice} from '../../shared/ui/notice';
import {RowViewDirective} from '../../shared/row-view.directive';
import {StatusBadge} from '../../shared/ui/status-badge';
import {Feedback} from '../../shared/ui/feedback';
import {HrmButton} from '../../shared/ui/button';
import {LeaveWorkflowGuide} from '../../shared/leave-workflow-guide';
import {ManagementPageBase} from '../../shared/management-page.base';
import {PageHeader} from '../../shared/page-header';
import {TranslatePipe} from '../../shared/translate.pipe';
import {Pagination} from '../../shared/pagination';
import {Component,DestroyRef,inject} from '@angular/core';
import {FormsModule,NgForm} from '@angular/forms';
import {ActivatedRoute,Router} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Subscription,finalize,combineLatest} from 'rxjs';
import {RecordWorkspace} from '../../shared/record-workspace';
import {CatalogService,CatalogRow} from '../../services/catalog.service';
import {errorMessage} from '../../services/employee.service';
import {AuthService} from '../../services/auth.service';
const catalogs={
 departments:{path:'departments',title:'Danh mục phòng ban',id:'departmentId',code:'departmentCode',name:'departmentName',paid:false},
 positions:{path:'positions',title:'Chức vụ',id:'positionId',code:'positionCode',name:'positionName',paid:false},
 qualifications:{path:'qualification-levels',title:'Danh mục trình độ',id:'qualificationLevelId',code:'levelCode',name:'levelName',paid:false},
 leaveTypes:{path:'leave-types',title:'Loại nghỉ phép',id:'leaveTypeId',code:'typeCode',name:'typeName',paid:true}
};
@Component({standalone:true,imports:[SearchAssistDirective,EditFormDirective,HrmNotice,RowViewDirective,StatusBadge,Feedback,HrmButton,LeaveWorkflowGuide,PageHeader,Pagination,FormsModule,RecordWorkspace,TranslatePipe],templateUrl:'./catalog.html'})
export class CatalogPage extends ManagementPageBase {
 saveRevision=0;private savedConsumes:boolean|null=null;
 private readonly auth=inject(AuthService);
 get admin(){return this.auth.profile()?.role==='ADMIN';}
 private router=inject(Router);private api=inject(CatalogService);private route=inject(ActivatedRoute);private destroy=inject(DestroyRef);private request?:Subscription;
 accrualPolicy={annualDays:12,hoursPerDay:8,seniorityExtra:false,departmentId:null as number|null};accrualDepartments:CatalogRow[]=[];accrualLoaded=false;accrualConfigured=false;grant={periodStart:"",periodEnd:"",reason:""};grantResults:{employeeCode:string;result:string;hours?:number}[]=[];
 changeReason="";
 rules={retroactiveDays:0,minimumNoticeHours:0,minimumServiceMonths:0,evidenceRequired:false,maximumHours:null as number|null};rulesLoaded=false;
 loadRules(row:CatalogRow){this.rulesLoaded=false;this.accrualLoaded=false;this.accrualConfigured=false;this.grantResults=[];this.grant={periodStart:"",periodEnd:"",reason:""};this.accrualPolicy={annualDays:12,hoursPerDay:8,seniorityExtra:false,departmentId:null as number|null};
 this.api.departments().pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>this.accrualDepartments=r.filter(d=>d['isActive']!==false),error:e=>{if(this.selected===row)this.error=errorMessage(e);}});
 this.api.accrual(Number(row[this.config.id])).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{if(this.selected===row){this.accrualConfigured=r['configured']!==false;if(this.accrualConfigured)this.accrualPolicy={annualDays:r['annualDays'],hoursPerDay:r['hoursPerDay'],seniorityExtra:r['seniorityExtra'],departmentId:r['departmentId']??null};this.accrualLoaded=true;}},error:e=>{if(this.selected===row)this.error=errorMessage(e);}});
 this.api.rules(Number(row[this.config.id])).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{if(this.selected===row){this.rules=r;this.rulesLoaded=true;}},error:e=>{if(this.selected===row)this.error=errorMessage(e);}});}
 saveAccrual(){if(!this.changeReason.trim()){this.error="Nhập lý do thay đổi.";return;}if(!this.admin||!this.selected||this.saving)return;this.runCommand(this.api.saveAccrual(Number(this.selected[this.config.id]),this.accrualPolicy,this.changeReason),()=>{this.accrualConfigured=true;this.saveRevision++;},'Đã lưu chính sách cấp phép.');}
 async generate(){if(!this.selected||this.saving||!this.grant.reason.trim()||!(await this.confirmAction('Cấp phép theo chính sách cho nhân viên trong phạm vi? Quỹ đã có sẽ giữ nguyên.')))return;this.runCommand(this.api.generate(Number(this.selected[this.config.id]),this.grant),rows=>this.grantResults=rows,'Đã xử lý cấp phép theo chính sách.');}
 saveRules(){if(!this.changeReason.trim()){this.error="Nhập lý do thay đổi.";return;}if(this.saving||!this.selected||!this.admin||!this.rulesLoaded)return;this.saving=true;this.api.saveRules(Number(this.selected[this.config.id]),this.rules,this.changeReason).pipe(takeUntilDestroyed(this.destroy),finalize(()=>this.saving=false)).subscribe({next:()=>{this.saveRevision++;this.message='Đã lưu điều kiện loại nghỉ.';},error:e=>this.error=errorMessage(e)});}
 keyword='';active='';config=catalogs.positions; rows:CatalogRow[]=[];selected:CatalogRow|null=null;mode='';consumes:boolean|null=null;policyError='';body:CatalogRow={};page=0;pages=0;total=0;
 constructor(){super();combineLatest([this.route.data,this.route.queryParamMap]).pipe(takeUntilDestroyed(this.destroy)).subscribe(([data,params])=>{this.request?.unsubscribe();this.keyword=params.get('keyword')||'';this.active=['true','false'].includes(params.get('active')||'')?params.get('active')!:'';this.applied={keyword:this.keyword.trim(),active:this.active};this.config=catalogs[data['catalog'] as keyof typeof catalogs];this.selected=null;this.mode='';this.message='';this.load(0);});}
 private applied={keyword:'',active:''};
 search(){if(this.loading||this.saving)return;this.close();const keyword=this.keyword.trim();const active=this.active;
 if((this.route.snapshot.queryParamMap.get('keyword')||'')===keyword&&(this.route.snapshot.queryParamMap.get('active')||'')===active){this.applied={keyword,active};this.load(0);return;}
 void this.router.navigate([],{relativeTo:this.route,queryParams:{keyword:keyword||null,active:active||null}});
 }
 resetFilters(){this.keyword='';this.active='';this.search();}
 load(page=0){if(this.saving)return;this.request?.unsubscribe();this.loading=true;this.error='';this.request=this.api.list(this.config.path,page,this.applied.keyword,this.applied.active).pipe(takeUntilDestroyed(this.destroy),finalize(()=>this.loading=false)).subscribe({next:r=>{this.rows=r.content;this.page=page;this.pages=r.totalPages;this.total=r.totalElements;},error:e=>this.error=errorMessage(e)});}
 open(row:CatalogRow|null){if(this.saving)return;this.consumes=null;this.policyError='';if(row&&this.config.paid){const id=Number(row[this.config.id]);this.api.policy(id).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{if(this.selected===row){this.consumes=r.consumesEntitlement;this.savedConsumes=r.consumesEntitlement;}},error:e=>{if(this.selected===row)this.policyError=errorMessage(e);}});}this.selected=row;if(row&&this.config.paid)this.loadRules(row);this.changeReason='';this.mode=row?'detail':'create';this.error='';this.message='';this.body=row?{...row}:{[this.config.code]:'',[this.config.name]:'',description:'',isActive:true,isPaid:false};}
 cancelEdit(){if(this.saving)return;if(this.selected){this.body={...this.selected};this.mode='detail';}else this.close();}
 close(){if(!this.saving){this.mode='';this.selected=null;}}
 save(form:NgForm){if(this.selected&&!this.changeReason.trim()){this.error="Nhập lý do thay đổi.";return;}if(this.saving||form.invalid)return;this.saving=true;this.error='';const body:CatalogRow={[this.config.code]:String(this.body[this.config.code]).trim(),[this.config.name]:String(this.body[this.config.name]).trim(),isActive:this.body['isActive']};if(this.config.paid)body['isPaid']=this.body['isPaid'];else body['description']=this.body['description']||null;if(this.config.path==='qualification-levels')body['rankOrder']=this.body['rankOrder']??null;
 this.api.save(this.config.path,this.selected?Number(this.selected[this.config.id]):null,body,this.changeReason).pipe(takeUntilDestroyed(this.destroy),finalize(()=>this.saving=false)).subscribe({next:row=>{this.saving=false;this.open(row);this.message='Đã lưu danh mục.';this.load(this.page);},error:e=>this.error=errorMessage(e)});}
 savePolicy(){if(this.consumes!==null&&this.consumes===this.savedConsumes){this.policyError='Chưa có thay đổi. Hãy cập nhật thông tin trước khi lưu.';return;}if(!this.changeReason.trim()){this.policyError="Nhập lý do thay đổi.";return;}if(!this.admin||this.saving||!this.selected||this.consumes===null)return;this.saving=true;this.policyError='';this.api.savePolicy(Number(this.selected[this.config.id]),this.consumes,this.changeReason).pipe(takeUntilDestroyed(this.destroy),finalize(()=>this.saving=false)).subscribe({next:()=>{this.savedConsumes=this.consumes;this.message='Đã cập nhật chính sách trừ quỹ phép.';},error:e=>this.policyError=errorMessage(e)});}
 async deactivate(){if(!this.changeReason.trim()){this.error="Nhập lý do thay đổi.";return;}if(this.saving||!this.selected||!(await this.confirmAction('Ngừng sử dụng danh mục này? Lịch sử vẫn được giữ.')))return;this.runCommand(this.api.deactivate(this.config.path,Number(this.selected[this.config.id]),this.changeReason),()=>{this.close();this.load(this.page);},'Đã ngừng sử dụng.');}

}
