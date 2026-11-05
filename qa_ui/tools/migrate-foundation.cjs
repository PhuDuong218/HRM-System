const fs=require('node:fs');const path=require('node:path');const root=path.resolve(__dirname,'../..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');const write=(p,s)=>fs.writeFileSync(path.join(root,p),s);
// Preserve earlier contributors' styles as an explicit compatibility layer.
let legacy=read('frontend/src/styles.css').split(/\r?\n/).slice(1,60).join('\n');
write('frontend/src/styles/legacy.css',legacy+'\n');
write('frontend/src/styles.css',"@import './styles/tokens.css';\n@import './styles/legacy.css';\n@import './app/shared/management-page.css';\n@import './styles/base.css';\n");
const pages=['catalog/catalog','employees/employees','departments/departments','personnel-workspace/personnel-workspace','head-requests/head-requests'];
function buttons(s){return s.replace(/<(button|a)\b([^>]*?)\sclass="([^"]*\bbtn\b[^"]*)"([^>]*)>/g,(all,tag,a,classes,b)=>{
 const list=classes.split(/\s+/);const variant=list.includes('btn-danger')?'danger':list.includes('btn-outline-danger')?'danger-outline':list.includes('btn-primary')?'primary':list.includes('btn-link')?'link':'secondary';const size=list.includes('btn-sm')?'sm':'md';const keep=list.filter(c=>c&&!/^btn(?:-|$)/.test(c)).join(' ');
 return `<${tag}${a}${keep?' class="'+keep+'"':''} hrmBtn="${variant}" hrmSize="${size}"${b}>`;
});}
for(const p of pages){const file='frontend/src/app/pages/'+p;write(file+'.html',buttons(read(file+'.html')));let ts=read(file+'.ts');ts="import {HrmButton} from '../../shared/ui/button';\n"+ts;ts=ts.replace(/imports\s*:\s*\[/,'imports:[HrmButton,');write(file+'.ts',ts);}
let css=read('frontend/src/app/shared/management-page.css');
// One card threshold, independent of whether the sidebar is expanded.
css=css.replace(/max-width:740px/g,'max-width:640px').replace(/max-width:420px/g,'max-width:575px');
css=css.replace(/@media\(max-width:1250px\)\s*\{[\s\S]*?html:has\(\.management-page \.record-detail\)\s*\{ overflow:hidden; \}\s*\}/,'html:has(app-record-workspace.drawer-mode .record-detail){overflow:hidden}');
// Remove page-specific button sizes now owned by the primitive.
css=css.replace(/\.management-page \.btn \{[^}]*\}/g,'').replace(/\.management-page \.table \.btn \{[^}]*\}/g,'');
css=css.replace(/min-height:42px/g,'min-height:var(--hrm-control-md)').replace(/min-height:44px/g,'min-height:var(--hrm-control-md)');
write('frontend/src/app/shared/management-page.css',css);
// Other members keep their structures, use the shared card threshold mechanically.
for(const p of ['frontend/src/app/pages/module-list.css','frontend/src/app/pages/accounts/accounts.css','frontend/src/app/pages/contracts/contracts.css']){
 let s=read(p).replace(/@container([^{}]*?)\(max-width:\s*(?:740|1000)px\)/g,'@container$1(max-width: 640px)');write(p,s);
}
// Global import bridge now owns the old employee status/button dimensions.
write('frontend/src/app/pages/employees/employees.css',read('frontend/src/app/pages/employees/employees.css').replace(/\.status\{[^}]*\}/g,'').replace(/\.retired\{[^}]*\}/g,''));
// Shell media conditions and keyboard focus use the same threshold.
let shell=read('frontend/src/app/layout/admin-layout.ts').replaceAll('max-width:800px','max-width:1023px').replaceAll('min-width:801px','min-width:1024px');
write('frontend/src/app/layout/admin-layout.ts',shell);
let shellCss=read('frontend/src/app/layout/admin-layout.css').replaceAll('max-width:800px','max-width:1023px').replaceAll('max-width: 800px','max-width: 1023px').replaceAll('min-width:801px','min-width:1024px');
shellCss=shellCss.replace(/width: 270px/g,'width: var(--hrm-sidebar)').replace(/width: 76px/g,'width: var(--hrm-sidebar-compact)').replace(/--app-topbar-height: 76px/g,'--app-topbar-height: var(--hrm-topbar)').replace(/--app-topbar-height: 64px/g,'--app-topbar-height: var(--hrm-topbar)');
write('frontend/src/app/layout/admin-layout.css',shellCss);
console.log('Foundation and five shared personnel page families migrated; endpoints untouched.');
