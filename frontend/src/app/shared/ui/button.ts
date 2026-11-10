import {Directive, HostBinding, Input} from '@angular/core';
export type ButtonVariant='primary'|'secondary'|'ghost'|'danger'|'danger-outline'|'link';
/** Styling only: native type, disabled, click and form submission stay with the caller. */
@Directive({selector:'button[hrmBtn],a[hrmBtn]',standalone:true})
export class HrmButton {
  @Input() hrmBtn:ButtonVariant|' ' | ''='secondary';
  @Input() hrmSize:'sm'|'md'|'lg'='md';
  @Input() hrmIconOnly=false;
  @HostBinding('class.hrm-button') base=true;
  @HostBinding('attr.data-variant') get variant(){return this.hrmBtn||'secondary';}
  @HostBinding('attr.data-size') get size(){return this.hrmSize;}
  @HostBinding('class.hrm-icon-only') get iconOnly(){return this.hrmIconOnly;}
}
