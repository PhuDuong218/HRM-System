import {Directive,DestroyRef,inject} from '@angular/core';
import {Observable,Subscription,finalize} from 'rxjs';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {LanguageService} from '../services/language.service';
import {errorMessage} from '../services/employee.service';
/** Shared state/lifecycle/confirmation contract for personnel management pages. */
@Directive({host:{class:'management-page'}})
export abstract class ManagementPageBase {
 readonly i18n=inject(LanguageService);
 protected readonly pageLifetime=inject(DestroyRef);
 loading=false;saving=false;error='';message='';
 abstract load(page?:number):void;
 protected confirmAction(message:string):boolean{return confirm(this.i18n.t(message));}
 protected runCommand<T>(request:Observable<T>,success:(result:T)=>void,notice='Đã xử lý thành công.'):Subscription {
  this.saving=true;this.error='';this.message='';
  return request.pipe(takeUntilDestroyed(this.pageLifetime),finalize(()=>this.saving=false)).subscribe({
   next:result=>{this.saving=false;this.message=notice;success(result);},
   error:error=>this.error=errorMessage(error)
  });
 }
}
