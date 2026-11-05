import {Component,Input,inject} from '@angular/core';
import {RouterLink} from '@angular/router';
import {TranslatePipe} from './translate.pipe';
import {AuthService} from '../services/auth.service';

/** One navigation and vocabulary for leave setup, balances and both approval stages. */
@Component({selector:'app-leave-workflow-guide',standalone:true,imports:[RouterLink,TranslatePipe],template:`
<section class="leave-guide" aria-label="{{'Quy trình nghỉ phép'|translate}}">
 <div class="guide-heading"><strong>{{'Quy trình nghỉ phép'|translate}}</strong><span>{{'Đơn vị quỹ phép: giờ'|translate}}</span></div>
 <nav class="guide-steps" aria-label="{{'Các bước xử lý nghỉ phép'|translate}}">
 @for(step of steps;track step.path){
  @if(step.key==='head'?isHead:canManage){<a [routerLink]="step.path" [class.current]="stage===step.key" [attr.aria-current]="stage===step.key?'page':null"><span class="step-number">{{step.number}}</span><span><strong>{{step.title|translate}}</strong><small>{{step.role|translate}}</small></span></a>}
  @else{<div class="step-disabled"><span class="step-number">3</span><span><strong>{{step.title|translate}}</strong><small>{{(step.key==='head'?'Chỉ trưởng phòng đang có nhiệm kỳ':'ADMIN / HR')|translate}}</small></span></div>}
 }
 </nav>
 <p>{{help|translate}}</p>
 <details><summary>{{'Ai thao tác ở bước nào?'|translate}}</summary><ul>
 <li>{{'ADMIN cấu hình điều kiện loại nghỉ; ADMIN/HR đúng phạm vi cấp quỹ phép.'|translate}}</li>
 <li>{{'Nhân viên nộp đơn từ ứng dụng nhân viên. Duyệt cấp 1 chưa trừ quỹ; duyệt cuối mới ghi nhận giờ phép.'|translate}}</li>
 <li>{{'Đơn của trưởng phòng chuyển HR/ADMIN xử lý; không ai được tự duyệt. Hủy đơn phép đã duyệt phải có lý do và đáp ứng điều kiện thời gian.'|translate}}</li>
 </ul></details>
</section>`,styles:[`
 .leave-guide{margin:0 0 24px;padding:20px;border:1px solid var(--bs-border-color,#e4eaf2);border-radius:14px;background:var(--bs-body-bg,#fff)}
 .guide-heading{display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap;margin-bottom:14px}.guide-heading>span{font-size:12px;color:var(--bs-secondary-color,#60788e)}
 .guide-steps{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px;margin-bottom:14px}.guide-steps a,.step-disabled{display:flex;align-items:center;gap:10px;padding:12px;border-radius:9px;border:1px solid var(--bs-border-color,#e4eaf2);text-decoration:none;color:inherit;min-width:0}.guide-steps .current{background:#eaf3ff;border-color:#004581;color:#004581}.guide-steps a:hover{border-color:#004581}.step-number{display:grid;place-items:center;width:26px;height:26px;border-radius:50%;background:#edf2f8;color:#004581;flex-shrink:0}.guide-steps strong{font-size:13px}.guide-steps small{display:block;font-size:11px;color:var(--bs-secondary-color,#60788e);margin-top:4px}.step-disabled{opacity:.7}.leave-guide p,.leave-guide li{font-size:13px;line-height:1.65}.leave-guide p{margin:0 0 8px}.leave-guide summary{font-size:13px;cursor:pointer}.leave-guide ul{margin:10px 0 0;padding-left:20px}
 @media(max-width:900px){.guide-steps{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:480px){.guide-steps{grid-template-columns:1fr}.leave-guide{padding:16px}}
`]})
export class LeaveWorkflowGuide {
 @Input() stage='';
 private auth=inject(AuthService);
 get canManage(){return ['ADMIN','HR'].includes(this.auth.profile()?.role||'');}
 get isHead(){return !!this.auth.profile()?.departmentHead;}
 steps=[{number:1,key:'types',path:'/leave-types',title:'Loại nghỉ phép',role:'Cấu hình chính sách'},
 {number:2,key:'balances',path:'/leave-entitlements',title:'Quỹ phép',role:'Cấp giờ phép cho nhân viên'},
 {number:3,key:'head',path:'/requests/head',title:'Duyệt cấp 1',role:'Trưởng phòng'},
 {number:4,key:'requests',path:'/requests',title:'Duyệt cuối / Theo dõi',role:'HR / ADMIN'}];
 get help(){return ({types:'Chọn một loại nghỉ để xem hưởng lương, trừ quỹ và điều kiện áp dụng. Hưởng lương không đồng nghĩa với trừ phép năm.',balances:'Chọn nhân viên rồi bấm Tìm kiếm để xem quỹ được cấp, đã dùng và còn lại. 8 giờ tương đương một ngày nếu chính sách quy đổi là 8 giờ/ngày.',head:'Chỉ xử lý đơn của nhân viên thuộc phòng bạn đang phụ trách. Sau duyệt cấp 1, đơn vẫn chờ HR/ADMIN quyết định cuối.',requests:'Mở Chi tiết để xem đơn đang chờ cấp nào. Chỉ duyệt cuối khi cấp 1 đã hoàn tất hoặc đơn được chuyển thẳng theo quy tắc hệ thống.'} as Record<string,string>)[this.stage]||'';}
}
