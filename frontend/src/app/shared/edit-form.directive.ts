import {afterNextRender,Directive,ElementRef,Injector,Input,OnChanges,OnDestroy,inject} from '@angular/core';
import {ToastService} from '../services/toast.service';

export interface EditControlValue {key:string;value:string|boolean;}
export function unchangedForm(a:EditControlValue[],b:EditControlValue[]):boolean {
 return JSON.stringify(a)===JSON.stringify(b);
}
/** One form contract: reveal the editor, retain its baseline, block no-op updates. */
@Directive({selector:'form[hrmEditForm]',standalone:true})
export class EditFormDirective implements OnChanges,OnDestroy {
 @Input() hrmEditForm=false;
 @Input() hrmEditReady=true;
 @Input() hrmEditReveal=true;
 @Input() hrmEditSaved=0;
 private element:ElementRef<HTMLFormElement>=inject(ElementRef);private injector=inject(Injector);private toast=inject(ToastService);
 private baseline:EditControlValue[]|null=null;private cancel:(()=>void)|null=null;
 private refresh=()=>{
  const unchanged=this.hrmEditForm&&!!this.baseline&&unchangedForm(this.baseline,this.snapshot());
  this.element.nativeElement.toggleAttribute('data-hrm-unchanged',unchanged);
 };
 private submit=(event:Event)=>{
  if(!this.hrmEditForm)return;
  if(!this.hrmEditReady||!this.baseline){event.preventDefault();event.stopImmediatePropagation();return;}
  if(unchangedForm(this.baseline,this.snapshot())){
   event.preventDefault();event.stopImmediatePropagation();
   this.toast.publish(this,'error','Chưa có thay đổi. Hãy cập nhật thông tin trước khi lưu.');
  }
 };
 constructor(){const form=this.element.nativeElement;form.addEventListener('submit',this.submit,true);form.addEventListener('input',this.refresh);form.addEventListener('change',this.refresh);}
 ngOnChanges(){
  this.cancel?.();this.baseline=null;if(!this.hrmEditReady)return;
  const pending=afterNextRender(()=>{
   const form=this.element.nativeElement;if(!form.isConnected)return;
   this.baseline=this.snapshot();this.refresh();
   form.setAttribute('tabindex','-1');form.setAttribute('data-hrm-editor','');
   if(this.hrmEditReveal&&form.getClientRects().length){form.style.scrollMarginTop='96px';form.scrollIntoView({block:'start',inline:'nearest',behavior:'instant'});form.focus({preventScroll:true});}
  },{injector:this.injector});this.cancel=()=>pending.destroy();
 }
 private snapshot():EditControlValue[]{
  return Array.from(this.element.nativeElement.querySelectorAll<HTMLInputElement|HTMLSelectElement|HTMLTextAreaElement>('input,select,textarea'))
   .filter(c=>!c.closest('[data-hrm-audit]')&&c.type!=='hidden'&&!/changeReason/i.test(c.name||c.id))
   .map((c,index)=>({key:c.name||c.id||String(index),value:c.type==='checkbox'||c.type==='radio'?(c as HTMLInputElement).checked:c.type==='number'&&c.value.trim()!==''?String(Number(c.value)):c.value.trim()}));
 }
 ngOnDestroy(){this.cancel?.();const form=this.element.nativeElement;form.removeEventListener('submit',this.submit,true);form.removeEventListener('input',this.refresh);form.removeEventListener('change',this.refresh);this.toast.clear(this);}
}
