const fs=require('node:fs'),path=require('node:path');
function change(p,fn){const b='qa_ui/backup_record_rows/'+p;fs.mkdirSync(path.dirname(b),{recursive:true});if(!fs.existsSync(b))fs.copyFileSync(p,b);fs.writeFileSync(p,fn(fs.readFileSync(p,'utf8')));}
const pages=[
 ['employees/employees','<tr [class.selected-row]','<tr (appRowView)="select(r)" [rowViewDisabled]="saving||loading||detailLoading" [class.selected-row]'],
 ['catalog/catalog','<tr [class.selected]','<tr (appRowView)="open(row)" [rowViewDisabled]="saving||loading" [class.selected]'],
 ['departments/departments','<tr [class.selected]','<tr (appRowView)="select(d)" [rowViewDisabled]="saving||loading" [class.selected]'],
 ['personnel-workspace/personnel-workspace','<tr [class.selected]','<tr (appRowView)="open(row)" [rowViewDisabled]="saving||loading" [class.selected]'],
 ['head-requests/head-requests','{<tr><td [attr.data-label]','{<tr (appRowView)="open(row)" [rowViewDisabled]="saving||loading||detailLoading"><td [attr.data-label]'],
 ['decisions/decisions','<tr [class.selected-row]','<tr (appRowView)="view(row)" [rowViewDisabled]="saving||loading" [class.selected-row]'],
 ['payroll/payroll','<tr>\n            <td>{{ item.','<tr (appRowView)="viewDetail(item)" [rowViewDisabled]="loading">\n            <td>{{ item.']
];
for(const [p,from,to]of pages){const h='frontend/src/app/pages/'+p+'.html';let html=fs.readFileSync(h,'utf8').replaceAll('\r\n','\n');if(!html.includes(from)){console.log('NOT MATCHED',p);continue;}change(h,()=>html.replace(from,to));const t='frontend/src/app/pages/'+p+'.ts';change(t,s=>"import {RowViewDirective} from '../../shared/row-view.directive';\n"+s.replace(/imports\s*:\s*\[/,'imports:[RowViewDirective,'));}
