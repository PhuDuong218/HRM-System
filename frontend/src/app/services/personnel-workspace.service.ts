import {inject,Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {EMPTY,expand,reduce,timeout} from 'rxjs';
import {API_BASE_URL} from './api.config';
export type PersonnelRow=Record<string,any>;
export interface PersonnelPage {content:PersonnelRow[];totalPages:number;totalElements:number;}
@Injectable({providedIn:'root'}) export class PersonnelWorkspaceService {
 private http=inject(HttpClient);
 page(path:string,params:Record<string,string|number>={}){return this.http.get<PersonnelPage>(API_BASE_URL+path,{params}).pipe(timeout(15000));}
 get(path:string){return this.http.get<PersonnelRow>(API_BASE_URL+path).pipe(timeout(15000));}
 employees(){return this.http.get<PersonnelRow[]>(API_BASE_URL+'/employees').pipe(timeout(15000));}
 options(path:string){return this.page(path,{page:0,size:100}).pipe(expand((r,i)=>i+1<r.totalPages?this.page(path,{page:i+1,size:100}):EMPTY),reduce((all,r)=>[...all,...r.content],[] as PersonnelRow[]));}
 write(method:string,path:string,body:PersonnelRow={}){return this.http.request<PersonnelRow>(method,API_BASE_URL+path,{body}).pipe(timeout(20000));}
}
