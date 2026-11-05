import {TranslatePipe} from './translate.pipe';
import { AfterViewInit, ChangeDetectorRef, Component, ElementRef, EventEmitter, HostBinding, Input, OnDestroy, Output, ViewChild, inject } from '@angular/core';
import {HrmButton} from './ui/button';
import {HrmIcon} from './ui/icon';
import {PendingEditorService} from '../services/pending-editor.service';

/** Shared list/detail workspace. Narrow screens use a right drawer, never a form below the table. */
@Component({imports:[TranslatePipe,HrmButton,HrmIcon],
  selector: 'app-record-workspace',
  standalone: true,
  template: `
    <div class="record-layout" [class.has-detail]="open">
      <div class="record-list"><ng-content select="[recordList]" /></div>
      @if (open) {
        <button class="record-backdrop" type="button" [attr.aria-label]="&quot;Đóng bảng chi tiết&quot; | translate"
                tabindex="-1" [disabled]="busy" (click)="dismiss.emit()"></button>
        <aside #panel class="record-detail" tabindex="-1" [attr.role]="compact?'dialog':null" [attr.aria-modal]="compact?'true':null" [attr.aria-label]="&quot;Chi tiết và thao tác&quot; | translate"
               (keydown.escape)="close($event)" (keydown.tab)="keepFocus($event)">
          <div class="panel-toolbar"><span>{{ "THÔNG TIN & THAO TÁC" | translate }}</span>
            <button type="button" hrmBtn="ghost" [hrmIconOnly]="true" [disabled]="busy"
                    (click)="dismiss.emit()" [attr.title]="'Đóng bảng chi tiết'|translate" [attr.aria-label]="&quot;Đóng bảng chi tiết&quot; | translate"><hrm-icon name="x"/></button>
          </div>
          <ng-content select="[recordDetail]" />
        </aside>
      }
    </div>
  `,
  styles: [`
    :host{display:block;container:record-space / inline-size;min-width:0}
    .record-layout{display:grid;gap:var(--hrm-space-5);align-items:start}
    .record-list,.record-detail{min-width:0}
    .has-detail{grid-template-columns:minmax(0,1.15fr) minmax(0,1fr)}
    .record-detail{position:sticky;top:calc(var(--hrm-topbar) + var(--hrm-space-4));max-height:calc(100dvh - var(--hrm-topbar) - var(--hrm-space-6));overflow:auto;scrollbar-gutter:stable;border:var(--hrm-border);border-radius:var(--hrm-radius-lg);background:var(--hrm-surface);color:var(--hrm-text);box-shadow:var(--hrm-shadow-1)}
    .record-detail:focus{outline:none}
    .panel-toolbar{position:sticky;top:0;z-index:var(--hrm-z-sticky);background:var(--hrm-surface);display:flex;align-items:center;justify-content:space-between;gap:var(--hrm-space-3);padding:var(--hrm-space-3) var(--hrm-space-4);border-bottom:var(--hrm-border)}
    .panel-toolbar span{font-size:var(--hrm-font-xs);font-weight:600;letter-spacing:.04em;color:var(--hrm-muted)}
    .record-backdrop{display:none}
    @container record-space (max-width:999px){
      .has-detail{grid-template-columns:minmax(0,1fr)}
      .record-detail{position:fixed;right:0;top:0;bottom:0;width:min(560px,100dvw);max-width:100dvw;height:100dvh;max-height:100dvh;overflow-y:auto;z-index:var(--hrm-z-drawer);border-radius:var(--hrm-radius-lg) 0 0 var(--hrm-radius-lg);box-shadow:var(--hrm-shadow-3);overscroll-behavior:contain}
      .record-backdrop{display:block;position:fixed;inset:0;z-index:calc(var(--hrm-z-drawer) - 1);border:0;background:color-mix(in srgb,var(--hrm-text) 35%,transparent);cursor:pointer}
      .record-backdrop:disabled{cursor:wait}
    }
    @media(max-width:575px){
      .panel-toolbar{min-width:0;gap:var(--hrm-space-2);padding:var(--hrm-space-3) var(--hrm-space-4)}
      .panel-toolbar span{min-width:0;overflow-wrap:anywhere}
      .panel-toolbar button{flex:0 0 auto}
    }
  `]
})
export class RecordWorkspace implements AfterViewInit,OnDestroy {
  private pendingEditor=inject(PendingEditorService);
  private host=inject(ElementRef<HTMLElement>);
  private cdr=inject(ChangeDetectorRef);
  private observer?:ResizeObserver;
  private returnFocus:HTMLElement|null=null;
  compact=false;
  @HostBinding('class.drawer-mode') get drawerMode(){return this.compact&&this.open;}
  @Input() open = false;
  private _busy=false;
  @Input() set busy(value:boolean){this._busy=value;this.pendingEditor.setBusy(this,value);}
  get busy(){return this._busy;}
  @Output() dismiss = new EventEmitter<void>();
  @ViewChild('panel') set panel(element: ElementRef<HTMLElement> | undefined) {
    // Keep the page heading and filter position stable when opening a panel.
    if (element) {
      this.returnFocus=document.activeElement as HTMLElement;
      queueMicrotask(() => element.nativeElement.isConnected&&element.nativeElement.focus({preventScroll:true}));
    } else if(this.returnFocus?.isConnected) {
      this.returnFocus.focus({preventScroll:true});this.returnFocus=null;
    }
  }
  ngAfterViewInit(){this.observer=new ResizeObserver(entries=>{this.compact=entries[0].contentRect.width<1000;this.cdr.markForCheck();});this.observer.observe(this.host.nativeElement);}
  ngOnDestroy(){this.observer?.disconnect();this.pendingEditor.setBusy(this,false);}
  async close(event: Event) {
    event.stopPropagation();
    if (!this.busy&&await this.pendingEditor.allow()) this.dismiss.emit();
  }
  keepFocus(event: Event) {
    // Only trap focus when the panel is a modal drawer; desktop keeps the list accessible.
    const panel = event.currentTarget as HTMLElement;
    if (getComputedStyle(panel).position !== 'fixed') return;
    const controls = Array.from(panel.querySelectorAll<HTMLElement>('button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href]'))
      .filter(node => node.getClientRects().length > 0);
    const first = controls[0], last = controls[controls.length - 1];
    const key = event as KeyboardEvent;
    if (key.shiftKey && (document.activeElement === first || document.activeElement === panel)) {
      event.preventDefault(); last?.focus();
    } else if (!key.shiftKey && (document.activeElement === last || document.activeElement === panel)) {
      event.preventDefault(); first?.focus();
    }
  }
}
