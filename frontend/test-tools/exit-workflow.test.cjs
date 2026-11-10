const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function fixture(){
 let now=1000;const signal=v=>{const fn=()=>v;fn.set=n=>v=n;return fn;};const exports={};const code=ts.transpileModule(fs.readFileSync(__dirname+'/../src/app/services/exit-workflow.service.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,experimentalDecorators:true}}).outputText;
 vm.runInNewContext(code,{exports,require:()=>({signal,Injectable:()=>c=>c}),Date:{now:()=>now}});return {flow:new exports.ExitWorkflowService(),advance:ms=>now+=ms};
}
test('same-tab handoff retains date and note only for the correct user and request',()=>{
 const {flow}=fixture();flow.save({owner:'hr-a',requestId:13,lastWorkingDate:'2026-12-21',note:'Đợi bàn giao hợp đồng',employeeLabel:'NV011'});
 assert.equal(flow.read('hr-a',13).lastWorkingDate,'2026-12-21');assert.equal(flow.read('hr-a',13).note,'Đợi bàn giao hợp đồng');
 assert.equal(flow.read('hr-b',13),null);assert.equal(flow.read('hr-a',14),null);
 flow.clear(14);assert.ok(flow.read('hr-a',13));flow.clear(13);assert.equal(flow.read('hr-a'),null);
});
test('expired handoff cannot restore stale approval input',()=>{
 const {flow,advance}=fixture();flow.save({owner:'hr-a',requestId:13,lastWorkingDate:'2026-12-21',note:'',employeeLabel:'NV011'});advance(30*60*1000+1);assert.equal(flow.read('hr-a',13),null);
});
