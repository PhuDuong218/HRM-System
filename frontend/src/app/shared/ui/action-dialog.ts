import {Component,ElementRef,Injectable,ViewChild,effect,inject,signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {TranslatePipe} from '../translate.pipe';
interface ActionState {message:string;prompt:boolean;}
@Injectable({providedIn:'root'})
export class ActionDialogService {
 readonly state=signal<ActionState|null>(null);
 private resolve:((result:boolean|string|null)=>void)|null=null;
 private open(message:string,prompt:boolean):Promise<boolean|string|null>{
  if(this.resolve)return Promise.resolve(null);
  return new Promise(resolve=>{this.resolve=resolve;this.state.set({message,prompt});});
 }
 confirm(message:string):Promise<boolean>{return this.open(message,false).then(result=>result===true);}
 prompt(message:string):Promise<string|null>{return this.open(message,true).then(result=>typeof result==='string'?result:null);}
 finish(result:boolean|string|null){const resolve=this.resolve;this.resolve=null;this.state.set(null);resolve?.(result);}
}
@Component({selector:'hrm-action-dialog',standalone:true,imports:[FormsModule,TranslatePipe],
 template:`<dialog #dialog data-hrm-confirmation aria-labelledby="action-dialog-title" aria-describedby="action-dialog-description" (cancel)="$event.preventDefault();cancel()"><form novalidate (ngSubmit)="accept()"><header><span class="dialog-icon" aria-hidden="true">!</span><h2 id="action-dialog-title">{{(service.state()?.prompt?'Nhập lý do':'Xác nhận thao tác')|translate}}</h2><button type="button" class="close-action" (click)="cancel()" [attr.aria-label]="'Đóng'|translate">×</button></header><section><p id="action-dialog-description">{{service.state()?.message||''|translate}}</p>@if(service.state()?.prompt){<label for="action-dialog-reason">{{'Lý do'|translate}} <span aria-hidden="true">*</span></label><textarea id="action-dialog-reason" name="reason" [(ngModel)]="reason" rows="3" maxlength="1000" required [attr.aria-invalid]="attempted&&!reason.trim()"></textarea>@if(attempted&&!reason.trim()){<small class="field-error">{{'Vui lòng nhập lý do.'|translate}}</small>}}</section><footer><button #cancelButton type="button" class="btn btn-outline-secondary" (click)="cancel()">{{'Hủy'|translate}}</button><button type="submit" class="btn btn-primary">{{'Xác nhận'|translate}}</button></footer></form></dialog>`,
 styles:[`dialog{margin:auto;width:min(520px,calc(100dvw - 32px));max-height:calc(100dvh - 32px);overflow:auto;padding:0;border:1px solid var(--hrm-border-color,#dde5ef);border-radius:16px;background:var(--hrm-surface,#fff);color:var(--hrm-text,#17364f);box-shadow:0 24px 80px #102a4355}dialog::backdrop{background:#102a4370}header{display:flex;align-items:center;gap:12px;padding:22px 24px;border-bottom:1px solid var(--bs-border-color,#e4eaf2)}h2{flex:1;margin:0;font-size:20px;font-weight:700}.dialog-icon{display:grid;place-items:center;width:36px;height:36px;border-radius:50%;background:#fff1d3;color:#8a5b00;font-weight:700}.close-action{border:0;background:transparent;color:inherit;font-size:26px;width:36px;height:36px}section{padding:24px}p{white-space:pre-line;overflow-wrap:anywhere;line-height:1.6;margin:0}label{display:block;margin:18px 0 8px;font-weight:600}textarea{display:block;width:100%;padding:12px;border:1px solid #cbd7e6;border-radius:8px;background:inherit;color:inherit}textarea[aria-invalid=true]{border-color:#ba3030}.field-error{display:block;color:var(--hrm-danger-text,#a62d2d);margin-top:6px}footer{display:flex;justify-content:flex-end;gap:10px;padding:18px 24px;border-top:1px solid var(--bs-border-color,#e4eaf2)}footer button{min-width:100px}@media(max-width:575px){header,section,footer{padding:18px}footer button{flex:1}}`]
})
export class ActionDialog {
 readonly service=inject(ActionDialogService);reason='';attempted=false;
 @ViewChild('dialog',{static:true}) dialog!:ElementRef<HTMLDialogElement>;
 @ViewChild('cancelButton',{static:true}) cancelButton!:ElementRef<HTMLButtonElement>;
 private previous:HTMLElement|null=null;
 constructor(){effect(()=>{const state=this.service.state();queueMicrotask(()=>{const dialog=this.dialog?.nativeElement;if(!dialog)return;if(state&&!dialog.open){this.reason='';this.attempted=false;this.previous=document.activeElement as HTMLElement;dialog.showModal();this.cancelButton.nativeElement.focus();}else if(!state&&dialog.open){dialog.close();if(this.previous?.isConnected)this.previous.focus();}});});}
 accept(){this.attempted=true;if(this.service.state()?.prompt){if(!this.reason.trim())return;this.service.finish(this.reason.trim());}else this.service.finish(true);}
 cancel(){this.service.finish(null);}
}
