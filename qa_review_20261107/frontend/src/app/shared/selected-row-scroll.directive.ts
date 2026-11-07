import { afterNextRender, Directive, ElementRef, Injector, Input, OnChanges, OnDestroy, inject } from '@angular/core';

/** Đưa dòng đang thao tác vào vùng nhìn thấy sau khi bảng đổi bố cục. */
@Directive({ selector: '[appSelectedRowScroll]', standalone: true })
export class SelectedRowScrollDirective implements OnChanges, OnDestroy {
  @Input() appSelectedRowScroll: string | null = null;

  private element = inject(ElementRef<HTMLElement>);
  private injector = inject(Injector);
  private cancel: (() => void) | null = null;

  ngOnChanges(): void {
    this.cancel?.();
    this.cancel = null;
    if (!this.appSelectedRowScroll) return;

    const pending = afterNextRender(() => {
      const row = this.element.nativeElement;
      if (!row.isConnected || !row.getClientRects().length) return;
      row.scrollIntoView({ block: 'start', inline: 'nearest', behavior: 'smooth' });
    }, { injector: this.injector });
    this.cancel = () => pending.destroy();
  }

  ngOnDestroy(): void {
    this.cancel?.();
  }
}