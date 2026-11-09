import {AfterViewInit, Directive, ElementRef, OnDestroy, inject} from '@angular/core';
import {EMPTY, Subject, Subscription, catchError, switchMap, timer} from 'rxjs';
import {EmployeeService} from '../services/employee.service';

export function searchKey(value:string):string {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[đĐ]/g,'d').toLowerCase().trim();
}

/** Progressive enhancement: existing bindings, IDs, permissions and submit handlers remain authoritative. */
@Directive({selector:'input',standalone:true})
export class SearchAssistDirective implements AfterViewInit,OnDestroy {
  private element=inject<ElementRef<HTMLInputElement|HTMLSelectElement>>(ElementRef).nativeElement;
  private cleanup:(()=>void)[]=[];
  private employees=inject(EmployeeService);
  private lookup?:Subscription;
  private static nextId=0;
  ngAfterViewInit(){
    const el=this.element;
    const labelNode=el.labels?.[0]?.cloneNode(true) as HTMLElement|undefined;
    labelNode?.querySelectorAll('input,select,textarea').forEach(n=>n.remove());
    const label=(labelNode?.textContent||'')+' '+el.getAttribute('placeholder')+' '+el.name;
    if(!(el instanceof HTMLInputElement))return;
    {
      if(!['text','search'].includes(el.type)||el.hasAttribute('list')||! /tìm|search|keyword|từ khóa|mã hoặc|code or/i.test(label))return;
      if(el.hasAttribute('hrmEmployeeSearch')) {
        const list=this.makeList(el);
        const input=new Subject<string>();
        this.lookup=input.pipe(switchMap(query=>query.length<2?EMPTY:timer(250).pipe(
          switchMap(()=>this.employees.suggest(query)),catchError(()=>EMPTY)
        ))).subscribe(rows=>{
          list.replaceChildren(...rows.map(row=>{
            const option=document.createElement('option');
            option.value=row.employeeCode;
            option.label=row.fullName+' · '+searchKey(row.fullName);
            return option;
          }));
        });
        this.listen(el,'input',()=>{
          list.replaceChildren();
          input.next(el.value.trim());
        });
        // Leave the browser's native Enter behavior for selecting a suggestion.
        if(el.form)return;
      }
      this.listen(el,'keydown',event=>{
        const e=event as KeyboardEvent;if(e.key!=='Enter'||e.defaultPrevented||e.isComposing)return;
        if(el.form){e.preventDefault();el.form.requestSubmit();return;}
        const scope=el.closest('section,.surface,.card')||el.parentElement;
        const button=Array.from(scope?.querySelectorAll<HTMLButtonElement>('button')||[]).find(b=>/^(tìm|tìm kiếm|search|find)$/i.test(b.textContent?.trim()||''));
        if(button&&!button.disabled){e.preventDefault();button.click();}
      });
    }
  }
  private makeList(input:HTMLInputElement){const list=document.createElement('datalist');list.id='hrm-search-'+ ++SearchAssistDirective.nextId;input.setAttribute('list',list.id);input.after(list);this.cleanup.push(()=>{input.removeAttribute('list');list.remove();});return list;}
  private listen(el:HTMLElement,event:string,handler:(e:Event)=>void){el.addEventListener(event,handler);this.cleanup.push(()=>el.removeEventListener(event,handler));}
  ngOnDestroy(){this.lookup?.unsubscribe();this.cleanup.forEach(fn=>fn());}
}
