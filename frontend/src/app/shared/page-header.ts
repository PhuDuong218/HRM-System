import {Component,Input} from '@angular/core';
import {TranslatePipe} from './translate.pipe';
@Component({selector:'app-page-header',standalone:true,imports:[TranslatePipe],template:`<header><div><h1>{{title|translate}}</h1>@if(description){<p>{{description|translate}}</p>}</div><div class="page-actions"><ng-content /></div></header>`,styles:[`:host{display:block;margin-bottom:24px}header{display:flex;justify-content:space-between;align-items:flex-start;gap:16px;flex-wrap:wrap}h1{margin:0 0 8px}p{margin:0;color:var(--bs-secondary-color,#738397);font-size:14px}.page-actions{display:flex;gap:8px;flex-wrap:wrap;align-items:center;max-width:100%}`]})
export class PageHeader {@Input() title='';@Input() description='';}
