const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {pathToFileURL}=require('node:url');
const esbuild=require('esbuild');

test('account workflows send real DTOs, prevent duplicate submits and retain failed forms',async()=>{
 const output=path.join(__dirname,`.accounts-workflow-${process.pid}.mjs`);
 await esbuild.build({entryPoints:[path.join(__dirname,'accounts-harness.ts')],bundle:true,packages:'external',platform:'node',format:'esm',outfile:output});
 try{
  const {setup,toListPage}=await import(pathToFileURL(output).href);
  const {page,http,injector,closed}=setup();
  page.openDialog('account-create');
  http.expectOne('/api/employees').flush([{employeeId:12,employeeCode:'E12',fullName:'HR',employmentStatus:'DANG_LAM'},{employeeId:13,employmentStatus:'DA_NGHI'}]);
  assert.equal(page.employees.length,1);
  Object.assign(page.draft,{employeeId:12,roleId:2,username:'new.hr',password:'TestPass123!',confirmPassword:'mismatch'});
  page.saveDialog();http.expectNone('/api/admin/accounts');assert.ok(page.actionError);
  page.draft.confirmPassword=page.draft.password;
  page.saveDialog();page.saveDialog();
  const create=http.expectOne('/api/admin/accounts');
  assert.equal(create.request.method,'POST');
  assert.deepEqual(create.request.body,{employeeId:12,roleId:2,username:'new.hr',active:true,password:'TestPass123!',confirmPassword:'TestPass123!'});
  create.flush({loi_chi_tiet:'Tên đăng nhập đã tồn tại'},{status:409,statusText:'Conflict'});
  assert.equal(page.saving,false);assert.equal(closed(),0);assert.equal(page.dialogMode,'account-create');assert.match(page.actionError,/đã tồn tại/);
  page.draft.username='unique.hr';page.saveDialog();http.expectOne('/api/admin/accounts').flush({accountId:8});
  http.expectOne(r=>r.url==='/api/admin/accounts'&&r.method==='GET').flush({items:[],page:0,size:10,totalPages:0,totalElements:0});
  assert.equal(closed(),1);assert.equal(page.draft.password,'');
  page.openDialog('role-permissions',{roleId:2,roleCode:'CUSTOM',roleName:'HR',permissions:[{permissionId:4}]});
  http.expectOne('/api/admin/permissions').flush([{permissionId:4},{permissionId:5}]);
  page.togglePermission(5,true);page.saveDialog();
  const permissions=http.expectOne('/api/admin/roles/2/permissions');assert.equal(permissions.request.method,'PUT');assert.deepEqual(permissions.request.body,{permissionIds:[4,5]});permissions.flush({});
  http.expectOne('/api/admin/roles').flush([]);
  const account={accountId:8,employeeId:12,roleId:2,username:'unique.hr',active:true};
  page.openDialog('account-lock',account);page.saveDialog();
  const lock=http.expectOne('/api/admin/accounts/8');assert.equal(lock.request.method,'PUT');
  assert.deepEqual(lock.request.body,{roleId:2,username:'unique.hr',active:false});lock.flush({});
  http.expectOne(r=>r.url==='/api/admin/accounts'&&r.method==='GET').flush({items:[],page:0,size:10,totalPages:0,totalElements:0});
  page.openDialog('account-reset',account);Object.assign(page.draft,{password:'NewPass123!',confirmPassword:'NewPass123!'});page.saveDialog();
  const reset=http.expectOne('/api/admin/accounts/8/reset-password');assert.deepEqual(reset.request.body,{newPassword:'NewPass123!',confirmPassword:'NewPass123!'});reset.flush(null);
  http.expectOne(r=>r.url==='/api/admin/accounts'&&r.method==='GET').flush({items:[],page:0,size:10,totalPages:0,totalElements:0});
  page.openDialog('role-delete',{roleId:1,roleCode:'ADMIN',roleName:'Admin',permissions:[]});page.saveDialog();
  http.expectNone('/api/admin/roles/1');page.closeDialog();
  assert.deepEqual(toListPage({content:['a'],number:2,totalPages:4,totalElements:31}),{rows:['a'],page:2,pages:4,total:31});
  assert.deepEqual(toListPage({items:['b'],page:1,size:10,totalPages:2,totalElements:11}),{rows:['b'],page:1,pages:2,total:11});
  http.verify();injector.destroy();
 }finally{fs.unlinkSync(output);}
});
