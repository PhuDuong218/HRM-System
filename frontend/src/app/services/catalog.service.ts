import {Injectable,inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {timeout} from 'rxjs';
import {API_BASE_URL} from './api.config';
export interface CatalogRow {[key:string]:string|number|boolean|null;}
export interface CatalogPage {content:CatalogRow[];totalPages:number;totalElements:number;}
@Injectable({providedIn:'root'}) export class CatalogService {
 private http=inject(HttpClient);
 policy(id:number){return this.http.get<{consumesEntitlement:boolean}>(`${API_BASE_URL}/employee-requests/leave-policies/${id}`).pipe(timeout(15000));}
 savePolicy(id:number,consumesEntitlement:boolean){return this.http.put<void>(`${API_BASE_URL}/employee-requests/leave-policies/${id}`,{consumesEntitlement}).pipe(timeout(20000));}
 list(path:string,page:number,keyword='',active=''){return this.http.get<CatalogPage>(`${API_BASE_URL}/${path}`,{params:{page,size:10,...(keyword?{keyword}:{}),...(active?{active}:{})}}).pipe(timeout(15000));}
 save(path:string,id:number|null,body:CatalogRow){return (id===null?this.http.post<CatalogRow>(`${API_BASE_URL}/${path}`,body):this.http.put<CatalogRow>(`${API_BASE_URL}/${path}/${id}`,body)).pipe(timeout(20000));}
 deactivate(path:string,id:number){return this.http.delete(`${API_BASE_URL}/${path}/${id}`,{responseType:'text'}).pipe(timeout(20000));}
}
