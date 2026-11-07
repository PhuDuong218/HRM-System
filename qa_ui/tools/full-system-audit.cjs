const fs = require('node:fs');
const out='qa_ui/full-audit'; fs.mkdirSync(out,{recursive:true});
const results=[], tokens={};
async function request(actor,path,method='GET',body){
 const r=await fetch('http://localhost:2005'+path,{method,headers:{'Content-Type':'application/json',...(tokens[actor]?{Authorization:'Bearer '+tokens[actor]}:{})},...(body===undefined?{}:{body:JSON.stringify(body)}),signal:AbortSignal.timeout(20000)});
 const raw=await r.text();let data;try{data=JSON.parse(raw)}catch{data=raw}return {status:r.status,data};
}
async function test(id,actor,path,expected,verify,method='GET',body){
 try{const r=await request(actor,path,method,body);const pass=expected.includes(r.status)&&(!verify||verify(r.data));
 const rows=r.data?.content|| (Array.isArray(r.data)?r.data:undefined);
 results.push({id,actor,method,path,expected,actual:r.status,pass,total:r.data?.totalElements,rows:rows?.length,message:r.status>=400?r.data?.message:undefined});console.log(pass?'PASS':'FAIL',id,actor,r.status);return r;
 }catch(e){results.push({id,actor,path,pass:false,error:e.message});console.log('ERROR',id,e.message);}
}
if(require.main===module)(async()=>{
 for(const [actor,username] of Object.entries({admin:'demo.admin',hr:'demo.hr',head:'demo.head.hcn',headDT:'demo.head.dt',employee:'demo.emp008',employeeDT:'demo.emp009'})){
  const r=await request('', '/api/auth/login','POST',{username,password:'Demo@2026!'});
  if(r.status!==200||!r.data.accessToken)throw Error('Cannot login '+actor+' HTTP '+r.status);
  tokens[actor]=r.data.accessToken;results.push({id:'LOGIN',actor,pass:true,actual:r.status});
 }
 const routes=[['profile','/employees/8/detail'],['quota','/leave-entitlements/8'],['balance','/employee-requests/balances/8'],['degrees','/employees/8/qualifications'],['degree-detail','/employee-qualifications/8'],['contract-detail','/contracts/8'],['request-detail','/employee-requests/4'],['audit-profile','/g1/audit?targetTable=employees&recordId=8'],['audit-degree','/g2/audit?targetTable=employee_qualifications&recordId=8']];
 for(const [name,path] of routes){
  await test('scope-out-'+name,'hr',path,[403]);
  await test('scope-admin-'+name,'admin',path,[200]);
  await test('scope-employee-other-'+name,'employeeDT',path,[403]);
 }
 for(const [name,path] of routes.slice(0,5)){
  await test('scope-hr-positive-'+name,'hr',path.replace(/\/8(?=\/|$|\?)/g,'/9'),[200]);
  await test('scope-owner-'+name,'employee',path,[200]);
 }
 await test('head-own-department-profile','head','/employees/8/detail',[200]);
 await test('head-other-department-profile','headDT','/employees/8/detail',[403]);
 const dates='from=2026-11-01&to=2026-11-30';
 for(const [name,path] of [['schedules','/api/management/schedules/search?'+dates+'&employeeId=8'],['attendance','/api/management/attendance?'+dates+'&employeeId=8'],['summary','/api/management/attendance/confirmed-summary?'+dates+'&employeeId=8'],['assignments','/employees/8/assignments']]){
  await test('hr-out-'+name,'hr',path,name==='assignments'?[403]:[200],name==='assignments'?null:d=>name==='summary'?d.days.length===0&&d.netMinutes===0:d.items.length===0&&d.totalElements===0);await test('admin-'+name,'admin',path,[200]);
 }
 for(const [name,path] of [['accounts','/api/admin/accounts'],['scope','/api/admin/hr-scopes'],['payroll','/api/payroll-batches'],['periods','/api/payroll-periods']]){
  await test('admin-list-'+name,'admin',path,[200]);await test('employee-block-'+name,'employee',path,[403]);
 }
 for(const [name,path] of [['employees','/employees'],['positions','/positions'],['departments','/departments'],['levels','/qualification-levels'],['types','/leave-types'],['contracts','/contracts'],['contract-types','/contract-types'],['assignments','/employee-assignments/search'],['degrees','/employee-qualifications/search'],['quota','/leave-entitlements'],['requests','/employee-requests'],['headqueue','/employee-requests/head-queue']])await test('admin-catalog-'+name,'admin',path,name==='headqueue'?[403]:[200]);
 await test('missing-dates','admin','/api/management/schedules/search',[400]);
 await test('invalid-dates','admin','/api/management/schedules/search?from=invalid&to=2026-11-30',[400]);
 await test('reversed-dates','admin','/api/management/schedules/search?from=2026-11-30&to=2026-11-01',[400]);
 await test('negative-page','admin','/employees?page=-1',[400]);
 await test('missing-profile','admin','/employees/99999999/detail',[404]);
 await test('anonymous-profile','','/employees/8/detail',[401]);
 await test('hr-scope-self-grant','hr','/api/admin/hr-scopes',[403],null,'POST',{hrAccountId:2,departmentId:1,effectiveFrom:'2026-11-01T00:00:00',reason:'QA forbidden scope attempt'});
 await test('employee-create-account','employee','/api/admin/accounts',[403],null,'POST',{employeeId:8,roleId:3,username:'QA_FORBIDDEN',password:'QA_Dummy_NotUsed_123!',confirmPassword:'QA_Dummy_NotUsed_123!',active:true});
 for(const actor of ['admin','hr'])for(const key of ['employees','contracts','work','payroll'])await test('report-'+key,actor,'/api/reports/'+key+(key==='employees'?'':'?fromDate=2026-11-01&toDate=2026-11-30'),actor==='admin'?[200]:[403]);
 for(const actor of ['hr','admin'])await test('overview-'+actor,actor,'/api/overview/hr',actor==='hr'?[200]:[403]);
 // Logout is performed on a fresh employee login; revokes only this demo account's sessions.
 await test('logout','employeeDT','/api/auth/logout',[200,204],null,'POST',{});
 await test('revoked-token','employeeDT','/api/auth/me',[401]);
})().catch(e=>{results.push({id:'HARNESS',pass:false,error:e.message});console.log(e.message)}).finally(()=>{fs.writeFileSync(out+'/api-results.json',JSON.stringify({executedAt:new Date().toISOString(),database:'hrm_center_full',results},null,2));console.log('TOTAL',results.length,'PASS',results.filter(r=>r.pass).length,'FAIL',results.filter(r=>!r.pass).length)});
module.exports={request,test,results,tokens};
