import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TranslatePipe } from './translate.pipe';
import { HrmButton } from './ui/button';

type PageItem = number | 'leading-gap' | 'trailing-gap';

@Component({
  selector: 'app-pagination',
  standalone: true,
  imports: [TranslatePipe, HrmButton],
  template: `
    @if (pages > 0) {
      <nav class="pager" [attr.aria-label]="label | translate">
        <span class="visually-hidden" aria-live="polite">{{ 'Trang' | translate }} {{ page + 1 }} / {{ pages }}</span>
        <button type="button" hrmBtn="secondary" hrmSize="sm" [disabled]="busy || page <= 0"
                [attr.aria-label]="'Trang đầu' | translate" (click)="go(0)">&lt;&lt;</button>
        <button type="button" hrmBtn="secondary" hrmSize="sm" [disabled]="busy || page <= 0"
                [attr.aria-label]="'Trang trước' | translate" (click)="go(page - 1)">&lt;</button>
        @for (item of items; track item) {
          @if (item === 'leading-gap' || item === 'trailing-gap') {
            <span class="gap-mark" aria-hidden="true">…</span>
          } @else {
            <button type="button" [hrmBtn]="item === page ? 'primary' : 'secondary'" hrmSize="sm"
                    [disabled]="busy" [attr.aria-current]="item === page ? 'page' : null"
                    [attr.aria-label]="('Trang' | translate) + ' ' + (item + 1)"
                    (click)="go(item)">{{ item + 1 }}</button>
          }
        }
        <button type="button" hrmBtn="secondary" hrmSize="sm" [disabled]="busy || page >= pages - 1"
                [attr.aria-label]="'Trang sau' | translate" (click)="go(page + 1)">&gt;</button>
        <button type="button" hrmBtn="secondary" hrmSize="sm" [disabled]="busy || page >= pages - 1"
                [attr.aria-label]="'Trang cuối' | translate" (click)="go(pages - 1)">&gt;&gt;</button>
      </nav>
    }
  `,
  styles: [`
    :host { display: block; min-width: 0; }
    .pager { display: flex; justify-content: flex-end; align-items: center; flex-wrap: wrap;
             gap: 4px; margin-top: var(--hrm-space-4); padding-top: var(--hrm-space-3); border-top: var(--hrm-border); }
    .pager button { min-inline-size: 2rem; padding-inline: .35rem; font-variant-numeric: tabular-nums; }
    .gap-mark { color: var(--hrm-muted); padding-inline: .15rem; }
  `]
})
export class Pagination {
  @Input() page = 0;
  @Input() pages = 0;
  @Input() busy = false;
  @Input() label = 'Phân trang';
  @Output() pageChange = new EventEmitter<number>();

  get items(): PageItem[] {
    const last = this.pages - 1;
    if (last < 0) return [];
    if (last < 4) return Array.from({ length: this.pages }, (_, i) => i);

    const start = Math.max(1, this.page - 1);
    const end = Math.min(last - 1, this.page + 1);
    const items: PageItem[] = [0];
    if (start > 1) items.push(start === 2 ? 1 : 'leading-gap');
    for (let index = start; index <= end; index++) items.push(index);
    if (end < last - 1) items.push(end === last - 2 ? last - 1 : 'trailing-gap');
    items.push(last);
    return items;
  }

  go(page: number): void {
    if (!this.busy && Number.isInteger(page) && page >= 0 && page < this.pages && page !== this.page) {
      this.pageChange.emit(page);
    }
  }
}
