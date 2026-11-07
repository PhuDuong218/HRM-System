const fs=require('node:fs');const {request,test,results,tokens}=require('./full-system-audit.cjs');
const fixture=JSON.parse(fs.readFileSync('qa_ui/full-audit/write-results.json')).fixture;
const ids=[];let account,quota;
function assert(id,pass,evidence){results.push({id,pass,evidence});console.log(pass?'PASS':'FAIL',id)}
function required(r,key){if(!r?.data?.[key])throw Error('Fixture missing '+key+': HTTP '+r?.status);return r.data[key]}
(async()=>{
 for(const [actor,username]of [['admin','demo.admin'],['hr','demo.hr'],['head','demo.head.dt']])tokens[actor]=(await request('','/api/auth/login','POST',{username,password:'Demo@2026!'})).data.accessToken;
 const username=fixture.tag+'.worker',password='QAonly_LeaveRace_2026!';
 const a=await test('create-test-employee-account','admin','/api/admin/accounts',[201,200],null,'POST',{employeeId:fixture.employeeId,roleId:3,username,password,confirmPassword:password,active:true});account=required(a,'accountId');
 const l=await request('','/api/auth/login','POST',{username,password});tokens.worker=required(l,'accessToken');
 const q=await test('eight-hour-quota','admin','/leave-entitlements',[201],null,'POST',{employeeId:fixture.employeeId,leaveTypeId:1,periodStart:'2026-01-01',periodEnd:'2027-01-01',entitledHours:8,reason:fixture.tag+' race'});quota=required(q,'entitlementId');
 for(const day of [10,11]){
  const r=await test('submit-'+day,'worker','/employee-requests/leave',[201],null,'POST',{leaveTypeId:1,leaveStart:`2026-11-${day}T08:00:00`,leaveEnd:`2026-11-${day}T16:00:00`,reason:fixture.tag});
  const rid=required(r,'requestId');ids.push(rid);
  await test('employee-cannot-final-approve-'+day,'worker',`/employee-requests/${rid}/approve`,[403],null,'POST',{});
  await test('head-first-'+day,'head',`/employee-requests/${rid}/head-decision`,[200],null,'POST',{approve:true,note:fixture.tag});
 }
 const race=await Promise.all(ids.map(rid=>request('admin',`/employee-requests/${rid}/approve`,'POST',{note:fixture.tag})));
 assert('two-approvals-only-eight-hours',JSON.stringify(race.map(r=>r.status).sort())==='[200,409]',race.map(r=>r.status));
 const balance=await request('admin',`/employee-requests/balances/${fixture.employeeId}`);const b=balance.data.find(x=>x.entitlementId===quota);
 assert('balance-never-negative',+b.usedHours===8&&+b.remainingHours===0,{used:b.usedHours,remaining:b.remainingHours});
 const winner=ids[race.findIndex(r=>r.status===200)];
 if(!winner)throw Error('No winning approval');
 const cancels=await Promise.all([1,2].map(()=>request('admin',`/employee-requests/${winner}/cancel-approved`,'POST',{reason:fixture.tag+' restore'})));
 assert('double-cancel-refunds-once',JSON.stringify(cancels.map(r=>r.status).sort())==='[200,409]',cancels.map(r=>r.status));
 const restored=(await request('admin',`/employee-requests/balances/${fixture.employeeId}`)).data.find(x=>x.entitlementId===quota);
 assert('balance-restored-to-eight',+restored.usedHours===0&&+restored.remainingHours===8,{used:restored.usedHours,remaining:restored.remainingHours});
})().catch(e=>{results.push({id:'HARNESS',pass:false,error:e.message});console.log('STOP',e.message)}).finally(async()=>{
 for(const rid of ids){const r=await request('admin',`/employee-requests/${rid}`);if(r.status===200&&!r.data.withdrawnAt){const approved=r.data.status==='DA_DUYET';await test('cleanup-request-'+rid,approved?'admin':'worker',`/employee-requests/${rid}/${approved?'cancel-approved':'withdraw'}`,[200,204],null,'POST',{reason:fixture.tag+' cleanup'});}}
 if(account)await test('disable-test-account','admin',`/api/admin/accounts/${account}/status`,[200,204],null,'PATCH',{expectedActive:true,active:false});
 fs.writeFileSync('qa_ui/full-audit/leave-race-results.json',JSON.stringify({executedAt:new Date().toISOString(),fixture,account,quota,ids,results},null,2));console.log('TOTAL',results.length,'PASS',results.filter(r=>r.pass).length,'FAIL',results.filter(r=>!r.pass).length);
});
