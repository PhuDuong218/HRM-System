import {Component} from '@angular/core';
import {HrmButton} from '../../shared/ui/button';
import {HrmIcon} from '../../shared/ui/icon';
import {StatusBadge} from '../../shared/ui/status-badge';
import {PageHeader} from '../../shared/page-header';
import {RecordWorkspace} from '../../shared/record-workspace';
import {TranslatePipe} from '../../shared/translate.pipe';
@Component({standalone:true,imports:[HrmButton,HrmIcon,StatusBadge,PageHeader,RecordWorkspace,TranslatePipe],
template:`<app-page-header [title]="'Bộ giao diện chuẩn'" [description]="'Trang minh họa giao diện; không đọc hoặc ghi dữ liệu nhân sự.'"/>
<section class="surface mb-4"><h2>{{'Nút và trạng thái'|translate}}</h2><div class="samples">@for(size of sizes;track size){<div class="sample-row">@for(variant of variants;track variant){<button type="button" [hrmBtn]="variant" [hrmSize]="size"><hrm-icon name="plus"/>{{'Thêm mới'|translate}}</button>}<button type="button" hrmBtn="secondary" [hrmSize]="size" disabled>{{'Đang lưu…'|translate}}</button></div>}</div><div class="sample-row mt-3">@for(code of statuses;track code){<hrm-status-badge [code]="code"/>}</div></section>
<app-record-workspace [open]="open" (dismiss)="open=false"><section recordList class="surface"><h2>{{'Biểu mẫu và khung chi tiết'|translate}}</h2><div class="ui-fields"><label>{{'Từ khóa'|translate}}<input class="form-control" [attr.placeholder]="'Mã hoặc họ tên'|translate"></label><label>{{'Trạng thái'|translate}}<select class="form-select"><option>{{'Tất cả'|translate}}</option></select></label></div><div class="sample-row mt-3"><button type="button" hrmBtn="primary" (click)="open=true"><hrm-icon name="eye"/>{{'Mở chi tiết mẫu'|translate}}</button></div></section><section recordDetail class="surface"><h2>{{'Biểu mẫu minh họa'|translate}}</h2><p>{{'Trang minh họa giao diện; không đọc hoặc ghi dữ liệu nhân sự.'|translate}}</p><label class="d-block mb-3">{{'Tên *'|translate}}<input class="form-control" required></label><label class="d-block">{{'Lý do'|translate}}<textarea class="form-control"></textarea></label><div class="form-actions"><button type="button" hrmBtn="secondary" (click)="open=false">{{'Hủy'|translate}}</button><button type="button" hrmBtn="primary" (click)="open=false">{{'Đã hoàn tất xem thử'|translate}}</button></div></section></app-record-workspace>
<section class="surface mt-4"><h2>{{'Khuôn trang dùng chung'|translate}}</h2><div class="pattern-grid">@for(pattern of patterns;track pattern.code){<article><span>{{pattern.code}}</span><h3>{{pattern.title|translate}}</h3><p>{{pattern.description|translate}}</p></article>}</div></section>`,
styles:[`.samples{display:grid;gap:var(--hrm-space-4)}.sample-row{display:flex;align-items:center;flex-wrap:wrap;gap:var(--hrm-space-2)}.ui-fields,.pattern-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,16rem),1fr));gap:var(--hrm-space-4)}label{min-width:0;display:grid;gap:var(--hrm-space-2)}article{padding:var(--hrm-space-4);border:var(--hrm-border);border-radius:var(--hrm-radius-md)}article>span{color:var(--hrm-primary);font-weight:600}article p{color:var(--hrm-muted);margin:0}`]})
export class UiKitPage {
 open=false;
 readonly sizes=['sm','md','lg'] as const;
 readonly variants=['primary','secondary','ghost','danger-outline','link'] as const;
 readonly statuses=['DANG_LAM','CHO_DUYET','TU_CHOI','DA_CHOT','DA_NGHI'];
 readonly patterns=[
 {code:'T1',title:'Danh mục',description:'Tiêu đề, bộ lọc, bảng, phân trang và biểu mẫu bên phải.'},
 {code:'T2',title:'Hồ sơ nhân viên',description:'Danh sách và hồ sơ cùng vùng làm việc; màn hình hẹp dùng ngăn kéo.'},
 {code:'T3',title:'Hàng đợi duyệt',description:'Theo dõi trạng thái, mở đơn, xem tác động trước khi quyết định.'},
 {code:'T4',title:'Lịch sử thay đổi',description:'Lọc đối tượng, xem người thực hiện, lý do và nội dung thay đổi.'},
 {code:'T5',title:'Tiền lương',description:'Chọn kỳ, đối chiếu tổng và thao tác theo trạng thái kỳ.'},
 {code:'T6',title:'Lịch làm việc',description:'Điều hướng ngày, bộ lọc và chi tiết theo lịch.'},
 {code:'T7',title:'Tổng quan',description:'Số liệu từ API, trạng thái tải và liên kết đến danh mục.'},
 {code:'T8',title:'Phiếu lương',description:'Số tiền căn phải, đơn vị rõ ràng và bố cục in.'},
 {code:'T9',title:'Đăng nhập',description:'Biểu mẫu tập trung, thông báo lỗi rõ và hỗ trợ bàn phím.'}
 ];
}
