const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url'),esbuild=require('esbuild');
test('employee draft carries read version, preserves conflicts and explicitly reloads latest',async()=>{
 const output=path.join(__dirname,`.employee-version-${process.pid}.mjs`);
 await esbuild.build({entryPoints:[path.join(__dirname,'employee-form-harness.ts')],bundle:true,packages:'external',platform:'node',format:'esm',outfile:output});
 try{
  const {setup,employeeFormErrors}=await import(pathToFileURL(output).href);const {injector,http,page}=setup();
  const full={employeeId:700,employeeCode:'QAFIX-TEST',fullName:'Before',birthDate:'1990-01-01',hireDate:'2020-01-01',email:'qafix@example.invalid',phone:'0901234588',identityNumber:'099900000006',address:'QAFIX address',version:'0000000000000001'};
  page.selected=full;page.mode='detail';page.edit('edit');http.expectOne('/api/employees/700/detail').flush(full);
  assert.equal(page.form.version,full.version);page.form.fullName='My unsaved draft';page.form.reason='QAFIX correction';const draft={...page.form};
  const form={invalid:false,control:{markAllAsTouched(){}},controls:{}};page.save(form);
  const put=http.expectOne('/api/employees/700');assert.equal(put.request.body.version,full.version);
  put.flush({loi_chi_tiet:'Hồ sơ đã được người khác thay đổi. Hãy tải lại để xem bản mới nhất.'},{status:409,statusText:'Conflict'});
  assert.deepEqual(page.form,draft);assert.equal(page.conflictOpen,true);page.keepDraft();assert.deepEqual(page.form,draft);
  page.reloadConflict();http.expectOne('/api/employees/700/detail').flush({...full,version:'0000000000000002',fullName:'New server name'});
  assert.equal(page.form.version,'0000000000000002');assert.equal(page.form.fullName,'New server name');assert.equal(page.conflictOpen,false);
  page.i18n.current=()=> 'en';assert.equal(page.profileText('Hồ sơ đã thay đổi'),'Employee profile has changed');assert.match(page.profileText('Hồ sơ đã được người khác thay đổi. Hãy tải lại để xem bản mới nhất.'),/Someone else/);page.i18n.current=()=> 'vi';
  for(const value of ['123456789','099900000006',' 099900000006 '])assert.equal(employeeFormErrors({...draft,identityNumber:value},'edit','2026-11-06').identityNumber,undefined);
  for(const value of ['CCCDabc','12345678','1234567890','12345678901','1234567890123','123 456789012'])assert.ok(employeeFormErrors({...draft,identityNumber:value},'edit','2026-11-06').identityNumber);
  http.verify();injector.destroy();
 }finally{fs.rmSync(output,{force:true});}
});
