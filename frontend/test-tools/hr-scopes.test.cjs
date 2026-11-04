const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const {pathToFileURL}=require('node:url');const esbuild=require('esbuild');
test('HR scopes use UTC, filter account, retain failed form and prevent double grants',async()=>{
 const output=path.join(__dirname,`.hr-scopes-${process.pid}.mjs`);await esbuild.build({entryPoints:[path.join(__dirname,'hr-scopes-harness.ts')],bundle:true,packages:'external',platform:'node',format:'esm',outfile:output});
 try{const {setup,scopeInstant,scopeLocalInput,scopeUtcInput}=await import(pathToFileURL(output).href);const {page,http,injector}=setup();
 const row={scopeId:5,hrAccountId:34,departmentId:8,effectiveFrom:'2026-01-01T00:00:15',effectiveTo:null,revokedAt:null};
 page.ngOnChanges();http.expectOne('/api/admin/hr-scopes').flush([row,{...row,scopeId:6,hrAccountId:35}]);http.expectOne('/api/departments').flush([{departmentId:8,departmentCode:'DT',departmentName:'Đào tạo',isActive:true}]);assert.equal(page.rows.length,1);
 page.save();http.expectNone('/api/admin/hr-scopes');assert.ok(page.error);
 page.departmentId=8;page.from='2030-01-01T09:00';page.to='2029-01-01T09:00';page.reason='test';page.save();http.expectNone('/api/admin/hr-scopes');assert.ok(page.error);
 page.to='';page.save();page.save();const grant=http.expectOne('/api/admin/hr-scopes');assert.equal(grant.request.method,'POST');assert.deepEqual(grant.request.body,{hrAccountId:34,departmentId:8,effectiveFrom:scopeUtcInput(page.from),effectiveTo:null,reason:'test'});
 grant.flush({message:'Phân công HR chồng lấn'},{status:409,statusText:'Conflict'});assert.equal(page.reason,'test');assert.equal(page.busy,false);assert.ok(page.error);
 page.start(row,'update');page.reason='extend';page.save();const update=http.expectOne('/api/admin/hr-scopes/5');assert.equal(update.request.method,'PUT');assert.equal(update.request.body.effectiveFrom,row.effectiveFrom);update.flush(row);
 http.expectOne('/api/admin/hr-scopes').flush([row]);http.expectOne('/api/departments').flush([]);
 assert.equal(scopeInstant('2026-01-01T00:00:00').toISOString(),'2026-01-01T00:00:00.000Z');assert.equal(scopeInstant(scopeUtcInput(scopeLocalInput('2026-01-01T00:00:00'))).toISOString(),'2026-01-01T00:00:00.000Z');
 global.confirm=()=>true;page.start(row,'revoke');page.reason='test revoke';page.save();const revoke=http.expectOne('/api/admin/hr-scopes/5/revoke');assert.deepEqual(revoke.request.body,{reason:'test revoke'});revoke.flush({...row,revokedAt:'2026-11-03T10:00:00'});http.expectOne('/api/admin/hr-scopes').flush([]);http.expectOne('/api/departments').flush([]);
 http.verify();injector.destroy();delete global.confirm;
 }finally{fs.rmSync(output,{force:true});}
});
