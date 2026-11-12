import { Pagination } from './pagination';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { API_BASE_URL } from '../services/api.config';
interface TenureRow {employeeId:number;employeeCode:string;fullName:string;hireDate:string;endDate:string;status:string;tenure:{years:number;months:number;days:number;totalMonths:number}}
interface TenureReport {asOf:string;totalEmployees:number;underOneYear:number;oneToFiveYears:number;fiveYearsOrMore:number;items:TenureRow[];page:number;totalPages:number}
@Component({selector:'app-seniority-report',standalone:true,imports: [Pagination,FormsModule,DatePipe],template:`
<section class="surface p-3 mt-3" aria-label="Thống kê thâm niên">
 <h2 class="h5">Thâm niên nhân sự</h2>
 <p class="small text-muted">Tính theo ngày vào làm của đợt công tác hiện tại; dừng tại ngày nghỉ việc. Số liệu là căn cứ tham khảo, không tự phát sinh thưởng.</p>
 <form class="d-flex gap-2 flex-wrap align-items-end" (ngSubmit)="load(0)">
  <label>Ngày thống kê<input class="form-control" type="date" name="asOf" [(ngModel)]="asOf" required></label>
  <label>Mã / tên nhân viên<input class="form-control" name="keyword" [(ngModel)]="keyword" maxlength="200" placeholder="Nhập một phần mã hoặc tên"></label>
  <button class="btn btn-primary" [disabled]="loading">Tìm kiếm</button>
 </form>
 @if(error){<p class="alert alert-danger mt-2" role="alert">{{error}}</p>}
 @if(loading){<p role="status">Đang tải thâm niên…</p>}
 @if(result){
  <div class="d-flex flex-wrap gap-3 my-3"><span>Tổng: <strong>{{result.totalEmployees}}</strong></span><span>Dưới 1 năm: <strong>{{result.underOneYear}}</strong></span><span>Từ 1 đến dưới 5 năm: <strong>{{result.oneToFiveYears}}</strong></span><span>Từ 5 năm: <strong>{{result.fiveYearsOrMore}}</strong></span></div>
  <div class="table-responsive"><table class="table"><thead><tr><th>Nhân viên</th><th>Ngày vào làm</th><th>Tính đến</th><th>Thâm niên</th><th>Tổng tháng</th><th>Trạng thái hiện tại</th></tr></thead><tbody>
  @for(row of result.items;track row.employeeId){<tr><td>{{row.employeeCode}} – {{row.fullName}}</td><td>{{row.hireDate|date:'dd/MM/yyyy'}}</td><td>{{row.endDate|date:'dd/MM/yyyy'}}</td><td>{{row.tenure.years}} năm {{row.tenure.months}} tháng {{row.tenure.days}} ngày</td><td>{{row.tenure.totalMonths}}</td><td>{{row.status==='DA_NGHI'?'Đã nghỉ việc':'Đang làm việc'}}</td></tr>}
  @empty{<tr><td colspan="6">Không có nhân viên phù hợp trong phạm vi được cấp.</td></tr>}
  </tbody></table></div>
  <app-pagination [page]="result.page" [pages]="result.totalPages" [busy]="loading" (pageChange)="load($event)" />
 }
</section>`})
export class SeniorityReport implements OnInit {
 private http=inject(HttpClient);private destroy=inject(DestroyRef);private request?:Subscription;
 asOf=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());keyword='';loading=false;error='';result:TenureReport|null=null;
 ngOnInit(){this.load(0);}
 load(page:number){this.request?.unsubscribe();this.error='';this.result=null;if(!this.asOf){this.error='Chọn ngày thống kê.';return;}this.loading=true;this.request=this.http.get<TenureReport>(API_BASE_URL+'/reports/seniority',{params:{asOf:this.asOf,keyword:this.keyword.trim(),page,size:10}}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{this.result=r;this.loading=false;},error:e=>{this.loading=false;this.error=e.status===403?'Bạn không có quyền xem thâm niên.':e.status===400?'Ngày thống kê không hợp lệ hoặc ở tương lai.':'Không tải được thâm niên. Vui lòng thử lại.';}});}
}
