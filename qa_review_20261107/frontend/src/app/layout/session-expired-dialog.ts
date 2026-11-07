import { AfterViewInit, Component, ElementRef, EventEmitter, Input, Output, ViewChild, inject } from '@angular/core';
import { LanguageService } from '../services/language.service';

@Component({
  selector: 'app-session-expired-dialog',
  standalone: true,
  template: `
    <dialog #dialog class="expired-dialog" aria-labelledby="expired-title" aria-describedby="expired-message"
            (cancel)="onCancel($event)">
      <div class="expired-header">
        <span class="expired-icon" aria-hidden="true">!</span>
        <h2 id="expired-title">{{ language.t('Phiên đăng nhập') }}</h2>
      </div>
      <p id="expired-message">{{ language.t(message) }}</p>
      <div class="expired-actions">
        <button type="button" (click)="exit.emit()" autofocus>{{ language.t('Thoát') }}</button>
      </div>
    </dialog>
  `,
  styles: [`
    .expired-dialog { width:min(440px, calc(100% - 32px)); padding:0; border:1px solid #dce7f2; border-radius:16px; background:#fff; color:#173653; box-shadow:0 24px 80px #102d4a38; }
    .expired-dialog::backdrop { background:#112b45a6; }
    .expired-header { display:flex; align-items:center; gap:12px; padding:24px 26px 18px; border-bottom:1px solid #e5edf5; }
    .expired-header h2 { margin:0; font-size:20px; font-weight:700; }
    .expired-icon { display:grid; place-items:center; width:36px; height:36px; flex-shrink:0; border-radius:50%; background:#fff1d7; color:#996300; font-weight:800; font-size:20px; }
    .expired-dialog p { margin:0; padding:22px 26px; color:#506b82; font-size:14px; line-height:1.6; }
    .expired-actions { display:flex; justify-content:flex-end; padding:16px 26px; border-top:1px solid #e5edf5; }
    .expired-actions button { min-width:90px; min-height:42px; padding:9px 18px; border:0; border-radius:8px; background:#004581; color:#fff; font-weight:700; cursor:pointer; }
    .expired-actions button:hover { background:#003561; }
    .expired-actions button:focus-visible { outline:2px solid #2677b5; outline-offset:3px; }
    :host-context([data-bs-theme='dark']) .expired-dialog { background:#1c2a3c; color:#edf5ff; border-color:#40536b; }
    :host-context([data-bs-theme='dark']) .expired-header, :host-context([data-bs-theme='dark']) .expired-actions { border-color:#40536b; }
    :host-context([data-bs-theme='dark']) .expired-dialog p { color:#c2d0e2; }
  `]
})
export class SessionExpiredDialog implements AfterViewInit {
  @Input({ required: true }) message = '';
  @Output() exit = new EventEmitter<void>();
  @ViewChild('dialog') private dialog!: ElementRef<HTMLDialogElement>;
  readonly language = inject(LanguageService);

  ngAfterViewInit(): void { this.dialog.nativeElement.showModal(); }

  onCancel(event: Event): void {
    event.preventDefault();
    this.exit.emit();
  }
}
