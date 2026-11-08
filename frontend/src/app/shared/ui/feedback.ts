import {Component,Input,OnDestroy,inject} from '@angular/core';
import {ToastService} from '../../services/toast.service';
/** Adapter for existing pages; the layout renders a single shared stack. */
@Component({selector:'hrm-feedback',standalone:true,template:''})
export class Feedback implements OnDestroy {
 private toast=inject(ToastService);
 @Input() set error(value:string){this.toast.publish(this,'error',value);}
 @Input() set success(value:string){this.toast.publish(this,'success',value);}
 ngOnDestroy(){this.toast.clear(this);}
}
