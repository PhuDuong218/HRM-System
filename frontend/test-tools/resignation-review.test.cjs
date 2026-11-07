const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript'),rx=require('rxjs');
function fixture(){
 const requests=[],events=[];const DestroyRef=class{},PersonnelWorkspaceService=class{};
 const api={resignationReadiness:(id,date)=>{const subject=new rx.Subject();requests.push({id,date,subject});return subject;}};
 const angular={Component:()=>c=>c,Input:()=>()=>{},Output:()=>()=>{},DestroyRef,EventEmitter:class{emit(value){events.push(value);}},inject:token=>token===PersonnelWorkspaceService?api:{onDestroy(){}}};
 const exports={};const code=ts.transpileModule(fs.readFileSync(__dirname+'/../src/app/shared/resignation-review.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,experimentalDecorators:true}}).outputText;
 vm.runInNewContext(code,{exports,Date,Set,require:name=>name==='@angular/core'?angular:name==='rxjs'?rx:name==='@angular/core/rxjs-interop'?{takeUntilDestroyed:()=>s=>s}:name.includes('personnel-workspace.service')?{PersonnelWorkspaceService}:name.includes('employee.service')?{errorMessage:()=> 'Không thể kiểm tra'}:{}});
 const page=new exports.ResignationReview();page.requestId=9;page.lastWorkingDate='2030-01-20';page.ngOnChanges({requestId:true});return {page,requests,events};
}
test('blocked review stays disabled, checking again can become ready',()=>{
 const f=fixture();assert.equal(f.page.loading,true);assert.equal(f.events.at(-1),false);
 f.requests[0].subject.next({canApprove:false,issues:[{code:'CONTRACT',count:2}]});assert.equal(f.events.at(-1),false);assert.equal(f.page.result.issues[0].count,2);
 f.page.check();assert.equal(f.events.at(-1),false);f.requests[1].subject.next({canApprove:true,issues:[]});assert.equal(f.events.at(-1),true);
});
test('changing final date invalidates the old result and ignores late responses',()=>{
 const f=fixture();f.requests[0].subject.next({canApprove:true,issues:[]});
 f.page.lastWorkingDate='2030-01-22';f.page.ngOnChanges({lastWorkingDate:true});assert.equal(f.page.result,null);assert.equal(f.events.at(-1),false);
 f.requests[0].subject.next({canApprove:true,issues:[]});assert.equal(f.page.result,null);assert.equal(f.requests[1].date,'2030-01-22');
 f.requests[1].subject.error(new Error('offline'));assert.equal(f.page.loading,false);assert.equal(f.page.error,'Không thể kiểm tra');assert.equal(f.events.at(-1),false);
});
test('closing a request cannot reopen its review; wall-clock dates keep their hour',()=>{
 const f=fixture();f.page.ngOnDestroy();f.requests[0].subject.next({canApprove:true,issues:[]});assert.equal(f.page.result,null);
 assert.equal(f.page.recordDate('2030-01-21T08:00:00'),'21/01/2030 08:00');assert.equal(f.page.recordDate('2030-01-20'),'20/01/2030');
});
test('processing uses an in-app action and a date-only value never shifts a day',()=>{
 const f=fixture();f.page.result={employeeId:12,employeeCode:'NV012',employeeName:'Nhân viên',issues:[]};f.page.openModule('/contracts');
 assert.equal(f.events.at(-1).route,'/contracts');assert.equal(f.events.at(-1).employeeId,12);
 const count=f.events.length;f.page.openModule('https://outside.example');assert.equal(f.events.length,count);
 assert.equal(f.page.recordDate('2026-12-21'),'21/12/2026');
});
