import {AfterViewInit,Directive,ElementRef,Input,OnChanges,OnDestroy,inject} from '@angular/core';
import {NoticeKind,ToastService} from '../../services/toast.service';
/** Result banners publish only a toast; field validation uses its own markup. */
@Directive({selector:'[hrmNotice]',standalone:true,host:{style:'display:none !important','aria-hidden':'true'}})
export class HrmNotice implements OnChanges,AfterViewInit,OnDestroy {
 private toast=inject(ToastService);
 private element:ElementRef<HTMLElement>=inject(ElementRef);private destroyed=false;
 @Input() hrmNotice='';@Input() noticeKind:NoticeKind='error';
 private publish(){
  if(this.destroyed)return;
  const retry=this.element.nativeElement.querySelector<HTMLButtonElement>('button');
  this.toast.publish(this,this.noticeKind,this.hrmNotice,retry?{label:retry.textContent?.trim()||'Thử lại',run:()=>retry.click(),disabled:()=>retry.disabled}:undefined);
 }
 ngOnChanges(){this.publish();}
 ngAfterViewInit(){queueMicrotask(()=>this.publish());}
 ngOnDestroy(){this.destroyed=true;this.toast.clear(this);}
}
