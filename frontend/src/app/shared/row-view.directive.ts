import {Directive,ElementRef,EventEmitter,HostListener,Input,Output,inject} from '@angular/core';

/** Keeps nested action buttons independent from opening a record. */
@Directive({selector:'[appRowView]',standalone:true,host:{'[attr.tabindex]':'rowViewDisabled ? -1 : 0','[style.cursor]':'rowViewDisabled ? null : "pointer"','[attr.aria-disabled]':'rowViewDisabled ? true : null'}})
export class RowViewDirective {
 @Input() rowViewDisabled=false;
 @Output() appRowView=new EventEmitter<void>();
 private readonly element=inject(ElementRef<HTMLElement>);
 private interactive(event:Event):boolean {
  const target=event.target as Element|null;
  return !!target?.closest?.('button,a,input,select,textarea,label,summary,[role="button"],[role="link"],[contenteditable="true"]');
 }
 @HostListener('click',['$event']) click(event:MouseEvent){
  if(this.rowViewDisabled||event.defaultPrevented||this.interactive(event))return;
  const selection=this.element.nativeElement.ownerDocument.defaultView?.getSelection();
  if(selection&&!selection.isCollapsed)return;
  this.appRowView.emit();
 }
 @HostListener('keydown',['$event']) keydown(event:KeyboardEvent){
  if(this.rowViewDisabled||event.defaultPrevented||event.repeat||event.target!==this.element.nativeElement||!['Enter',' '].includes(event.key))return;
  event.preventDefault();this.appRowView.emit();
 }
}
