const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),{createRequire}=require('node:module');
const root=require('node:path').resolve(__dirname,'..');const req=createRequire(root+'/package.json'),ts=req('typescript'),rx=req('rxjs');
function fixture(){
 const HttpClient=class{},DestroyRef=class{},AttendanceApiService=class{},OvertimeApiService=class{};
 const callbacks=[],regular=new Map(),ot=new Map();
 const regularApi={detail:id=>{const s=new rx.Subject();regular.set(id,s);return s;},message:e=>String(e)};
 const otApi={detail:id=>{const s=new rx.Subject();ot.set(id,s);return s;},message:e=>String(e)};
 const tokens=new Map([[DestroyRef,{onDestroy:cb=>callbacks.push(cb)}],[HttpClient,{get:()=>rx.of({items:[],totalPages:0})}],[AttendanceApiService,regularApi],[OvertimeApiService,otApi]]);
 const deco=()=>target=>target;const angular={Component:deco,Input:deco,Output:deco,inject:t=>tokens.get(t),DestroyRef};
 function load(file){const result=ts.transpileModule(fs.readFileSync(root+'/src/app/shared/'+file+'.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,experimentalDecorators:true}});const exports={};vm.runInNewContext(result.outputText,{exports,require:name=>{if(name==='@angular/core')return angular;if(name==='rxjs')return rx;if(name==='@angular/common/http')return {HttpClient,HttpParams:class{set(){return this;}}};if(name==='@angular/core/rxjs-interop')return {takeUntilDestroyed:()=>s=>s};if(name.includes('attendance-api.service'))return {AttendanceApiService};if(name.includes('overtime-api.service'))return {OvertimeApiService};if(name==='./overtime-calendar')return load('overtime-calendar');return {};},Intl,Date,console});return exports;}
 const component=new (load('personal-calendar').PersonalCalendarComponent)();
 const entry=id=>({key:'r'+id,regular:{scheduleId:id},start:'',end:''});const overtime=id=>({key:'o'+id,ot:{row:{ot:{overtimeId:id}}},start:'',end:''});
 return {component,regular,ot,entry,overtime,callbacks};
}
test('older regular response cannot replace the latest selected record',()=>{const f=fixture();f.component.open(f.entry(1));const a=f.regular.get(1);f.component.open(f.entry(2));f.regular.get(2).next({row:{scheduleId:2}});a.next({row:{scheduleId:1}});assert.equal(f.component.regularDetail.row.scheduleId,2);});
test('regular response cannot reopen after selecting OT',()=>{const f=fixture();f.component.open(f.entry(1));const a=f.regular.get(1);f.component.open(f.overtime(7));a.next({row:{scheduleId:1}});f.ot.get(7).next({row:{ot:{overtimeId:7}}});assert.equal(f.component.regularDetail,null);assert.equal(f.component.viewDetail.row.ot.overtimeId,7);});
test('OT response cannot replace later regular selection',()=>{const f=fixture();f.component.open(f.overtime(7));const a=f.ot.get(7);f.component.open(f.entry(2));f.regular.get(2).next({row:{scheduleId:2}});a.next({row:{ot:{overtimeId:7}}});assert.equal(f.component.viewDetail,null);assert.equal(f.component.regularDetail.row.scheduleId,2);});
test('closing regular detail prevents a late response reopening it',()=>{const f=fixture();f.component.open(f.entry(1));const a=f.regular.get(1);f.component.closeRegularDetail();a.next({row:{scheduleId:1}});assert.equal(f.component.regularDetail,null);});
test('changed calendar and destroyed component invalidate pending detail',async()=>{const f=fixture();f.component.open(f.entry(1));const a=f.regular.get(1);f.component.load();a.next({row:{scheduleId:1}});await new Promise(resolve=>setImmediate(resolve));assert.equal(f.component.regularDetail,null);f.component.open(f.entry(2));const b=f.regular.get(2);f.callbacks.forEach(cb=>cb());b.next({row:{scheduleId:2}});assert.equal(f.component.regularDetail,null);});
