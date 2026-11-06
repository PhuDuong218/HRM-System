const fs=require('node:fs');const {request,test,results,tokens}=require('./full-system-audit.cjs');
const f=JSON.parse(fs.readFileSync('qa_ui/full-audit/write-results.json')).fixture;
const race=JSON.parse(fs.readFileSync('qa_ui/full-audit/leave-race-results.json'));const requests=[];
function must(r,key){if(!r?.data?.[key])throw Error('Missing '+key+' HTTP '+r?.status);return r.data[key]}
(async()=>{
 for(const [actor,username]of [['admin','demo.admin'],['hr','demo.hr'],['head','demo.head.dt'],['otherHead','demo.head.hcn']])tokens[actor]=(await request('','/api/auth/login','POST',{username,password:'Demo@2026!'})).data.accessToken;
 await test('QA-enable-account','admin',`/api/admin/accounts/${race.account}/status`,[200],null,'PATCH',{expectedActive:false,active:true});
 tokens.worker=must(await request('','/api/auth/login','POST',{username:f.tag+'.worker',password:'QAonly_LeaveRace_2026!'}),'accessToken');
 const leave={leaveTypeId:1,leaveStart:'2026-11-10T08:00:00',leaveEnd:'2026-11-10T12:00:00',reason:f.tag};
 await test('I1-reversed-interval','worker','/employee-requests/leave',[400],null,'POST',{...leave,leaveStart:leave.leaveEnd,leaveEnd:leave.leaveStart});
 await test('I1-no-schedule','worker','/employee-requests/leave',[400,409],null,'POST',{...leave,leaveStart:'2026-11-12T08:00:00',leaveEnd:'2026-11-12T12:00:00'});
 await test('I1-backdate-over-seven-days','worker','/employee-requests/leave',[400,409],null,'POST',{...leave,leaveStart:'2026-10-01T08:00:00',leaveEnd:'2026-10-01T12:00:00'});
 await test('I19-blank-leave-reason','worker','/employee-requests/leave',[400],null,'POST',{...leave,reason:'  '});
 const submit=await test('M5-cannot-submit-for-other-employee','worker','/employee-requests/leave',[201],d=>d.employeeId===f.employeeId,'POST',{...leave,employeeId:8,status:'DA_DUYET',approvedBy:1});const rid=must(submit,'requestId');requests.push(rid);
 await test('I4-overlapping-pending','worker','/employee-requests/leave',[409],null,'POST',leave);
 await test('I12-final-before-first','hr',`/employee-requests/${rid}/approve`,[409],null,'POST',{});
 await test('I8-other-head-decision','otherHead',`/employee-requests/${rid}/head-decision`,[403],null,'POST',{approve:true,note:f.tag});
 await test('I10-blank-head-reject','head',`/employee-requests/${rid}/head-decision`,[400],null,'POST',{approve:false,note:'  '});
 await test('I8-head-approve','head',`/employee-requests/${rid}/head-decision`,[200],null,'POST',{approve:true,note:f.tag});
 await test('I10-head-approve-twice','head',`/employee-requests/${rid}/head-decision`,[409],null,'POST',{approve:true,note:f.tag});
 await test('I12-hr-outside-scope-final','hr','/employee-requests/4/approve',[403],null,'POST',{});
 await test('I19-blank-final-reject','hr',`/employee-requests/${rid}/reject`,[400],null,'POST',{reason:' '});
 await test('I3-half-day-final','hr',`/employee-requests/${rid}/approve`,[200],null,'POST',{note:f.tag});
 await test('I3-half-day-four-hours','admin',`/employee-requests/balances/${f.employeeId}`,[200],d=>{const x=d.find(q=>q.entitlementId===race.quota);return +x.usedHours===4&&+x.remainingHours===4;});
 await test('I17-employee-cannot-cancel-approved','worker',`/employee-requests/${rid}/cancel-approved`,[403],null,'POST',{reason:f.tag});
 await test('I17-admin-cancel-half-day','admin',`/employee-requests/${rid}/cancel-approved`,[200],null,'POST',{reason:f.tag+' restore'});
 const next=await test('I4-resubmit-after-cancel','worker','/employee-requests/leave',[201],null,'POST',leave);requests.push(must(next,'requestId'));
 await test('I10-first-approve-next','head',`/employee-requests/${requests[1]}/head-decision`,[200],null,'POST',{approve:true,note:f.tag});
 await test('I10-withdraw-after-stage-one','worker',`/employee-requests/${requests[1]}/withdraw`,[200],null,'POST',{reason:f.tag});
 await test('I10-cannot-final-after-withdraw','hr',`/employee-requests/${requests[1]}/approve`,[409],null,'POST',{});
 const eid=f.missingFieldsEmployeeId;
 await test('E-future-transfer','admin',`/employees/${eid}/transfer`,[200],null,'PUT',{departmentId:3,positionId:5,effectiveFrom:'2026-11-25',reason:f.tag+' future transfer'});
 await test('L3-block-exit-before-future-transfer','admin',`/employees/${eid}/termination-decisions`,[409],null,'POST',{lastWorkingDate:'2026-11-20',decisionType:'MUTUAL_AGREEMENT',decisionNumber:f.tag+'BLOCKEXIT',reason:f.tag});
 await test('M1-zero-page-size','admin','/employees/search?page=0&size=0',[400]);
 await test('M1-excessive-page-size','admin','/employees/search?page=0&size=100000',[400]);
 await test('M3-overlong-employee-name','admin','/employees',[400],null,'POST',{employeeCode:f.tag+'LONG',fullName:'a'.repeat(10000),hireDate:'2026-01-01',departmentId:2,positionId:4});
})().catch(e=>{results.push({id:'HARNESS',pass:false,error:e.message});console.log('STOP',e.message)}).finally(async()=>{
 for(const rid of requests){const r=await request('admin',`/employee-requests/${rid}`);if(r.status===200&&!r.data.withdrawnAt){const approved=r.data.status==='DA_DUYET';await test('cleanup-'+rid,approved?'admin':'worker',`/employee-requests/${rid}/${approved?'cancel-approved':'withdraw'}`,[200,204],null,'POST',{reason:f.tag+' cleanup'});}}
 await test('QA-disable-account','admin',`/api/admin/accounts/${race.account}/status`,[200],null,'PATCH',{expectedActive:true,active:false});
 fs.writeFileSync('qa_ui/full-audit/workflow-results.json',JSON.stringify({executedAt:new Date().toISOString(),fixture:f,requests,results},null,2));console.log('TOTAL',results.length,'PASS',results.filter(r=>r.pass).length,'FAIL',results.filter(r=>!r.pass).length);
});
