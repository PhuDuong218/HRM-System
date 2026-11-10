const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript');
const root=path.resolve(__dirname,'../src/app');
function fixture(){
 const signal=v=>{const fn=()=>v;fn.set=n=>v=n;return fn;};const deco=()=>c=>c;
 const exports={};const code=ts.transpileModule(fs.readFileSync(root+'/shared/ui/action-dialog.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,experimentalDecorators:true}}).outputText;
 vm.runInNewContext(code,{exports,require:name=>name==='@angular/core'?{signal,Injectable:deco,Component:deco,ViewChild:deco}:{}});
 return new exports.ActionDialogService();
}
test('confirmation waits for a user choice; cancel resolves false',async()=>{
 const service=fixture();let resolved=false;const pending=service.confirm('Proceed?').then(value=>{resolved=true;return value;});
 await Promise.resolve();assert.equal(resolved,false);assert.equal(service.state().prompt,false);
 service.finish(null);assert.equal(await pending,false);assert.equal(service.state(),null);
 const accepted=service.confirm('Proceed?');service.finish(true);assert.equal(await accepted,true);
});
test('reason dialog returns text or null, and duplicate opens cannot replace active choice',async()=>{
 const service=fixture(),pending=service.prompt('Reason?');assert.equal(service.state().prompt,true);
 assert.equal(await service.confirm('Other action'),false);assert.equal(service.state().message,'Reason?');
 service.finish('A valid reason');assert.equal(await pending,'A valid reason');
 const cancelled=service.prompt('Reason?');service.finish(null);assert.equal(await cancelled,null);
});
test('no source calls native alert, confirm or prompt',()=>{
 function walk(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(dir,e.name)):[path.join(dir,e.name)]);}
 const found=[];for(const file of walk(root).filter(p=>p.endsWith('.ts'))){const source=fs.readFileSync(file,'utf8'),ast=ts.createSourceFile(file,source,ts.ScriptTarget.Latest,true);
 function visit(node){if(ts.isCallExpression(node)&&/^(?:window\.)?(alert|confirm|prompt)$/.test(node.expression.getText(ast)))found.push(path.relative(root,file));ts.forEachChild(node,visit);}visit(ast);}
 assert.deepEqual(found,[]);
});
