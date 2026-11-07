import {Component,Injectable,Input,inject} from '@angular/core';
import {Router,RouterLink} from '@angular/router';
import {navigation} from '../navigation';
import {AuthService} from '../services/auth.service';
import {TranslatePipe} from './translate.pipe';
/** Navigation labels are the single source of page hierarchy. No API requests. */
@Injectable({providedIn:'root'})
export class PageContext {
 private router=inject(Router);private auth=inject(AuthService);
 get current(){
  const path=this.router.url.split(/[?#]/)[0].replace(/^\/+|\/+$/g,'');
  const role=this.auth.profile()?.role??'';
  for(const group of navigation){
   const children=(group.children??[]).filter(c=>!c.roles||c.roles.includes(role));
   const child=children.find(c=>c.path===path);
   if(child)return {group:group.label,parent:children[0]?.path??child.path,title:child.label,path};
   if(group.path===path)return {group:'',parent:'',title:group.label,path};
  }
  if(path==='requests/head')return {group:'Thời gian & Nghỉ phép',parent:['ADMIN','HR'].includes(role)?'requests':'employees',title:'Duyệt cấp 1',path};
  return {group:'Hệ thống',parent:['ADMIN','HR'].includes(role)?'overview':'employees',title:path==='ui-kit'?'Bộ giao diện chuẩn':path==='forbidden'?'Không có quyền truy cập':'Không tìm thấy trang',path};
 }
 get canOverview(){return ['ADMIN','HR'].includes(this.auth.profile()?.role??'');}
}
@Component({selector:'app-breadcrumb',standalone:true,imports:[RouterLink,TranslatePipe],
template:`@if(variant==='group'){@if(context.current.group){<a [routerLink]="'/'+context.current.parent">{{context.current.group|translate}}</a>}@else{<span>{{context.current.title|translate}}</span>}}@else{<nav [attr.aria-label]="'Đường dẫn trang'|translate"><ol>@if(context.canOverview&&context.current.path!=='overview'){<li><a routerLink="/overview">{{'Tổng quan'|translate}}</a></li>}@if(context.current.group){<li><a [routerLink]="'/'+context.current.parent">{{context.current.group|translate}}</a></li>}<li aria-current="page"><span>{{context.current.title|translate}}</span></li></ol></nav>}`,
styles:[`:host{display:block;min-width:0;font-size:var(--hrm-font-sm);line-height:1.54;color:var(--hrm-muted)}ol{display:flex;align-items:center;flex-wrap:wrap;gap:var(--hrm-space-2);list-style:none;padding:0;margin:0}li{display:flex;align-items:center;gap:var(--hrm-space-2);min-width:0}li+li::before{content:'/';color:var(--hrm-muted)}a{color:var(--hrm-primary);text-decoration:none;border-radius:var(--hrm-radius-sm)}a:hover{text-decoration:underline}span{overflow-wrap:anywhere}:host([variant=group]){margin-bottom:var(--hrm-space-2);font-size:var(--hrm-font-xs);font-weight:600}`]})
export class Breadcrumb {@Input() variant:'path'|'group'='path';readonly context=inject(PageContext);}
