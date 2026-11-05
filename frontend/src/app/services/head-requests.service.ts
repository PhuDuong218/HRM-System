import {inject,Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {timeout} from 'rxjs';
import {API_BASE_URL} from './api.config';
export interface HeadRequest {requestId:number;requestCode:string;employeeId:number;employeeCode:string;employeeName:string;requestType:string;status:string;submittedAt:string;reason:string;stageStatus:string;}
export interface HeadRequestDetail {reason?:string;leaveStart?:string;leaveEnd?:string;requestedLastWorkingDate?:string;}
@Injectable({providedIn:'root'})
export class HeadRequestsService {
 private http=inject(HttpClient);
 capabilities(){return this.http.get<{departmentHead:boolean}>(`${API_BASE_URL}/employee-requests/capabilities`).pipe(timeout(15000));}
 list(page=0){return this.http.get<import('../models/page.model').SpringPage<HeadRequest>>(`${API_BASE_URL}/employee-requests/head-queue`,{params:{page,size:10}}).pipe(timeout(15000));}
 detail(id:number){return this.http.get<HeadRequestDetail>(`${API_BASE_URL}/employee-requests/${id}`).pipe(timeout(15000));}
 decide(id:number,approve:boolean,note:string){return this.http.post(`${API_BASE_URL}/employee-requests/${id}/head-decision`,{approve,note}).pipe(timeout(20000));}
}
