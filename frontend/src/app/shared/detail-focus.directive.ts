import {afterNextRender,Directive,ElementRef,Injector,Input,OnChanges,OnDestroy,inject} from '@angular/core';

/** Focus only after the selected record has rendered; cancel stale selections. */
@Directive({selector:'[appDetailFocus]',standalone:true,host:{'tabindex':'-1','style':'scroll-margin-top:96px'}})
export class DetailFocusDirective implements OnChanges,OnDestroy {
 @Input() appDetailFocus:unknown;
 private element=inject(ElementRef<HTMLElement>);private injector=inject(Injector);private cancel:(()=>void)|null=null;
 ngOnChanges(){
  this.cancel?.();this.cancel=null;
  if(this.appDetailFocus===null||this.appDetailFocus===undefined||this.appDetailFocus===false)return;
  const pending=afterNextRender(()=>{
   const panel=this.element.nativeElement;
   if(!panel.isConnected||!panel.getClientRects().length)return;
   panel.scrollIntoView({block:'start',inline:'nearest',behavior:'instant'});
   panel.focus({preventScroll:true});
  },{injector:this.injector});
  this.cancel=()=>pending.destroy();
 }
 ngOnDestroy(){this.cancel?.();}
}
