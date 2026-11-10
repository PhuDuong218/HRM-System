import {Component,DestroyRef,EventEmitter,Input,OnChanges,OnDestroy,Output,inject} from '@angular/core';
import {DatePipe} from '@angular/common';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Subscription} from 'rxjs';
import {PersonnelWorkspaceService} from '../services/personnel-workspace.service';
import {ResignationReadiness} from '../models/resignation-readiness.model';
import {TranslatePipe} from './translate.pipe';
import {Feedback} from './ui/feedback';
import {errorMessage} from '../services/employee.service';
@Component({selector:'app-resignation-review',standalone:true,imports:[DatePipe,TranslatePipe,Feedback],templateUrl:'./resignation-review.html',styleUrl:'./resignation-review.css'})
export class ResignationReview implements OnChanges,OnDestroy {
 @Input({required:true}) requestId!:number;@Input() lastWorkingDate='';@Input() refreshToken=0;@Input() busy=false;
 @Output() readinessChange=new EventEmitter<boolean>();
 @Output() processRequest=new EventEmitter<{route:string;employeeId:number;employeeLabel:string}>();
 private api=inject(PersonnelWorkspaceService);private destroy=inject(DestroyRef);private request?:Subscription;private version=0;
 result:ResignationReadiness|null=null;loading=false;error='';checkedAt:Date|null=null;
 ngOnChanges(changes:Record<string,unknown>){if(changes['requestId']||changes['lastWorkingDate']||changes['refreshToken'])this.check();}
 check(){
  this.request?.unsubscribe();const version=++this.version;this.result=null;this.checkedAt=null;this.error='';this.loading=false;this.readinessChange.emit(false);
  if(!this.requestId||!/^\d{4}-\d{2}-\d{2}$/.test(this.lastWorkingDate))return;
  this.loading=true;
  this.request=this.api.resignationReadiness(this.requestId,this.lastWorkingDate).pipe(takeUntilDestroyed(this.destroy)).subscribe({
   next:r=>{if(version!==this.version)return;this.result=r;this.checkedAt=new Date();this.loading=false;this.readinessChange.emit(r.canApprove);},
   error:e=>{if(version!==this.version)return;this.error=errorMessage(e);this.loading=false;}
  });
 }
 recordDate(value:string){const date=value.slice(0,10).split('-').reverse().join('/');return date+(value.length>10?' '+value.slice(11,16):'');}
 readonly allowedRoutes=new Set(['/payroll','/departments','/schedules','/contracts','/assignments','/requests','/attendance']);
 openModule(route:string){if(!this.busy&&this.result&&this.allowedRoutes.has(route))this.processRequest.emit({route,employeeId:this.result.employeeId,employeeLabel:this.result.employeeCode+' — '+this.result.employeeName});}
 ngOnDestroy(){++this.version;this.request?.unsubscribe();}
}
