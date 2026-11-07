import {Component,ElementRef,afterRenderEffect,inject,viewChild} from '@angular/core';
import {ToastService} from '../../services/toast.service';
import {HrmIcon} from './icon';
import {TranslatePipe} from '../translate.pipe';
@Component({selector:'hrm-toast-outlet',standalone:true,imports:[HrmIcon,TranslatePipe],
 template:`<div #stack class="toast-stack" popover="manual" data-hrm-notification>@for(notice of toast.notices();track notice.id){<article class="toast-card" [class.is-error]="notice.kind==='error'" [attr.role]="notice.kind==='error'?'alert':'status'" aria-atomic="true"><span class="toast-icon"><hrm-icon [name]="notice.kind==='error'?'alert-triangle':'check'"/></span><div class="toast-content"><strong>{{(notice.kind==='error'?'Không thể hoàn tất':'Thành công')|translate}}</strong><p>{{notice.text|translate}}</p>@if(notice.action;as action){<button class="toast-action" type="button" [disabled]="action.disabled()" (click)="action.run()">{{action.label|translate}}</button>}</div><button type="button" (click)="toast.dismiss(notice.id)" [attr.aria-label]="'Đóng thông báo'|translate"><hrm-icon name="x"/></button></article>}</div>`,
 styleUrl:'./toast-outlet.css'})
export class ToastOutlet {
 readonly toast=inject(ToastService);private stack=viewChild<ElementRef<HTMLElement>>('stack');
 constructor(){afterRenderEffect(()=>{
  const element=this.stack()?.nativeElement;if(!element)return;
  // The top layer keeps errors visible even above a native profile/password dialog.
  if(typeof element.showPopover!=='function'){element.removeAttribute('popover');return;}
  if(this.toast.notices().length){if(!element.matches(':popover-open'))element.showPopover();}
  else if(element.matches(':popover-open'))element.hidePopover();
 });}
}
