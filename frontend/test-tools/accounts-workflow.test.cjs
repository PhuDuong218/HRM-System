const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const {pathToFileURL}=require('node:url');const esbuild=require('esbuild');
test('account role edits keep the username, retain failed forms and prevent double submits',async()=>{
 const output=path.join(__dirname,`.accounts-workflow-${process.pid}.mjs`);
 await esbuild.build({entryPoints:[path.join(__dirname,'account-create-harness.ts')],bundle:true,packages:'external',platform:'node',format:'esm',outfile:output});
 try{const {setup}=await import(pathToFileURL(output).href);const {page,http,injector}=setup();const account={accountId:34,employeeId:34,username:'hr.sample',roleId:2,roleCode:'HR',active:true};
 page.openDialog('account-view',account);http.expectOne('/api/admin/accounts/34/contact').flush({email:null,phone:null});http.expectOne('/api/admin/hr-scopes/account/34/active-departments').flush([]);
 page.showDetailAction('account-edit');page.editRoleId=0;page.saveAccountIdentity();http.expectNone('/api/admin/accounts/34/identity');assert.ok(page.actionError);
 page.roles.push({roleId:3,roleCode:'EMPLOYEE',roleName:'Nhân viên',permissions:[]});page.editRoleId=3;page.saveAccountIdentity();page.saveAccountIdentity();const request=http.expectOne('/api/admin/accounts/34/identity');assert.equal(request.request.method,'PATCH');assert.deepEqual(request.request.body,{expectedUsername:'hr.sample',expectedRoleId:2,username:'hr.sample',roleId:3});request.flush({loi_chi_tiet:'Trạng thái đã thay đổi'},{status:409,statusText:'Conflict'});assert.equal(page.selectedAccount.username,'hr.sample');assert.equal(page.editRoleId,3);assert.equal(page.dialogMode,'account-edit');assert.equal(page.saving,false);assert.ok(page.actionError);http.verify();injector.destroy();
 }finally{fs.rmSync(output,{force:true});}
});
