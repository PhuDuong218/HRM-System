import {Component,inject} from '@angular/core';
import {Router,RouterLink} from '@angular/router';
import {AuthService} from '../services/auth.service';
import {ExitWorkflowService} from '../services/exit-workflow.service';
import {TranslatePipe} from './translate.pipe';
@Component({selector:'app-exit-workflow-banner',standalone:true,imports:[RouterLink,TranslatePipe],
 template:`@if(context();as c){@if(!inRequests()){<aside><div><strong>{{'Đang đối soát nghỉ việc'|translate}}</strong><span>{{c.employeeLabel}}</span></div><a class="btn btn-outline-primary" [routerLink]="'/requests'" [queryParams]="{resumeExit:c.requestId}">{{'Quay lại đơn nghỉ việc'|translate}}</a></aside>}}`,
 styles:[`aside{display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:20px;padding:16px;border:1px solid var(--bs-border-color,#dce5ef);border-left:4px solid #004581;border-radius:10px;background:var(--hrm-surface,#fff)}div{display:grid;gap:4px;min-width:0}strong{font-size:14px}span{font-size:13px;overflow-wrap:anywhere}`]})
export class ExitWorkflowBanner {
 private flow=inject(ExitWorkflowService);private auth=inject(AuthService);private router=inject(Router);
 context(){return this.flow.read(this.auth.profile()?.username||'');}
 inRequests(){return this.router.url.split('?')[0]==='/requests'&&this.router.parseUrl(this.router.url).queryParams['resumeExit']===String(this.context()?.requestId);}
}
