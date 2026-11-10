import {inject,Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {timeout} from 'rxjs';
import {API_BASE_URL} from './api.config';
export interface HeadRequest {requestId:number;requestCode:string;employeeId:number;employeeCode:string;employeeName:string;requestType:string;status:string;submittedAt:string;reason:string;stageStatus:string;decidedBy?:string;decidedAt?:string;note?:string;withdrawnAt?:string;}
export interface HeadAudit {auditId:number;action:string;actor:string;occurredAt:string;reason?:string;beforeJson?:string;afterJson?:string;}
export interface HeadRequestDetail {reason?:string;leaveStart?:string;leaveEnd?:string;requestedLastWorkingDate?:string;status?:string;withdrawnAt?:string;evidenceReference?:string;approvalStage?:{stageStatus:string;decidedBy?:number;decidedAt?:string;note?:string};canCancelLeave?:boolean;cancelLeaveBlocker?:string;withdrawnBy?:string;withdrawalReason?:string;history:HeadAudit[];}
export interface HeadFilters {stageStatus:string;status:string;type:string;keyword:string;fromDate:string;toDate:string;}
@Injectable({providedIn:'root'})
export class HeadRequestsService {
 private http=inject(HttpClient);
 capabilities(){return this.http.get<{departmentHead:boolean}>(`${API_BASE_URL}/employee-requests/capabilities`).pipe(timeout(15000));}
 list(page=0,filters:HeadFilters={stageStatus:'PENDING',status:'',type:'',keyword:'',fromDate:'',toDate:''}){const params:Record<string,string|number>={page,size:10};for(const [key,value] of Object.entries(filters))if(value)params[key]=value;return this.http.get<import('../models/page.model').SpringPage<HeadRequest>>(`${API_BASE_URL}/employee-requests/head-search`,{params}).pipe(timeout(15000));}
 detail(id:number){return this.http.get<HeadRequestDetail>(`${API_BASE_URL}/employee-requests/head-detail/${id}`).pipe(timeout(15000));}
 cancel(id:number,reason:string){return this.http.post(`${API_BASE_URL}/employee-requests/${id}/cancel-leave`,{reason}).pipe(timeout(20000));}
 decide(id:number,approve:boolean,note:string){return this.http.post(`${API_BASE_URL}/employee-requests/${id}/head-decision`,{approve,note}).pipe(timeout(20000));}
}
