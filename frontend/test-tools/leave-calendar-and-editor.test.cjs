const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const ts=require('typescript'),rx=require('rxjs');
const root=path.resolve(__dirname,'../src/app');
function load(file,requireModule,extra={}) {
 const exports={};
 const js=ts.transpileModule(fs.readFileSync(path.join(root,file),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,experimentalDecorators:true}}).outputText;
 vm.runInNewContext(js,{exports,require:requireModule,Intl,Date,Set,Map,clearTimeout,setTimeout,...extra});return exports;
}
function pendingFixture(controls) {
 const calls=[];let answer=false;let navUrl='';const events={};
 const document={querySelectorAll:()=>controls,addEventListener:(n,cb)=>events[n]=cb};
 const window={addEventListener(){}};
 const angular={Injectable:()=>c=>c,inject:token=>token.name==='ConfirmationService'?{ask:async busy=>{calls.push(busy);return answer;}}:token.name==='Router'?{getCurrentNavigation:()=>({extractedUrl:{toString:()=>navUrl}})}:{}};
 const Pending=load('services/pending-editor.service.ts',name=>name==='@angular/core'?angular:name.includes('confirmation')?{ConfirmationService:class ConfirmationService{}}:name==='@angular/router'?{Router:class Router{}}:{LanguageService:class LanguageService{}},{document,window}).PendingEditorService;
 return {service:new Pending(),calls,events,nav:url=>navUrl=url,answer:v=>answer=v};
}
// Ancestor fixtures distinguish filter forms and real editor panels, including
// Angular's dirty FORM itself, not only its dirty child input.
function dirtyControl(filter=false,visible=true) {
 const editor={getClientRects:()=>visible?[{}]:[]};
 return {closest:selector=>selector.includes('[data-hrm-filter]')?(filter?{}:null):selector.includes('.record-detail')?editor:null};
}
test('changing filters alone never prompts on route departure',async()=>{
 const f=pendingFixture([dirtyControl(true),dirtyControl(true)]);
 assert.equal(await f.service.allow(),true);assert.equal(f.calls.length,0);
});
test('unsaved edit still prompts even with dirty filters elsewhere',async()=>{
 const f=pendingFixture([dirtyControl(true),dirtyControl()]);
 assert.equal(await f.service.allow(),false);assert.deepEqual(f.calls,[false]);
 f.answer(true);assert.equal(await f.service.allow(),true);
});
test('hidden editor does not prompt; ongoing write remains protected',async()=>{
 const f=pendingFixture([dirtyControl(false,false)]);
 assert.equal(await f.service.allow(),true);
 f.service.setBusy(f,true);assert.equal(await f.service.allow(),false);assert.deepEqual(f.calls,[true]);
 f.service.setBusy(f,false);assert.equal(await f.service.allow(),true);
});
test('dismissing a toast does not trigger the unsaved editor warning',()=>{
 const f=pendingFixture([dirtyControl()]);let blocked=false;
 f.events.click({target:{closest:selector=>selector.includes('[data-hrm-notification]')?{}:null},preventDefault:()=>blocked=true,stopImmediatePropagation:()=>blocked=true});
 assert.equal(blocked,false);assert.equal(f.calls.length,0);
});
test('appearance toggles bypass unsaved and in-flight CRUD checks',()=>{
 const f=pendingFixture([dirtyControl()]);f.service.setBusy(f,true);let blocked=false;
 f.events.click({target:{closest:selector=>selector.includes('[data-hrm-preference]')?{}:null},preventDefault:()=>blocked=true,stopImmediatePropagation:()=>blocked=true});
 assert.equal(blocked,false);assert.equal(f.calls.length,0);
});
test('preserved workflow can leave only for its intended destination, once',async()=>{
 const f=pendingFixture([dirtyControl()]);f.nav('/contracts?employeeId=12');
 f.service.preserveWorkflowTransition('/contracts?employeeId=12');assert.equal(await f.service.allow(),true);assert.equal(f.calls.length,0);
 assert.equal(await f.service.allow(),false);
 f.service.preserveWorkflowTransition('/contracts?employeeId=12');f.nav('/accounts');assert.equal(await f.service.allow(),false);
});
test('query forms outside standard filter bars are explicitly marked',()=>{
 for(const file of ['pages/attendance-summary/attendance-summary.html','pages/attendance/attendance.html','pages/overtime/overtime.html','shared/overtime-calendar.html','shared/ot-supplement-panel.html']){
  const html=fs.readFileSync(path.join(root,file),'utf8');
  const forms=html.match(/<form[^>]*>/g)||[];
  for(const form of forms.filter(s=>/\(ngSubmit\)="(?:load|search|tab===|open\?load)/.test(s)))assert.match(form,/data-hrm-filter/,file);
 }
});
function calendarFixture() {
 const HttpClient=class HttpClient{},DestroyRef=class DestroyRef{},AttendanceApiService=class AttendanceApiService{};
 const types=[{leaveTypeId:1,typeCode:'ANNUAL',typeName:'Phép năm'},{leaveTypeId:2,typeCode:'OM_DAU',typeName:'Nghỉ ốm có minh chứng'},{leaveTypeId:3,typeCode:'THAI_SAN',typeName:'Nghỉ thai sản'},{leaveTypeId:4,typeCode:'VIEC_RIENG',typeName:'Nghỉ việc riêng không lương'}];
 const leave=(id,type,start='2026-11-09T08:00:00',end='2026-11-09T12:00:00')=>({requestId:id,employeeId:9,leaveTypeId:type,leaveStart:start,leaveEnd:end,withdrawnAt:null});
 const approved=[leave(1,2),{...leave(2,3),withdrawnAt:'2026-11-01T08:00:00'}];
 const http={get:url=>rx.of(url.includes('leave-types')?{content:types,totalPages:1}:url.includes('employee-requests')?{content:approved,totalPages:1}:url.includes('calendar-policy')?{configured:true,holidays:[]}:{items:[],totalPages:0})};
 const angular={Component:()=>c=>c,Input:()=>()=>{},DestroyRef,inject:token=>token===HttpClient?http:token===DestroyRef?{onDestroy(){}}:{message:String}};
 const C=load('shared/attendance-calendar.ts',name=>{
  if(name==='@angular/core')return angular;
  if(name==='@angular/common/http')return {HttpClient,HttpParams:class{set(){return this;}}};
  if(name==='rxjs')return rx;
  if(name==='@angular/core/rxjs-interop')return {takeUntilDestroyed:()=>s=>s};
  if(name.includes('attendance-api.service'))return {AttendanceApiService};return {};
 }).AttendanceCalendarComponent;
 const c=new C();c.types=types;c.month='2026-11';c.anchor='2026-11-01';c.employee='9';
 return {c,leave};
}
test('calendar retains approved sick leave and excludes withdrawn leave',async()=>{
 const {c}=calendarFixture();await c.load();assert.equal(c.error,'');assert.equal(c.leaves.length,1);
 const day=c.days.find(d=>d.date==='2026-11-09');assert.equal(c.state(day),'sick');
 assert.ok(c.legend.some(s=>s[0]==='sick'));c.filter='sick';assert.equal(c.matches(day),true);
});
test('leave categories remain distinct and half-day interval is not duplicated',()=>{
 const {c,leave}=calendarFixture();
 assert.equal(c.leaveState(leave(1,1)),'leave');assert.equal(c.leaveState(leave(1,2)),'sick');assert.equal(c.leaveState(leave(1,3)),'maternity');assert.equal(c.leaveState(leave(1,4)),'leave');
 c.leaves=[leave(1,2)];c.rebuild();assert.equal(c.days.filter(d=>d.leaves.length).length,1);
 c.leaves=[leave(1,2,'2026-11-09T22:00:00','2026-11-10T00:00:00')];c.rebuild();assert.equal(c.days.filter(d=>d.leaves.length).length,1);
});

test('calendar can select an employee then return directly to all employees repeatedly',()=>{
 const {c}=calendarFixture();
 c.rows=[{employeeId:9,employeeLabel:'NV009 — Nguyễn Tâm'},{employeeId:10,employeeLabel:'NV010 — Hân'}];
 c.chooseEmployee('9');assert.equal(c.scopedRows.length,1);
 c.chooseEmployee('');assert.equal(c.scopedRows.length,2);assert.equal(c.q,'');
 c.chooseEmployee('10');c.chooseEmployee('');assert.equal(c.scopedRows.length,2);
});
test('Enter applies accent-insensitive partial search without choosing a suggestion',()=>{
 const {c}=calendarFixture();
 c.rows=[{employeeId:9,employeeLabel:'NV009 — Nguyễn Tâm'},{employeeId:10,employeeLabel:'NV010 — Trần Tâm'},{employeeId:11,employeeLabel:'NV011 — Hân'}];
 c.q='tam';c.applyEmployeeSearch();assert.equal(c.scopedRows.length,2);assert.equal(c.suggesting,false);
 c.q='han';c.applyEmployeeSearch();assert.equal(c.scopedRows.length,1);
 c.q='not-found';c.applyEmployeeSearch();assert.equal(c.scopedRows.length,0);
 c.chooseEmployee('');assert.equal(c.scopedRows.length,3);
});
