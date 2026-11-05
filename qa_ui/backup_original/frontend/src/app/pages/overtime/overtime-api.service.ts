import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { API_BASE_URL } from '../../services/api.config';
export type OtTab = 'employees'|'approval'|'actual'|'other'|'mine';
export interface OtContext { role:string; mine:boolean; manage:boolean; canRegister:boolean; serverNow:string; }
export interface Ot { overtimeId:number; employeeId:number; requestCode:string; requestedStart:string; requestedEnd:string; reason:string; status:string; processedBy:number|null; rejectionReason:string|null; actualStart:string|null; actualEnd:string|null; actualBreakMinutes:number; confirmedBy:number|null; }
export interface OtActions { edit:boolean; cancel:boolean; cancelRequest:boolean; approve:boolean; recordActual:boolean; amendActual:boolean; confirm:boolean; overflowRequest:boolean; reopen:boolean; confirmationReason:string; }
export interface OtRow { ot:Ot; employeeCode:string; fullName:string; department:string|null; role:string|null; workContent:string|null; plannedBreakStatus:string; submittedAt:string; approvedAt:string|null; approver:string|null; confirmer:string|null; version:string; requestedMinutes:number|null; approvedMinutes:number|null; actualMinutes:number|null; validMinutes:number|null; overflowMinutes:number|null; warning:string; actions:OtActions; }
export interface OtStats { employees:number; pending:number; approved:number; rejected:number; needsAction:number; validMinutes:number; unresolved:number; }
export interface OtEmployee { employeeId:number; employeeCode:string; fullName:string; department:string; requests:number; pending:number; approved:number; approvedMinutes:number; validMinutes:number; unresolved:number; needsAction:number; requestTimes:Array<{overtimeId:number;requestCode:string;submittedAt:string;approvedAt:string|null}>; }
export interface OtPage<T> { items:T[]; page:number; totalPages:number; totalElements:number; stats?:OtStats; }
export interface OtBreak { breakStart:string; breakEnd:string; }
export interface OtDay { day:string; requestedMinutes:number|null; actualMinutes:number|null; recognizedMinutes:number|null; regularMinutes:number|null; }
export interface OtSchedule { schedule_id:number; planned_start:string; planned_end:string; planned_break_minutes:number; break_detail_status:string; shift_code:string|null; shift_name:string|null; }
export interface OtDetail { row:OtRow; plannedBreaks:OtBreak[]; actualBreaks:OtBreak[]; days:OtDay[]; schedules:OtSchedule[]; audit:Array<{action:string; actor:string; at:string; reason:string|null}>; reconciliationIssue:string; }
export interface OtInterval { interval_id:number; work_date:string; requested_start:string; requested_end:string; approved_start:string|null; approved_end:string|null; }
export interface OtWorkflow { requestId:number; overtimeId:number; requestCode:string; employeeLabel:string; type:string; status:string; reason:string; submittedAt:string; processedBy:string|null; processedAt:string|null; rejectionReason:string|null; canDecide:boolean; intervals:OtInterval[]; }
export interface OtFilter { mine:boolean; from:string; to:string; q:string; departmentId:string; status:string; actual:string; registration:string; employeeId?:number; page:number; }
export interface OtRegistration { requestedStart:string; requestedEnd:string; reason:string; workContent:string; breaks:OtBreak[]; }
export interface OtActual { actualStart:string; actualEnd:string; breaks:OtBreak[]; actualBreakMinutes:number; reason:string; }
export interface OtWindow { workDate:string; start:string; end:string; }
@Injectable({providedIn:'root'})
export class OvertimeApiService {
  private readonly http=inject(HttpClient); private readonly url=`${API_BASE_URL}/overtime/workspace`;
  private params(obj:object):HttpParams {let p=new HttpParams();for(const [key,value]of Object.entries(obj))if(value!==''&&value!=null)p=p.set(key,String(value));return p;}
  context(){return this.http.get<OtContext>(`${this.url}/context`);}
  departments(){return this.http.get<Array<{departmentId:number;departmentName:string}>>(`${this.url}/departments`);}
  search(f:OtFilter){return this.http.get<OtPage<OtRow>>(`${this.url}/requests`,{params:this.params(f)});}
  employees(f:OtFilter){return this.http.get<OtPage<OtEmployee>>(`${this.url}/employees`,{params:this.params(f)});}
  detail(id:number){return this.http.get<OtDetail>(`${this.url}/requests/${id}`);}
  register(body:OtRegistration){return this.http.post<Ot>(`${this.url}/requests`,body);}
  edit(row:OtRow,body:OtRegistration){return this.http.put<Ot>(`${this.url}/requests/${row.ot.overtimeId}`,{expectedVersion:row.version,request:body});}
  action(row:OtRow,action:string,reason:string,actual?:OtActual){return this.http.post<Ot>(`${this.url}/requests/${row.ot.overtimeId}/${action}`,{expectedVersion:row.version,reason,actual});}
  submitWorkflow(row:OtRow,type:'cancel'|'overflow',reason:string,windows?:OtWindow[]){return this.http.post<{requestId:number}>(`${this.url}/requests/${row.ot.overtimeId}/${type}-request`,{expectedVersion:row.version,reason,windows});}
  inbox(mine:boolean,from:string,to:string,type:string,status:string,page:number){return this.http.get<OtPage<OtWorkflow>>(`${this.url}/inbox`,{params:this.params({mine,from,to,type,status,page})});}
  decide(request:OtWorkflow,approve:boolean,reason:string,accepted:Array<{intervalId:number;approvedStart:string;approvedEnd:string}>){return this.http.post<void>(`${this.url}/inbox/${request.requestId}/decision`,{approve,reason,accepted});}
  message(error:unknown):string {if(!(error instanceof HttpErrorResponse))return 'Không thể thực hiện yêu cầu.';const messages:Record<number,string>={0:'Không kết nối được máy chủ.',400:'Dữ liệu không hợp lệ.',401:'Phiên đăng nhập hết hạn, vui lòng đăng nhập lại.',403:'Không đủ quyền hoặc ngoài phạm vi được quản lý.',404:'Không tìm thấy đơn trong phạm vi được xem.',409:'Trạng thái đã thay đổi, kỳ đã khóa hoặc dữ liệu xung đột.',503:'Chức năng tạm thời chưa sẵn sàng.'};const detail=error.error?.loi_chi_tiet??error.error?.detail??error.error?.message;if(error.status===409&&typeof detail==='string'&&detail.trim())return detail;return `${messages[error.status]??'Máy chủ chưa xử lý được yêu cầu.'}${typeof detail==='string'?' '+detail:''}`;}
}
