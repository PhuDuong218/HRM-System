import {changeReasonHeaders} from './auth.interceptor';
import {Injectable,inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {timeout} from 'rxjs';
import {API_BASE_URL} from './api.config';
export interface CatalogRow {[key:string]:string|number|boolean|null;}
export type CatalogPage=import('../models/page.model').SpringPage<CatalogRow>;
@Injectable({providedIn:'root'}) export class CatalogService {
 private http=inject(HttpClient);
 departments(){return this.http.get<CatalogRow[]>(`${API_BASE_URL}/departments`).pipe(timeout(15000));}
 accrual(id:number){return this.http.get<Record<string,any>>(`${API_BASE_URL}/employee-requests/leave-accrual/${id}`).pipe(timeout(15000));}
 saveAccrual(id:number,body:{annualDays:number;hoursPerDay:number;seniorityExtra:boolean;departmentId:number|null},reason=""){return this.http.put(`${API_BASE_URL}/employee-requests/leave-accrual/${id}`,body,{headers:changeReasonHeaders(reason)}).pipe(timeout(20000));}
 generate(id:number,body:{periodStart:string;periodEnd:string;reason:string}){return this.http.post<{employeeCode:string;result:string;hours?:number}[]>(`${API_BASE_URL}/employee-requests/leave-accrual/${id}/generate`,body).pipe(timeout(60000));}

 rules(id:number){return this.http.get<{retroactiveDays:number;minimumNoticeHours:number;maximumHours:number|null;minimumServiceMonths:number;evidenceRequired:boolean}>(`${API_BASE_URL}/employee-requests/leave-rules/${id}`).pipe(timeout(15000));}
 saveRules(id:number,body:{retroactiveDays:number;minimumNoticeHours:number;maximumHours:number|null;minimumServiceMonths:number;evidenceRequired:boolean},reason=""){return this.http.put(`${API_BASE_URL}/employee-requests/leave-rules/${id}`,body,{headers:changeReasonHeaders(reason)}).pipe(timeout(20000));}
 policy(id:number){return this.http.get<{consumesEntitlement:boolean}>(`${API_BASE_URL}/employee-requests/leave-policies/${id}`).pipe(timeout(15000));}
 savePolicy(id:number,consumesEntitlement:boolean,reason=""){return this.http.put<void>(`${API_BASE_URL}/employee-requests/leave-policies/${id}`,{consumesEntitlement},{headers:changeReasonHeaders(reason)}).pipe(timeout(20000));}
 list(path:string,page:number,keyword='',active=''){return this.http.get<CatalogPage>(`${API_BASE_URL}/${path}`,{params:{page,size:10,...(keyword?{keyword}:{}),...(active?{active}:{})}}).pipe(timeout(15000));}
 save(path:string,id:number|null,body:CatalogRow,reason=""){return (id===null?this.http.post<CatalogRow>(`${API_BASE_URL}/${path}`,body,{headers:changeReasonHeaders(reason)}):this.http.put<CatalogRow>(`${API_BASE_URL}/${path}/${id}`,body,{headers:changeReasonHeaders(reason)})).pipe(timeout(20000));}
 deactivate(path:string,id:number,reason=""){return this.http.delete(`${API_BASE_URL}/${path}/${id}`,{responseType:'text',headers:changeReasonHeaders(reason)}).pipe(timeout(20000));}
}
