import {Pipe,PipeTransform,inject} from '@angular/core';
import {LanguageService} from '../services/language.service';
/** UI text only. Business records and API values are not translated implicitly. */
@Pipe({name:'translate',standalone:true,pure:false})
export class TranslatePipe implements PipeTransform {
 private language=inject(LanguageService);
 transform(value:unknown):string {return value===null||value===undefined?'':this.language.t(String(value));}
}
