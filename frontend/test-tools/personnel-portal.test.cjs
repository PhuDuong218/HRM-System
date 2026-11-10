const {test}=require('node:test');const assert=require('node:assert/strict');
const fs=require('node:fs');const path=require('node:path');const {pathToFileURL}=require('node:url');const esbuild=require('esbuild');
test('head employee portal is server-verified and restricted to requests and scoped employee reads',async()=>{
 const file=path.join(__dirname,`.portal-${process.pid}.mjs`);await esbuild.build({entryPoints:[path.join(__dirname,'personnel-portal-harness.ts')],bundle:true,packages:'external',platform:'node',format:'esm',outfile:file});
 global.sessionStorage={getItem:()=>null,setItem(){},removeItem(){}};
 try{const {setup}=await import(pathToFileURL(file).href);const {injector,auth,http,guard}=setup();
  const token='header.'+Buffer.from(JSON.stringify({role:'EMPLOYEE'})).toString('base64url')+'.signature';
  let loggedIn=false;auth.login('head','secret').subscribe(()=>loggedIn=true);
  http.expectOne('/api/auth/web/login').flush({accessToken:token,tokenType:'Bearer',expiresInSeconds:3600});
  const me=http.expectOne('/api/auth/me');assert.equal(me.request.headers.get('Authorization'),'Bearer '+token);me.flush({role:'EMPLOYEE',fullName:'Head'});
  http.expectOne('/api/employee-requests/capabilities').flush({departmentHead:true});assert.equal(loggedIn,true);
  let route;guard('/employees').subscribe(value=>route=value);http.expectOne('/api/auth/me').flush({role:'EMPLOYEE',fullName:'Head'});http.expectOne('/api/employee-requests/capabilities').flush({departmentHead:true});assert.equal(route,true);
  guard('/accounts').subscribe(value=>route=value);http.expectOne('/api/auth/me').flush({role:'EMPLOYEE',fullName:'Head'});http.expectOne('/api/employee-requests/capabilities').flush({departmentHead:true});assert.equal(route,'/requests/head');
  guard('/requests/head').subscribe(value=>route=value);http.expectOne('/api/auth/me').flush({role:'EMPLOYEE',fullName:'Head'});http.expectOne('/api/employee-requests/capabilities').flush({departmentHead:true});assert.equal(route,true);
  let denied=false;auth.login('ordinary','secret').subscribe({error:()=>denied=true});http.expectOne('/api/auth/web/login').flush({accessToken:token,tokenType:'Bearer',expiresInSeconds:3600});http.expectOne('/api/auth/me').flush({role:'EMPLOYEE',fullName:'Employee'});http.expectOne('/api/employee-requests/capabilities').flush({departmentHead:false});assert.equal(denied,true);assert.equal(auth.token(),null);
  http.verify();injector.destroy();
 }finally{fs.unlinkSync(file);delete global.sessionStorage;}
});
