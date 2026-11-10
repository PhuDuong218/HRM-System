const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url'),esbuild=require('esbuild');
test('employee editor hydrates detail, retains failed drafts, and rejects invalid fields',async()=>{
 const output=path.join(__dirname,`.employee-form-${process.pid}.mjs`);
 await esbuild.build({entryPoints:[path.join(__dirname,'employee-form-harness.ts')],bundle:true,packages:'external',platform:'node',format:'esm',outfile:output});
 try {
  const {setup,employeeFormErrors}=await import(pathToFileURL(output).href);
  const {injector,http,page}=setup();
  const full={employeeId:5,employeeCode:'QA-KEEP',fullName:'Original name',birthDate:'1990-01-02',hireDate:'2020-01-02',email:'old@example.test',phone:'0901234567',identityNumber:'000000000123',address:'Old home',gender:'FEMALE'};
  page.selected={employeeId:5,employeeCode:'QA-KEEP',fullName:'Summary only'};page.mode='detail';page.edit('edit');
  assert.equal(page.detailLoading,true);assert.equal(page.mode,'detail');
  http.expectOne('/api/employees/5/detail').flush(full);
  assert.equal(page.mode,'edit');for(const field of ['fullName','birthDate','email','phone','identityNumber','address','gender'])assert.equal(page.form[field],full[field]);
  page.form.fullName='Changed name';page.form.address='Changed home';page.form.reason='Correction';
  const draft={...page.form};const f={invalid:false,control:{markAllAsTouched(){}},controls:{}};
  page.save(f);page.save(f);
  const put=http.expectOne('/api/employees/5');assert.equal(put.request.method,'PUT');assert.equal(put.request.body.identityNumber,'000000000123');assert.equal(put.request.body.address,'Changed home');
  put.flush({loi_chi_tiet:'Database conflict'},{status:409,statusText:'Conflict'});
  assert.deepEqual(page.form,draft);assert.equal(page.mode,'edit');assert.equal(page.saving,false);
  page.mode='detail';page.edit('contact');http.expectOne('/api/employees/5/detail').flush({message:'unavailable'},{status:500,statusText:'Error'});
  assert.deepEqual(page.form,draft);assert.equal(page.mode,'detail');assert.equal(page.detailLoading,false);
  const valid={...draft,employeeCode:'',departmentId:1,positionId:2};
  assert.deepEqual(employeeFormErrors(valid,'create','2026-11-04'),{});
  for(const [field,value,error]of [['fullName',' ','fullName'],['birthDate','2020-01-02','birthDate'],['birthDate','2026-11-05','birthDate'],['hireDate','2026-11-05','hireDate'],['email','invalid','email'],['phone','123','phone'],['identityNumber','1'.repeat(51),'identityNumber'],['address','a'.repeat(501),'address'],['departmentId',null,'departmentId'],['positionId',null,'positionId'],['gender','INVALID','gender']])assert.ok(employeeFormErrors({...valid,[field]:value},'create','2026-11-04')[error],field);
  assert.ok(employeeFormErrors({...valid,reason:'  '},'edit','2026-11-04').changeReason);
  const missing=employeeFormErrors({...valid,email:'',phone:'',identityNumber:'',address:'',birthDate:'',gender:null},'edit','2026-11-04');
  for(const field of ['email','identityNumber','address','gender'])assert.ok(missing[field],field+' is required');
  assert.deepEqual(employeeFormErrors({...valid,email:'',identityNumber:'',address:'',privateFieldsEditable:false},'edit','2026-11-04',false),{});
  page.form={...valid,email:'bad'};page.mode='edit';page.save(f);http.expectNone('/api/employees/5');assert.ok(page.fieldError('email',f));
  http.verify();injector.destroy();
 } finally{fs.rmSync(output,{force:true});}
});
