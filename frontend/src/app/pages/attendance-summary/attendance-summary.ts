import {Component, DestroyRef, OnInit, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {WorkModuleApiService} from '../../services/work-module-api.service';
import {AttendanceApiService} from '../attendance/attendance-api.service';
interface Day {employeeId:number;employeeCode:string;employeeName:string;department:string|null;workDate:string;presentRecords:number;absentRecords:number;netMinutes:number;unresolvedRecords:number;}
interface Employee {id:number;code:string;name:string;departments:string[];days:Day[];present:number;absent:number;minutes:number;unresolved:number;}
@Component({selector:'app-attendance-summary',standalone:true,imports:[CommonModule,FormsModule],templateUrl:'./attendance-summary.html',styleUrls:['../work-module.css','./attendance-summary.css']})
export class AttendanceSummaryPage implements OnInit {
 private api=inject(WorkModuleApiService);private attendance=inject(AttendanceApiService);private destroy=inject(DestroyRef);
 private date(d:Date){return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;}
 month=this.date(new Date()).slice(0,7);from=`${this.month}-01`;to=this.date(new Date(new Date().getFullYear(),new Date().getMonth()+1,0));
 q='';department='';state='';loading=false;error='';loaded=false;period='';page=1;readonly size=10;
 employees:Employee[]=[];results:Employee[]=[];selected:Employee|null=null;
 get departments(){return [...new Set(this.employees.flatMap(e=>e.departments))].sort((a,b)=>a.localeCompare(b,'vi'));}
 get pages(){return Math.max(1,Math.ceil(this.results.length/this.size));}
 get visible(){return this.results.slice((this.page-1)*this.size,this.page*this.size);}
 get numbers():(number|string)[]{const a=[...new Set([1,this.page-1,this.page,this.page+1,this.pages])].filter(n=>n>0&&n<=this.pages).sort((a,b)=>a-b);const out:(number|string)[]=[];a.forEach((n,i)=>{if(i&&n-a[i-1]>1)out.push('…');out.push(n);});return out;}
 get stats(){return this.results.reduce((s,e)=>({minutes:s.minutes+e.minutes,present:s.present+e.present,absent:s.absent+e.absent,unresolved:s.unresolved+e.unresolved}),{minutes:0,present:0,absent:0,unresolved:0});}
 ngOnInit(){this.load();}
 normalize(s:string){return s.normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/đ/g,'d').replace(/Đ/g,'D').toLowerCase();}
 hours(n:number){return `${Math.floor(n/60)} giờ ${n%60} phút`;}
 setMonth(){if(!/^\d{4}-\d{2}$/.test(this.month))return;const[y,m]=this.month.split('-').map(Number);this.from=`${this.month}-01`;this.to=this.date(new Date(y,m,0));}
 reset(){this.q='';this.department='';this.state='';this.month=this.date(new Date()).slice(0,7);this.setMonth();this.load();}
 load(){if(this.loading)return;if(!this.from||!this.to||this.to<this.from||(Date.parse(this.to)-Date.parse(this.from))/86400000>366){this.error='Chọn khoảng ngày hợp lệ, tối đa 367 ngày.';return;}this.loading=true;this.error='';this.selected=null;this.loaded=false;this.results=[];this.employees=[];const from=this.from,to=this.to;
 this.api.confirmed(from,to,'').pipe(takeUntilDestroyed(this.destroy)).subscribe({next:value=>{const data=value as {days:Day[]};const map=new Map<number,Employee>();for(const d of data.days){let e=map.get(d.employeeId);if(!e){e={id:d.employeeId,code:d.employeeCode,name:d.employeeName,departments:[],days:[],present:0,absent:0,minutes:0,unresolved:0};map.set(e.id,e);}e.days.push(d);if(d.department&&!e.departments.includes(d.department))e.departments.push(d.department);e.present+=d.presentRecords;e.absent+=d.absentRecords;e.minutes+=d.netMinutes;e.unresolved+=d.unresolvedRecords;}this.employees=[...map.values()].sort((a,b)=>a.name.localeCompare(b.name,'vi'));this.period=`${from} → ${to}`;this.loading=false;this.loaded=true;this.filter();},error:e=>{this.loading=false;this.error=this.attendance.message(e);}});}
 filter(){const q=this.normalize(this.q.trim());this.results=this.employees.map(e=>{const days=e.days.filter(d=>!this.department||d.department===this.department);return {...e,days,departments:[...new Set(days.map(d=>d.department).filter((d):d is string=>!!d))],present:days.reduce((s,d)=>s+d.presentRecords,0),absent:days.reduce((s,d)=>s+d.absentRecords,0),minutes:days.reduce((s,d)=>s+d.netMinutes,0),unresolved:days.reduce((s,d)=>s+d.unresolvedRecords,0)};}).filter(e=>e.days.length&&this.normalize(`${e.code} ${e.name}`).includes(q)&&(!this.state||(this.state==='review'?e.unresolved>0:e.unresolved===0)));this.page=1;this.selected=null;}
 go(n:number|string){if(typeof n==='number'&&n>=1&&n<=this.pages){this.page=n;this.selected=null;}}
}
