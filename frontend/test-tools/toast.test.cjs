const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function fixture(){
 const timers=new Map();let sequence=0;
 const signal=value=>{const read=()=>value;read.set=next=>value=next;read.update=fn=>value=fn(value);return read;};
 const exports={};const code=ts.transpileModule(fs.readFileSync(__dirname+'/../src/app/services/toast.service.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,experimentalDecorators:true}}).outputText;
 vm.runInNewContext(code,{exports,require:()=>({signal,Injectable:()=>c=>c}),setTimeout:(fn,ms)=>{assert.equal(ms,5000);timers.set(++sequence,fn);return sequence;},clearTimeout:id=>timers.delete(id)});
 return {service:new exports.ToastService(),timers};
}
test('errors are prioritised and successes cannot evict them',()=>{
 const {service:s}=fixture(),owners=[{},{},{}];owners.forEach((owner,i)=>s.publish(owner,'error','Error '+i));
 s.publish({},'success','Saved');assert.equal(s.notices().length,3);assert.ok(s.notices().every(n=>n.kind==='error'));
 s.publish(owners[0],'error','Corrected error');assert.equal(s.notices()[0].text,'Corrected error');
});
test('duplicate results do not stack; all notifications expire after five seconds and can be dismissed',()=>{
 const {service:s,timers}=fixture(),a={},b={};s.publish(a,'error','Failure');s.publish(b,'error','Failure');assert.equal(s.notices().length,1);
 s.publish(a,'success','Saved');assert.equal(s.notices().length,2);assert.equal(timers.size,2);
 [...timers.values()].forEach(fn=>fn());assert.equal(s.notices().length,0);
 s.publish(a,'error','New failure');
 s.dismiss(s.notices()[0].id);assert.equal(s.notices().length,0);
});
test('clearing a page removes its notices without affecting another page',()=>{
 const {service:s,timers}=fixture(),a={},b={};s.publish(a,'success','Saved');s.publish(b,'error','Other failure');s.clear(a);
 assert.equal(timers.size,1);assert.equal(s.notices().length,1);assert.equal(s.notices()[0].owner,b);
});
