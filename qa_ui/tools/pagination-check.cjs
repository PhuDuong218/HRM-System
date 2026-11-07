const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const ts=require('../../frontend/node_modules/typescript');
const source=fs.readFileSync('frontend/src/app/shared/pagination.ts','utf8');
const compiled=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,experimentalDecorators:true}}).outputText;
const exportsObj={};const decorator=()=>()=>{};class Emitter{values=[];emit(n){this.values.push(n)}}
vm.runInNewContext(compiled,{exports:exportsObj,require:()=>({Component:decorator,Input:decorator,Output:decorator,EventEmitter:Emitter,TranslatePipe:class{},HrmButton:class{},HrmIcon:class{}})});
const p=new exportsObj.Pagination();p.go(0);assert.equal(p.pageChange.values.length,0);
p.pages=8;p.go(-1);p.go(8);p.go(0);assert.equal(p.pageChange.values.length,0);
p.go(7);assert.deepEqual(p.pageChange.values,[7]);p.busy=true;p.go(1);assert.deepEqual(p.pageChange.values,[7]);
p.busy=false;p.page=7;assert.equal(JSON.stringify(p.numbers),'[5,6,7]');p.page=0;assert.equal(JSON.stringify(p.numbers),'[0,1,2]');p.pages=0;assert.equal(p.numbers.length,0);
console.log('PASS: empty list, lower/upper boundary, current page, busy lock and numbered page window.');
