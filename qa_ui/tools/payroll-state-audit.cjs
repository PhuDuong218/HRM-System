const fs=require('node:fs');const {request,test,results,tokens}=require('./full-system-audit.cjs');const tag='QA6-PAY-'+Date.now().toString(36);let periodId,batchId;
(async()=>{
 for(const [actor,username]of [['admin','demo.admin'],['hr','demo.hr'],['employee','demo.emp008']])tokens[actor]=(await request('','/api/auth/login','POST',{username,password:'Demo@2026!'})).data.accessToken;
 const body={periodCode:tag,periodStart:'2040-02-01',periodEnd:'2040-02-29'};
 const p=await test('isolated-leap-year-period','admin','/api/payroll-periods',[201],null,'POST',body);periodId=p.data.periodId;if(!periodId)throw Error('Missing periodId');
 await test('duplicate-period','admin','/api/payroll-periods',[409,400],null,'POST',body);
 await test('reversed-period','admin','/api/payroll-periods',[400,409],null,'POST',{periodCode:tag+'BAD',periodStart:'2041-02-20',periodEnd:'2041-02-01'});
 const b=await test('isolated-draft','admin','/api/payroll-batches/draft',[201],null,'POST',{periodId});batchId=b.data.batchId;if(!batchId)throw Error('Missing batchId');
 await test('K8-publish-before-lock','admin',`/api/payroll-batches/${batchId}/publish`,[409,400],null,'POST',{});
 await test('K6-lock-before-calculation','admin',`/api/payroll-batches/${batchId}/lock`,[409,400],null,'POST',{});
 await test('employee-cannot-create-draft','employee','/api/payroll-batches/draft',[403],null,'POST',{periodId});
 await test('HR-cannot-publish','hr',`/api/payroll-batches/${batchId}/publish`,[403],null,'POST',{});
 await test('employee-cannot-publish','employee',`/api/payroll-batches/${batchId}/publish`,[403],null,'POST',{});
})().catch(e=>{results.push({id:'HARNESS',pass:false,error:e.message});console.log('STOP',e.message)}).finally(()=>{fs.writeFileSync('qa_ui/full-audit/payroll-state-results.json',JSON.stringify({executedAt:new Date().toISOString(),tag,periodId,batchId,results},null,2));console.log('TOTAL',results.length,'PASS',results.filter(r=>r.pass).length,'FAIL',results.filter(r=>!r.pass).length)});
