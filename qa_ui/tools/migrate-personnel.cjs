const fs=require('node:fs');const path=require('node:path');const root=path.resolve(__dirname,'../..');const read=p=>fs.readFileSync(path.join(root,p),'utf8');const write=(p,s)=>fs.writeFileSync(path.join(root,p),s);
const pages=['catalog/catalog','employees/employees','departments/departments','personnel-workspace/personnel-workspace','head-requests/head-requests'];
for(const p of pages){const base='frontend/src/app/pages/'+p;let h=read(base+'.html');let ts=read(base+'.ts');
 h=h.replace(/@if\s*\(error\)\s*\{\s*<(p|div) class="alert alert-danger" role="alert">\{\{\s*\(?error\)?\s*\|\s*translate\s*\}\}<\/\1>\s*\}/,'');
 h=h.replace(/@if\s*\(message\)\s*\{\s*<(p|div) class="alert alert-success" role="status">\{\{\s*\(?message\)?\s*\|\s*translate\s*\}\}<\/\1>\s*\}/,'');
 h='<hrm-feedback [error]="error" [success]="message"/>\n'+h;
 if(p==='catalog/catalog')h=h.replace(/<span class="status" \[class.retired\]="!row\['isActive'\]">[\s\S]*?<\/span>/g,'<hrm-status-badge [code]="row[\'isActive\']?\'HOAT_DONG\':\'NGUNG\'"/>');
 if(p==='personnel-workspace/personnel-workspace')h=h.replace(/<span class="status" \[class.status-pending\][\s\S]*?<\/span>/g,'<hrm-status-badge [code]="text(row[c])" [label]="display(c,row[c])"/>');
 // Only those two pages use the new badge, avoiding unused standalone imports.
 ts="import {Feedback} from '../../shared/ui/feedback';\n"+ts;
 ts=ts.replace('imports:[HrmButton,','imports:[Feedback,HrmButton,');
 if(p==='catalog/catalog'||p==='personnel-workspace/personnel-workspace'){
  ts="import {StatusBadge} from '../../shared/ui/status-badge';\n"+ts;ts=ts.replace('imports:[Feedback,','imports:[StatusBadge,Feedback,');
 }
 h=h.replace('[code]="text(row[c])"','[code]="displayCode(row[c])"');
 if(p==='personnel-workspace/personnel-workspace')ts=ts.replace('export class PersonnelWorkspacePage extends ManagementPageBase {','export class PersonnelWorkspacePage extends ManagementPageBase {\n displayCode(value:unknown):string{return String(value??\'\');}\n');
 write(base+'.html',h);write(base+'.ts',ts);
}
// Preserve role rules. Only change the grouping of existing links.
let nav=read('frontend/src/app/navigation.ts').replace('  roles?: string[];','  roles?: string[];\n  permissions?: string[];\n  badge?: number;',);
nav=nav.replace('  roles?: string[];\n  path?: string;','  roles?: string[];\n  permissions?: string[];\n  badge?: number;\n  path?: string;');
nav=nav.replace("{ path: 'contract-types', label: 'Loại hợp đồng' },",'').replace("{ path: 'work-shifts', label: 'Danh mục ca' },",'').replace("{ path: 'leave-types', label: 'Loại nghỉ phép' },",'');
nav=nav.replace("id: 'personnel', label: 'Quản lý Nhân sự'","id: 'personnel', label: 'Nhân sự'").replace("id: 'time', label: 'Thời gian & Chấm công'","id: 'time', label: 'Thời gian & Nghỉ phép'").replace("id: 'organization', label: 'Cơ cấu tổ chức'","id: 'organization', label: 'Danh mục dùng chung'");
nav=nav.replace("{ path: 'qualifications', label: 'Trình độ' }","{ path: 'qualifications', label: 'Trình độ' },\n      { path: 'department-catalog', label: 'Danh mục phòng ban' },\n      { path: 'contract-types', label: 'Loại hợp đồng' },\n      { path: 'leave-types', label: 'Loại nghỉ phép' },\n      { path: 'work-shifts', label: 'Danh mục ca' }");
write('frontend/src/app/navigation.ts',nav);
let overview=read('frontend/src/app/pages/overview/overview.html').replace('class="overview-refresh"','hrmBtn="secondary"');write('frontend/src/app/pages/overview/overview.html',overview);
let ov=read('frontend/src/app/pages/overview/overview.ts');ov="import {HrmButton} from '../../shared/ui/button';\n"+ov.replace('imports: [RouterLink]','imports: [HrmButton,RouterLink]');write('frontend/src/app/pages/overview/overview.ts',ov);
const connection='frontend/src/app/pages/connection/connection';let ch=read(connection+'.html').replace(/class="btn btn-primary"/g,'hrmBtn="primary"');write(connection+'.html',ch);write(connection+'.ts',"import {HrmButton} from '../../shared/ui/button';\n"+read(connection+'.ts').replace('imports:[TranslatePipe]','imports:[HrmButton,TranslatePipe]'));
console.log('Personnel feedback, badge and navigation presentation migrated.');
