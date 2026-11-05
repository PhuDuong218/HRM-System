import {Component,Input,OnDestroy,signal} from '@angular/core';
import {HrmButton} from './button';
import {HrmIcon} from './icon';
import {TranslatePipe} from '../translate.pipe';
/** Only presentation changes: the caller still owns API errors and success messages. */
@Component({selector:'hrm-feedback',standalone:true,imports:[HrmButton,HrmIcon,TranslatePipe],
template:`<div class="notices">@if(errorText()){<div class="notice error" role="alert"><hrm-icon name="alert-triangle"/><p>{{errorText()|translate}}</p><button type="button" hrmBtn="ghost" hrmSize="sm" [hrmIconOnly]="true" (click)="errorText.set('')" [attr.aria-label]="'Đóng'|translate" [attr.title]="'Đóng'|translate"><hrm-icon name="x"/></button></div>}@if(successText()){<div class="notice success" role="status"><hrm-icon name="check"/><p>{{successText()|translate}}</p><button type="button" hrmBtn="ghost" hrmSize="sm" [hrmIconOnly]="true" (click)="successText.set('')" [attr.aria-label]="'Đóng'|translate" [attr.title]="'Đóng'|translate"><hrm-icon name="x"/></button></div>}</div>`,
styles:[`.notices{position:fixed;inset-block-start:calc(var(--hrm-topbar) + var(--hrm-space-4));inset-inline-end:var(--hrm-space-4);z-index:var(--hrm-z-toast);display:grid;gap:var(--hrm-space-2);width:min(28rem,calc(100dvw - var(--hrm-space-6)));pointer-events:none}.notice{pointer-events:auto;display:flex;align-items:center;gap:var(--hrm-space-3);padding:var(--hrm-space-3);border:var(--hrm-border);border-radius:var(--hrm-radius-md);box-shadow:var(--hrm-shadow-2)}p{flex:1;min-width:0;margin:0;overflow-wrap:anywhere;font-size:var(--hrm-font-md)}.error{background:var(--hrm-danger-bg);color:var(--hrm-danger-text)}.success{background:var(--hrm-success-bg);color:var(--hrm-success-text)}`]})
export class Feedback implements OnDestroy {
 errorText=signal('');successText=signal('');private timer?:ReturnType<typeof setTimeout>;
 @Input() set error(value:string){this.errorText.set(value);}
 @Input() set success(value:string){clearTimeout(this.timer);this.successText.set(value);if(value)this.timer=setTimeout(()=>this.successText.set(''),4000);}
 ngOnDestroy(){clearTimeout(this.timer);}
}
