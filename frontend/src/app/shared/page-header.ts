import {NgTemplateOutlet} from '@angular/common';
import {Component,Input} from '@angular/core';
import {TranslatePipe} from './translate.pipe';
import {Breadcrumb} from './breadcrumb';
@Component({selector:'app-page-header',standalone:true,imports:[Breadcrumb,TranslatePipe,NgTemplateOutlet],template:`@if(variant === "directory"){<header class="directory-hero">
  <div class="directory-hero-copy">
    <p class="directory-eyebrow">{{ eyebrow | translate }}</p>
    <h1>{{ title | translate }}</h1>
    @if(description){<p>{{ description | translate }}</p>}
    <div class="page-actions"><ng-container [ngTemplateOutlet]="actions"/></div>
  </div>
  @if(resultCount !== null){<div class="directory-hero-stat">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
      <circle cx="9" cy="7" r="3"/><path d="M3 20v-2a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v2M16 4a3 3 0 0 1 0 6m1 4a4 4 0 0 1 4 4v2"/>
    </svg>
    <strong>{{ busy ? '—' : resultCount }}</strong>
    <span>{{ 'Kết quả tìm thấy' | translate }}</span>
  </div>
}
</header>}@else{<header><div><app-breadcrumb variant="group"/><h1>{{title|translate}}</h1>@if(description){<p>{{description|translate}}</p>}</div><div class="page-actions"><ng-container [ngTemplateOutlet]="actions"/></div></header>}<ng-template #actions><ng-content /></ng-template>`,styleUrl:"./page-header.css",styles:[`:host{display:block;margin-bottom:24px}header{display:flex;justify-content:space-between;align-items:flex-start;gap:16px;flex-wrap:wrap}h1{margin:0 0 8px}p{margin:0;color:var(--bs-secondary-color,#738397);font-size:14px}.page-actions{display:flex;gap:8px;flex-wrap:wrap;align-items:center;max-width:100%}`]})
export class PageHeader {@Input() eyebrow='HRM';@Input() title='';@Input() description='';@Input() variant:'standard'|'directory'='standard';@Input() resultCount:number|null=null;@Input() busy=false;}
