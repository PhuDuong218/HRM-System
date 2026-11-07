import {EmployeeAssignmentDTO,EmployeeQualificationDTO} from '../models/personnel.model';
import {ResignationReadiness} from '../models/resignation-readiness.model';
import {EmployeeService} from './employee.service';
import {inject,Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {EMPTY,expand,reduce,timeout} from 'rxjs';
import {API_BASE_URL} from './api.config';
export type PersonnelRow=Record<string,any>;
export type PersonnelPage=import('../models/page.model').SpringPage<PersonnelRow>;
@Injectable({providedIn:'root'}) export class PersonnelWorkspaceService {
 private http=inject(HttpClient);
 private employeeApi=inject(EmployeeService);
 page<T extends object=PersonnelRow>(path:string,params:Record<string,string|number>={}){return this.http.get<import('../models/page.model').SpringPage<T>>(API_BASE_URL+path,{params}).pipe(timeout(15000));}
 assignments(params:Record<string,string|number>){return this.page<EmployeeAssignmentDTO>('/employee-assignments/search',params);}
 qualifications(params:Record<string,string|number>){return this.page<EmployeeQualificationDTO>('/employee-qualifications/search',params);}
 get(path:string){return this.http.get<PersonnelRow>(API_BASE_URL+path).pipe(timeout(15000));}
 resignationReadiness(id:number,lastWorkingDate:string){return this.http.get<ResignationReadiness>(`${API_BASE_URL}/employee-requests/${id}/resignation-readiness`,{params:{lastWorkingDate}}).pipe(timeout(15000));}
 employees(){return this.employeeApi.all();}
 options(path:string){return this.page(path,{page:0,size:100}).pipe(expand((r,i)=>i+1<r.totalPages?this.page(path,{page:i+1,size:100}):EMPTY),reduce((all,r)=>[...all,...r.content],[] as PersonnelRow[]));}
 write(method:string,path:string,body:PersonnelRow={}){return this.http.request<PersonnelRow>(method,API_BASE_URL+path,{body}).pipe(timeout(20000));}
}
