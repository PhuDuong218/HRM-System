import {Injectable,inject} from '@angular/core';
import {CanDeactivateFn} from '@angular/router';
import {LanguageService} from './language.service';
import {ConfirmationService} from '../shared/ui/confirmation';
@Injectable({providedIn:'root'})
export class PendingEditorService {
 private language=inject(LanguageService);
 private confirmation=inject(ConfirmationService);
 private approved=false;
 private busyOwners=new Set<object>();
 setBusy(owner:object,busy:boolean){if(busy)this.busyOwners.add(owner);else this.busyOwners.delete(owner);}
 private dirtyEditor():Element|null{
  const controls=document.querySelectorAll('.ng-dirty');
  for(const control of Array.from(controls)){
   if(control.closest('.filter-bar,.filters,.contract-filters,.report-filters'))continue;
   // The footer may sit outside <form> while its submit button uses form="id".
   // Treat the whole panel/dialog as one task, not only the nearest form.
   const editor=control.closest('.record-detail,dialog[open]')??control.closest('form');
   if(editor&&editor.getClientRects().length)return editor;
  }
  return null;
 }
 constructor(){
  document.addEventListener('input',()=>this.approved=false,true);
  document.addEventListener('change',()=>this.approved=false,true);
  document.addEventListener('click',event=>{
   const editor=this.dirtyEditor(),target=event.target as Element|null;
   if(!target||target.closest('[data-hrm-confirmation]'))return;
   if(this.busyOwners.size&&!target.closest('.record-detail')&&target.closest('button,a,[tabindex="0"]')){event.preventDefault();event.stopImmediatePropagation();void this.allow(true);return;}
   if(!editor||this.approved)return;
   const closing=target.closest('.panel-toolbar button,.record-backdrop,.profile-close')||(/^(Hủy|Cancel|Đóng|Close)$/.test(target.closest('button')?.textContent?.trim()||''));
   const submit=target.closest<HTMLButtonElement>('button[type="submit"],input[type="submit"]');
   if(!closing&&submit?.form&&editor.contains(submit.form))return;
   if((!editor.contains(target)||closing)&&target.closest('button,a,[tabindex="0"]')){
    event.preventDefault();event.stopImmediatePropagation();
    const action=target.closest<HTMLElement>('button,a,[tabindex="0"]');
    void this.allow().then(allowed=>{if(allowed&&action?.isConnected)action.click();});
   }
  },true);
  document.addEventListener('keydown',event=>{if((event.target as Element|null)?.closest?.('[data-hrm-confirmation]'))return;if(event.key==='Escape'&&this.dirtyEditor()){event.preventDefault();event.stopImmediatePropagation();void this.allow().then(allowed=>{if(allowed)document.querySelector<HTMLButtonElement>('.record-detail .panel-toolbar button')?.click();});}},true);
  window.addEventListener('beforeunload',event=>{if(this.dirtyEditor()&&!this.approved){event.preventDefault();event.returnValue='';}});
 }
 async allow(saving=false):Promise<boolean>{
  if(saving||this.busyOwners.size){await this.confirmation.ask(true);return false;}
  if(this.approved||!this.dirtyEditor())return true;
  this.approved=await this.confirmation.ask(false);
  return this.approved;
 }
}
export const pendingEditorGuard:CanDeactivateFn<unknown>=(component)=>inject(PendingEditorService).allow(!!(component as {saving?:boolean})?.saving);
