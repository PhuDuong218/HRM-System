import {Injectable,signal} from '@angular/core';
export interface ExitWorkflowDraft {owner:string;requestId:number;lastWorkingDate:string;note:string;employeeLabel:string;savedAt:number;}
/** Same-tab handoff: no JWT sharing or private notes in URLs/storage. */
@Injectable({providedIn:'root'})
export class ExitWorkflowService {
 private draft=signal<ExitWorkflowDraft|null>(null);
 save(value:Omit<ExitWorkflowDraft,'savedAt'>){this.draft.set({...value,savedAt:Date.now()});}
 read(owner:string,requestId?:number){const value=this.draft();return value&&owner&&value.owner===owner&&Date.now()-value.savedAt<30*60*1000&&(!requestId||value.requestId===requestId)?value:null;}
 clear(requestId?:number){if(!requestId||this.draft()?.requestId===requestId)this.draft.set(null);}
}
