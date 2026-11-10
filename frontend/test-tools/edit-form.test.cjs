const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function fixture(){
 const listeners={},attributes=new Set(),notices=[],render=[];let revealed=0;
 const control=(name,value,type='text',audit=false)=>({name,id:name,value,type,checked:false,closest:()=>audit?{}:null});
 const controls=[control('name','Tên cũ'),control('amount','12','number'),control('reason','','text',true)];
 const form={isConnected:true,querySelectorAll:()=>controls,addEventListener:(key,fn)=>listeners[key]=fn,removeEventListener:key=>delete listeners[key],setAttribute:name=>attributes.add(name),toggleAttribute:(name,on)=>on?attributes.add(name):attributes.delete(name),getClientRects:()=>[{}],style:{},scrollIntoView:()=>revealed++,focus(){}};
 const ElementRef=class ElementRef{},Injector=class Injector{},ToastService=class ToastService{};
 const angular={Directive:()=>c=>c,Input:()=>()=>{},ElementRef,Injector,inject:token=>token===ElementRef?{nativeElement:form}:token===ToastService?{publish:(owner,kind,text)=>notices.push({kind,text}),clear(){}}:{},afterNextRender:fn=>{render.push(fn);return{destroy(){}};}};
 const exports={};const code=ts.transpileModule(fs.readFileSync(__dirname+'/../src/app/shared/edit-form.directive.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,experimentalDecorators:true}}).outputText;
 vm.runInNewContext(code,{exports,require:name=>name==='@angular/core'?angular:{ToastService}});
 const directive=new exports.EditFormDirective();directive.hrmEditForm=true;
 const start=()=>{directive.ngOnChanges();render.splice(0).forEach(fn=>fn());};start();
 const submit=()=>{let blocked=false;listeners.submit({preventDefault:()=>blocked=true,stopImmediatePropagation:()=>blocked=true});return blocked;};
 return {directive,controls,notices,start,submit,listeners,attributes,revealed:()=>revealed};
}
test('an unchanged update and audit-only change are blocked before submit handler',()=>{
 const f=fixture();assert.equal(f.submit(),true);f.controls[2].value='Lý do cập nhật';assert.equal(f.submit(),true);assert.equal(f.notices.length,2);
});
test('changes can save; reverting to original data is unchanged again',()=>{
 const f=fixture();f.controls[0].value='Tên mới';f.listeners.input();assert.equal(f.submit(),false);assert.equal(f.attributes.has('data-hrm-unchanged'),false);
 f.controls[0].value='Tên cũ';f.listeners.input();assert.equal(f.submit(),true);assert.equal(f.attributes.has('data-hrm-unchanged'),true);
 f.controls[1].value='12.00';assert.equal(f.submit(),true);
});
test('create actions are not blocked; editor is revealed after render',()=>{
 const f=fixture();assert.equal(f.revealed(),1);f.directive.hrmEditForm=false;f.start();assert.equal(f.submit(),false);
});
test('successful persistent editor can rebase while failed edits remain retryable',()=>{
 const f=fixture();f.controls[0].value='Tên đã cập nhật';assert.equal(f.submit(),false);assert.equal(f.submit(),false);
 f.directive.hrmEditSaved++;f.start();assert.equal(f.submit(),true);
});
