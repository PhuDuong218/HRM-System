import { Pagination } from './pagination';
import {Component,DestroyRef,ElementRef,HostListener,OnInit,inject} from '@angular/core';
import {Router} from '@angular/router';
import {DatePipe} from '@angular/common';
import {HttpClient} from '@angular/common/http';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {EMPTY,Subject,catchError,distinctUntilChanged,filter,finalize,forkJoin,fromEvent,map,merge,of,switchMap,timer} from 'rxjs';
import {API_BASE_URL} from '../services/api.config';
interface Notice {id:number;title:string;message:string;eventType:string;referenceType:string;referenceId:number;isRead:boolean;createdAt:string}
interface Inbox {items:Notice[];totalElements:number;page:number;totalPages:number;unreadCount:number;snapshotId:number}
@Component({selector:'app-notification-bell',standalone:true,imports: [Pagination,DatePipe],templateUrl:'./notification-bell.html',styleUrl:'./notification-bell.css'})
export class NotificationBell implements OnInit {
 private router=inject(Router);private http=inject(HttpClient);private destroy=inject(DestroyRef);private element=inject<ElementRef<HTMLElement>>(ElementRef);private refresh=new Subject<void>();
 open=false;unread=0;onlyUnread=false;page=0;snapshotId:number|undefined;inbox:Inbox|null=null;loading=false;busy=false;error='';detail:Record<string,unknown>|null=null;
 get badge(){return this.unread>99?'99+':String(this.unread);}
 ngOnInit(){
  const visible=merge(of(!document.hidden),fromEvent(document,'visibilitychange').pipe(map(()=>!document.hidden))).pipe(distinctUntilChanged());
  const ticks=visible.pipe(switchMap(v=>v?timer(0,15000):EMPTY));
  merge(ticks,this.refresh).pipe(filter(()=>!document.hidden&&!this.busy),switchMap(()=>{
   this.loading=this.open;
   const params:Record<string,string|number|boolean>={page:this.page,size:10};if(this.onlyUnread)params['read']=false;if(this.snapshotId!==undefined&&this.page>0)params['snapshotId']=this.snapshotId;
   return forkJoin({count:this.http.get<{unreadCount:number}>(API_BASE_URL+'/notifications/unread-count'),list:this.open?this.http.get<Inbox>(API_BASE_URL+'/notifications',{params}):of(null)}).pipe(catchError(e=>{this.loading=false;this.error=this.message(e.status);return of(null);}));
  }),takeUntilDestroyed(this.destroy)).subscribe(result=>{this.loading=false;if(!result)return;this.error='';this.unread=result.count.unreadCount;if(result.list){this.inbox=result.list;this.snapshotId=result.list.snapshotId;}});
 }
 toggle(){this.open=!this.open;this.detail=null;if(this.open){this.page=0;this.snapshotId=undefined;this.refresh.next();}}
 close(){this.open=false;this.detail=null;this.element.nativeElement.querySelector<HTMLButtonElement>('.bell-trigger')?.focus();}
 setFilter(value:boolean){if(this.busy)return;this.onlyUnread=value;this.page=0;this.snapshotId=undefined;this.detail=null;this.refresh.next();}
 move(page:number){if(this.busy||this.loading)return;this.page=page;this.refresh.next();}
 reload(){this.page=0;this.snapshotId=undefined;this.refresh.next();}
 markAll(){if(this.busy||!this.inbox)return;this.busy=true;this.http.put<void>(API_BASE_URL+'/notifications/read-all',{throughId:this.inbox.snapshotId}).pipe(takeUntilDestroyed(this.destroy),finalize(()=>this.busy=false)).subscribe({next:()=>{this.busy=false;this.reload();},error:e=>this.error=this.message(e.status)});}
 select(row:Notice){if(this.busy)return;this.busy=true;this.error='';this.http.get<{detail:Record<string,unknown>;mine?:boolean;scheduleId?:number;route?:string;reviewKind?:string;referenceType?:string;referenceId?:number}>(API_BASE_URL+'/notifications/'+row.id+'/target').pipe(switchMap(target=>this.http.put<void>(API_BASE_URL+'/notifications/'+row.id+'/read',{}).pipe(map(()=>target))),takeUntilDestroyed(this.destroy),finalize(()=>this.busy=false)).subscribe({next:target=>{this.busy=false;this.open=false;this.detail=null;this.reload();if(target.route==='/department/reviews'){void this.router.navigate(['/department/reviews'],{queryParams:{notificationRequest:target.referenceId??row.referenceId,notificationKind:target.reviewKind}});return;}const referenceType=target.referenceType??row.referenceType;const referenceId=target.referenceId??row.referenceId;if(referenceType==='overtime_requests'){void this.router.navigate(['/overtime'],{queryParams:{notificationRequest:referenceId,notificationMine:target.mine===true}});return;}const attendance=referenceType==='attendance_portal_requests'||referenceType==='attendance_records';void this.router.navigate([attendance?'/attendance':target.route==='/requests/head'?'/requests/head':'/requests'],{queryParams:attendance?(referenceType==='attendance_records'?{notificationSchedule:target.scheduleId,notificationMine:true}:{notificationRequest:referenceId,notificationMine:target.mine===true}):{notificationRequest:referenceId,notificationMine:target.mine===true,notificationType:target.detail['requestType']}});},error:e=>{this.detail=null;this.error=this.message(e.status);}});}
 status(value:unknown){return ({CHO_XU_LY:'Chờ xử lý',DANG_XU_LY:'Đã tiếp nhận',DA_CHAP_NHAN:'Đã chấp thuận',CHO_DUYET:'Chờ duyệt',DA_DUYET:'Đã duyệt',TU_CHOI:'Từ chối',DA_HUY:'Đã hủy'} as Record<string,string>)[String(value)]||String(value??'—');}
 private message(status:number){return status===401?'Phiên đăng nhập đã hết hạn.':status===403?'Bạn không còn quyền xem nội dung này.':status===404?'Thông báo hoặc hồ sơ không còn tồn tại.':status===503?'Chức năng thông báo chưa được kích hoạt.':'Không tải được thông báo. Hệ thống sẽ thử lại; bạn có thể bấm Tải lại.';}
 @HostListener('document:keydown.escape') escape(){if(this.open)this.close();}
 @HostListener('document:click',['$event']) outside(e:MouseEvent){if(this.open&&!this.element.nativeElement.contains(e.target as Node)){this.open=false;this.detail=null;}}
}
