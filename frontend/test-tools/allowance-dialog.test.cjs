const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{pathToFileURL}=require('node:url'),esbuild=require('esbuild');
test('allowance deactivation waits for reason; cancelling never calls API',async()=>{
 const output=path.join(__dirname,`.allowance-dialog-${process.pid}.mjs`);
 await esbuild.build({entryPoints:[path.join(__dirname,'allowance-dialog-harness.ts')],bundle:true,packages:'external',platform:'node',format:'esm',outfile:output});
 try{const {setup}=await import(pathToFileURL(output).href),f=setup(),row={allowanceTypeId:1,typeName:'Hỗ trợ điện thoại',isActive:true};
  const cancelled=f.page.deactivateType(row);assert.equal(f.calls(),0);f.resolve(null);await cancelled;assert.equal(f.calls(),0);assert.equal(f.page.saving,false);
  const accepted=f.page.deactivateType(row);assert.equal(f.calls(),0);f.resolve('Không còn áp dụng chính sách này');await accepted;assert.equal(f.calls(),1);assert.equal(f.page.saving,true);f.injector.destroy();
 }finally{fs.rmSync(output,{force:true});}
});
