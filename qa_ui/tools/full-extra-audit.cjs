const fs=require('node:fs');const {request,test,results,tokens}=require('./full-system-audit.cjs');
(async()=>{
 for(const [actor,username]of [['admin','demo.admin'],['hr','demo.hr'],['employee','demo.emp008']])tokens[actor]=(await request('','/api/auth/login','POST',{username,password:'Demo@2026!'})).data.accessToken;
 const rows=d=>d?.content||d?.items||(Array.isArray(d)?d:[]);
 for(const path of ['/employees','/employees?page=0&size=100','/employees/search?keyword=DEMO008','/contracts?employeeId=8','/employee-assignments/search?employeeId=8','/employee-qualifications/search?employeeId=8'])await test('HR-scope-list','hr',path,[200,403],d=>!rows(d).some(r=>r.employeeId===8||r.employee?.employeeId===8));
 for(const path of ['/employees/8/allowances','/employees/8/allowance-calculations?periodId=1','/api/payslips/preview/employee/8/html'])await test('HR-scope-financial','hr',path,[403]);
 for(const path of ['/employees/9/allowances','/employees/9/allowance-calculations?periodId=1','/api/payslips/preview/employee/9/html'])await test('HR-positive-financial','hr',path,[200]);
 await test('employee-payslip-other-id','employee','/api/payslips/my-payslips?employeeId=9',[403]);
 await test('employee-own-payslips','employee','/api/payslips/my-payslips',[200]);
 for(const path of ['/api/payroll-batches/1/details','/api/payroll-batches/period/1','/api/payroll-periods/1'])await test('employee-denied-payroll','employee',path,[403]);
 for(const q of ['%', '_', "'",'"','han','Hân'])await test('search-special-and-unicode','admin','/employees/search?keyword='+encodeURIComponent(q),[200]);
 for(const key of ['employees','contracts','work','payroll']){
  const path='/api/reports/'+key+'/export'+(key==='employees'?'':'?fromDate=2026-11-01&toDate=2026-11-30');
  for(const actor of ['admin','hr','employee']){
   const r=await fetch('http://localhost:2005'+path,{headers:{Authorization:'Bearer '+tokens[actor]},signal:AbortSignal.timeout(30000)});const bytes=new Uint8Array(await r.arrayBuffer());
   const pass=actor==='admin'?r.status===200&&bytes[0]===80&&bytes[1]===75:r.status===403;
   results.push({id:'export-'+key,actor,path,actual:r.status,pass,bytes:bytes.length,contentType:r.headers.get('content-type')});console.log(pass?'PASS':'FAIL','export-'+key,actor,r.status);
  }
 }
 await test('anonymous-no-db-diagnostics','','/api/health',[200,401]);
})().catch(e=>results.push({id:'HARNESS',pass:false,error:e.message})).finally(()=>{fs.writeFileSync('qa_ui/full-audit/extra-results.json',JSON.stringify({executedAt:new Date().toISOString(),results},null,2));console.log('TOTAL',results.length,'PASS',results.filter(r=>r.pass).length,'FAIL',results.filter(r=>!r.pass).length)});
