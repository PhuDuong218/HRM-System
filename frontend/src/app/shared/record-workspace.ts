import { Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';

/** Shared list/detail workspace. Narrow screens use a right drawer, never a form below the table. */
@Component({
  selector: 'app-record-workspace',
  standalone: true,
  template: `
    <div class="record-layout" [class.has-detail]="open">
      <div class="record-list"><ng-content select="[recordList]" /></div>
      @if (open) {
        <button class="record-backdrop" type="button" aria-label="Đóng bảng chi tiết"
                tabindex="-1" [disabled]="busy" (click)="dismiss.emit()"></button>
        <aside #panel class="record-detail" tabindex="-1" aria-label="Chi tiết và thao tác"
               (keydown.escape)="close($event)" (keydown.tab)="keepFocus($event)">
          <div class="panel-toolbar"><span>THÔNG TIN & THAO TÁC</span>
            <button type="button" class="btn btn-sm btn-outline-secondary" [disabled]="busy"
                    (click)="dismiss.emit()" aria-label="Đóng bảng chi tiết">Đóng ×</button>
          </div>
          <ng-content select="[recordDetail]" />
        </aside>
      }
    </div>
  `,
  styles: [`
    :host{display:block}
    .record-layout{display:grid;gap:20px;align-items:start}
    .record-list,.record-detail{min-width:0}
    .has-detail{grid-template-columns:minmax(0,1.15fr) minmax(360px,1fr)}
    .record-detail{position:sticky;top:16px;max-height:calc(100dvh - 32px);overflow:auto;scrollbar-gutter:stable;border:1px solid #e4eaf2;border-radius:14px;background:#fff;box-shadow:0 6px 24px #17365308}
    .record-detail:focus{outline:none}
    .panel-toolbar{position:sticky;top:0;z-index:2;background:#fff;display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px 18px;border-bottom:1px solid #e4eaf2}
    .panel-toolbar span{font-size:10px;font-weight:700;letter-spacing:.8px;color:#637b91}
    .record-backdrop{display:none}
    @media(max-width:1250px){
      .has-detail{grid-template-columns:minmax(0,1fr)}
      .record-detail{position:fixed;right:0;top:0;bottom:0;width:min(560px,100%);max-height:100dvh;z-index:101;border-radius:16px 0 0 16px;box-shadow:-12px 0 40px #17365324;overscroll-behavior:contain}
      .record-backdrop{display:block;position:fixed;inset:0;z-index:100;border:0;background:#112b4566;cursor:pointer}
      .record-backdrop:disabled{cursor:wait}
    }
  `]
})
export class RecordWorkspace {
  @Input() open = false;
  @Input() busy = false;
  @Output() dismiss = new EventEmitter<void>();
  @ViewChild('panel') set panel(element: ElementRef<HTMLElement> | undefined) {
    if (element) queueMicrotask(() => element.nativeElement.focus());
  }
  close(event: Event) {
    event.stopPropagation();
    if (!this.busy) this.dismiss.emit();
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
