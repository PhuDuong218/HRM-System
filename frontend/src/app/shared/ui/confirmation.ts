import {Component,ElementRef,Injectable,ViewChild,effect,inject,signal} from '@angular/core';
import {HrmButton} from './button';
import {HrmIcon} from './icon';
import {TranslatePipe} from '../translate.pipe';
@Injectable({providedIn:'root'})
export class ConfirmationService {
 state=signal<{busy:boolean}|null>(null);
 private pending?:Promise<boolean>;private resolve?:(value:boolean)=>void;
 ask(busy=false):Promise<boolean>{if(this.pending)return this.pending;this.state.set({busy});this.pending=new Promise<boolean>(resolve=>this.resolve=resolve);return this.pending;}
 finish(value:boolean){this.state.set(null);this.resolve?.(value);this.pending=undefined;this.resolve=undefined;}
}
@Component({selector:'hrm-confirmation',standalone:true,imports:[HrmButton,HrmIcon,TranslatePipe],
template:`<dialog #dialog data-hrm-confirmation aria-labelledby="draft-title" aria-describedby="draft-description" (cancel)="$event.preventDefault();finish(false)"><section><div class="icon"><hrm-icon [name]="service.state()?.busy?'clock':'alert-triangle'"/></div><h2 id="draft-title">{{(service.state()?.busy?'Đang xử lý dữ liệu':'Bạn có thay đổi chưa lưu')|translate}}</h2><p id="draft-description">{{(service.state()?.busy?'Vui lòng chờ thao tác hoàn tất. Dữ liệu đang được gửi đến máy chủ.':'Thông tin vừa nhập sẽ không được lưu nếu bạn rời chức năng này.')|translate}}</p></section><footer><button #stay type="button" hrmBtn="secondary" (click)="finish(false)">{{(service.state()?.busy?'Đã hiểu':'Tiếp tục nhập')|translate}}</button>@if(!service.state()?.busy){<button type="button" hrmBtn="danger" (click)="finish(true)">{{'Bỏ thay đổi'|translate}}</button>}</footer></dialog>`,
styles:[`dialog{width:min(28rem,calc(100dvw - var(--hrm-space-6)));max-height:calc(100dvh - var(--hrm-space-6));overflow:auto;padding:0;border:var(--hrm-border);border-radius:var(--hrm-radius-lg);background:var(--hrm-surface);color:var(--hrm-text);box-shadow:var(--hrm-shadow-3)}dialog::backdrop{background:color-mix(in srgb,var(--hrm-text) 45%,transparent)}section{padding:var(--hrm-space-5)}.icon{display:grid;place-items:center;width:var(--hrm-control-lg);height:var(--hrm-control-lg);border-radius:var(--hrm-radius-pill);color:var(--hrm-warning-text);background:var(--hrm-warning-bg);margin-bottom:var(--hrm-space-4)}h2{font:600 var(--hrm-font-section)/1.45 var(--hrm-font);margin:0 0 var(--hrm-space-2)}p{font-size:var(--hrm-font-md);color:var(--hrm-muted);margin:0}footer{display:flex;justify-content:flex-end;flex-wrap:wrap;gap:var(--hrm-space-2);padding:var(--hrm-space-4) var(--hrm-space-5);border-top:var(--hrm-border);background:var(--hrm-subtle)}footer button{min-width:6rem}`]})
export class Confirmation {
 service=inject(ConfirmationService);
 @ViewChild('dialog',{static:true}) dialog!:ElementRef<HTMLDialogElement>;
 @ViewChild('stay',{static:true}) stay!:ElementRef<HTMLButtonElement>;
 private previous:HTMLElement|null=null;
 constructor(){effect(()=>{const state=this.service.state();queueMicrotask(()=>{const d=this.dialog?.nativeElement;if(!d)return;if(state&&!d.open){this.previous=document.activeElement as HTMLElement;d.showModal();this.stay.nativeElement.focus();}else if(!state&&d.open){d.close();if(this.previous?.isConnected)this.previous.focus();}});});}
 finish(value:boolean){this.service.finish(value);}
}
