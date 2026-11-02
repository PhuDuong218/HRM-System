import {TranslatePipe} from './translate.pipe';
import {Component,Input,Output,EventEmitter} from '@angular/core';
@Component({imports:[TranslatePipe],selector:'app-pagination',standalone:true,template:`<nav class="pager" [attr.aria-label]="label|translate"><button type="button" class="btn btn-outline-secondary" [disabled]="busy||page<=0||pages===0" (click)="pageChange.emit(page-1)"><span aria-hidden="true">‹ </span>{{ "Trước" | translate }}</button><span class="page-label" aria-live="polite">{{'Trang'|translate}} <strong>{{pages?page+1:0}}</strong> / {{pages}}</span><button type="button" class="btn btn-outline-secondary" [disabled]="busy||page+1>=pages" (click)="pageChange.emit(page+1)">{{'Sau'|translate}}<span aria-hidden="true"> ›</span></button></nav>`,styles:[`:host{display:block;margin-top:16px}.pager{display:flex;justify-content:space-between;align-items:center;gap:12px;padding-top:16px;border-top:1px solid #e4eaf2}.page-label{font-size:13px;text-align:center;color:#506278;white-space:nowrap}.btn{min-height:44px;white-space:nowrap}@media(max-width:360px){.pager{gap:6px}.btn{padding:8px 10px}.page-label{font-size:12px}}`]})
export class Pagination {
 @Input() page=0;@Input() pages=0;@Input() busy=false;@Input() label='Phân trang';
 @Output() pageChange=new EventEmitter<number>();
}
