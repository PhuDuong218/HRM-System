import {Injectable,signal} from '@angular/core';
export type NoticeKind='success'|'error';
export interface ToastAction {label:string;run:()=>void;disabled:()=>boolean;}
export interface ToastNotice {id:number;owner:object;kind:NoticeKind;text:string;action?:ToastAction;}
@Injectable({providedIn:'root'})
export class ToastService {
 readonly notices=signal<ToastNotice[]>([]);
 private sequence=0;private timers=new Map<number,ReturnType<typeof setTimeout>>();
 publish(owner:object,kind:NoticeKind,text:string,action?:ToastAction){
  this.clear(owner,kind);if(!text?.trim())return;
  if(this.notices().some(row=>row.kind===kind&&row.text===text.trim()))return;
  const notice={id:++this.sequence,owner,kind,text:text.trim(),action};
  const next=[notice,...this.notices()].sort((a,b)=>a.kind===b.kind?b.id-a.id:a.kind==='error'?-1:1);
  for(const removed of next.splice(3))this.stopTimer(removed.id);this.notices.set(next);
  if(next.includes(notice))this.timers.set(notice.id,setTimeout(()=>this.dismiss(notice.id),5000));
 }
 dismiss(id:number){this.stopTimer(id);this.notices.update(rows=>rows.filter(row=>row.id!==id));}
 clear(owner:object,kind?:NoticeKind){const removed=this.notices().filter(row=>row.owner===owner&&(!kind||row.kind===kind));for(const row of removed)this.stopTimer(row.id);this.notices.update(rows=>rows.filter(row=>!removed.includes(row)));}
 private stopTimer(id:number){clearTimeout(this.timers.get(id));this.timers.delete(id);}
}
