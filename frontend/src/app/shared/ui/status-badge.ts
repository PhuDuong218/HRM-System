import {Component,Input} from '@angular/core';
import {TranslatePipe} from '../translate.pipe';
import {HrmIcon} from './icon';
export const STATUS_MAP:Record<string,{label:string;tone:string;icon:string}>={
 DANG_LAM:{label:'Đang làm',tone:'success',icon:'check'},DA_DUYET:{label:'Đã duyệt',tone:'success',icon:'check'},
 CONG_BO:{label:'Đã công bố',tone:'success',icon:'check'},CHO_DUYET:{label:'Chờ duyệt',tone:'warning',icon:'clock'},
 NHAP:{label:'Nháp',tone:'warning',icon:'edit'},DA_CHOT:{label:'Đã chốt',tone:'info',icon:'lock'},
 TU_CHOI:{label:'Từ chối',tone:'danger',icon:'x'},DA_NGHI:{label:'Đã nghỉ',tone:'neutral',icon:'info'},
 DA_HUY:{label:'Đã hủy',tone:'neutral',icon:'x'},DA_RUT:{label:'Đã rút',tone:'neutral',icon:'x'},
 HOAT_DONG:{label:'Hoạt động',tone:'success',icon:'check'},NGUNG:{label:'Ngừng sử dụng',tone:'neutral',icon:'info'},
 HET_HAN:{label:'Đã hết hạn',tone:'neutral',icon:'clock'}
};
@Component({selector:'hrm-status-badge',standalone:true,imports:[TranslatePipe,HrmIcon],
template:`<span class="badge" [attr.data-tone]="state.tone"><hrm-icon [name]="state.icon"/>{{state.label|translate}}</span>`,
styles:[`:host{display:inline-flex}.badge{display:inline-flex;align-items:center;gap:var(--hrm-space-1);border-radius:var(--hrm-radius-pill);padding:var(--hrm-space-1) var(--hrm-space-2);font:600 var(--hrm-font-xs)/1.333 var(--hrm-font);white-space:nowrap;background:var(--hrm-neutral-bg);color:var(--hrm-neutral-text)}[data-tone=success]{background:var(--hrm-success-bg);color:var(--hrm-success-text)}[data-tone=warning]{background:var(--hrm-warning-bg);color:var(--hrm-warning-text)}[data-tone=danger]{background:var(--hrm-danger-bg);color:var(--hrm-danger-text)}[data-tone=info]{background:var(--hrm-info-bg);color:var(--hrm-info-text)}`]})
export class StatusBadge {@Input() code='';@Input() label='';get state(){return STATUS_MAP[this.code]||{label:this.label||this.code,tone:'neutral',icon:'info'};}}
