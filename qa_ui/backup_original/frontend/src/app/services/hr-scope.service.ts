import {inject,Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {timeout} from 'rxjs';
import {API_BASE_URL} from './api.config';
export interface HrScope {scopeId:number;hrAccountId:number;departmentId:number;effectiveFrom:string;effectiveTo:string|null;revokedAt:string|null;revokedBy:number|null;createdAt:string;createdBy:number;updatedAt:string|null;updatedBy:number|null;}
export interface ScopePeriod {effectiveFrom:string;effectiveTo:string|null;reason:string;}
export interface ScopeDepartment {departmentId:number;departmentCode:string;departmentName:string;isActive:boolean;}
/** Backend LocalDateTime fields in this API are UTC, without a Z suffix. */
export function scopeInstant(value:string):Date {return new Date(/(?:Z|[+-]\d{2}:\d{2})$/i.test(value)?value:value+'Z');}
export function scopeLocalInput(value:string):string {const date=scopeInstant(value);date.setMinutes(date.getMinutes()-date.getTimezoneOffset());return date.toISOString().slice(0,16);}
export function scopeUtcInput(value:string):string {const date=new Date(value);if(!value||!Number.isFinite(date.getTime()))throw new Error('Invalid date');return date.toISOString().slice(0,-1);}
@Injectable({providedIn:'root'}) export class HrScopeService {
 private http=inject(HttpClient);private url=API_BASE_URL+'/admin/hr-scopes';
 all(){return this.http.get<HrScope[]>(this.url).pipe(timeout(15000));}
 departments(){return this.http.get<ScopeDepartment[]>(API_BASE_URL+'/departments').pipe(timeout(15000));}
 grant(hrAccountId:number,departmentId:number,body:ScopePeriod){return this.http.post<HrScope>(this.url,{hrAccountId,departmentId,...body}).pipe(timeout(20000));}
 update(id:number,body:ScopePeriod){return this.http.put<HrScope>(this.url+'/'+id,body).pipe(timeout(20000));}
 revoke(id:number,reason:string){return this.http.post<HrScope>(this.url+'/'+id+'/revoke',{reason}).pipe(timeout(20000));}
}
