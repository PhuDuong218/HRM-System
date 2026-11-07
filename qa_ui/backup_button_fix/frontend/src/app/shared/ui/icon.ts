import {Component,Input} from '@angular/core';
export const ICONS:Record<string,string>={
 plus:'M12 5v14 M5 12h14',edit:'m16 3 5 5-12 12H4v-5z M14 5l5 5',eye:'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12 M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
 search:'M21 21l-5-5 M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',filter:'M3 4h18l-7 8v7l-4 2v-9z',x:'m6 6 12 12 M6 18 18 6',check:'m5 12 4 4L19 6',
 'chevron-left':'m15 6-6 6 6 6','chevron-right':'m9 6 6 6-6 6','chevron-down':'m6 9 6 6 6-6','chevron-up':'m6 15 6-6 6 6',
 'chevrons-left':'m11 6-6 6 6 6 M19 6l-6 6 6 6','chevrons-right':'m5 6 6 6-6 6 M13 6l6 6-6 6',
 refresh:'M20 7v5h-5 M4 17v-5h5 M5 7a8 8 0 0 1 14-2l1 2 M4 17l1 2a8 8 0 0 0 14-2',
 info:'M12 11v6 M12 7v.1 M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0',
 'alert-triangle':'M12 3 2 21h20z M12 9v5 M12 17v.1',calendar:'M3 5h18v16H3z M3 10h18 M7 3v4 M17 3v4',
 clock:'M12 8v4l3 2 M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0',
 user:'M20 21v-2a7 7 0 0 0-14 0v2 M17 7a5 5 0 1 1-10 0 5 5 0 0 1 10 0',
 'file-text':'M4 3h11l5 5v13H4z M14 3v6h6 M8 13h8 M8 17h6',
 menu:'M4 6h16 M4 12h16 M4 18h16',download:'M12 3v12 m-5-5 5 5 5-5 M4 17v4h16v-4',
 printer:'M6 9V3h12v6 M6 17H3V9h18v8h-3 M6 14h12v7H6z',lock:'M5 11h14v10H5z M8 11V6a4 4 0 0 1 8 0v5',
 'more-horizontal':'M4 12h.1 M12 12h.1 M20 12h.1',copy:'M9 9h12v12H9z M5 15H3V3h12v2',
 'arrow-up':'M12 21V3 m-7 7 7-7 7 7','arrow-down':'M12 3v18 m-7-7 7 7 7-7'
};
@Component({selector:'hrm-icon',standalone:true,template:`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path [attr.d]="icons[name]||icons['info']"/></svg>`,styles:[`:host{display:inline-flex;flex:0 0 var(--hrm-icon);width:var(--hrm-icon);height:var(--hrm-icon)}svg{display:block;width:100%;height:100%}`]})
export class HrmIcon {@Input() name='info';readonly icons=ICONS;}
