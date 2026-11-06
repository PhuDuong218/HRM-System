const fs=require('node:fs');const {request,test,results,tokens}=require('./full-system-audit.cjs');const f=JSON.parse(fs.readFileSync('qa_ui/full-audit/write-results.json')).fixture;let original;
(async()=>{
 for(const [actor,username]of [['admin','demo.admin'],['hr','demo.hr']])tokens[actor]=(await request('','/api/auth/login','POST',{username,password:'Demo@2026!'})).data.accessToken;
 original=(await request('admin',`/employees/${f.employeeId}/detail`)).data;
 const body={fullName:original.fullName,birthDate:original.birthDate,email:original.email,phone:original.phone,identityNumber:original.identityNumber,address:original.address,gender:original.gender,reason:f.tag+' stale test'};
 await test('D11-admin-saves-new-name','admin',`/employees/${f.employeeId}`,[200],null,'PUT',{...body,fullName:'QA tên mới ở phiên A'});
 const stale=await request('hr',`/employees/${f.employeeId}`,'PUT',{...body,phone:'0901234599'});
 const after=(await request('admin',`/employees/${f.employeeId}/detail`)).data;
 const pass=stale.status===409||stale.status===412||(stale.status===200&&after.fullName==='QA tên mới ở phiên A');
 results.push({id:'D11-stale-profile-must-not-silently-overwrite',actual:stale.status,pass,evidence:{newNameRetained:after.fullName==='QA tên mới ở phiên A',oldNameOverwrote:after.fullName===original.fullName}});console.log(pass?'PASS':'FAIL','D11-stale-profile',stale.status);
 await test('D5-invalid-ID-letters','admin',`/employees/${f.employeeId}`,[400],null,'PUT',{...body,identityNumber:'CCCDabc'});
})().catch(e=>results.push({id:'HARNESS',pass:false,error:e.message})).finally(async()=>{
 if(original)await test('restore-QA-profile','admin',`/employees/${f.employeeId}`,[200],null,'PUT',{fullName:original.fullName,birthDate:original.birthDate,email:original.email,phone:original.phone,identityNumber:original.identityNumber,address:original.address,gender:original.gender,reason:f.tag+' restore QA profile'});
 fs.writeFileSync('qa_ui/full-audit/stale-profile-results.json',JSON.stringify({executedAt:new Date().toISOString(),results},null,2));console.log('TOTAL',results.length,'PASS',results.filter(r=>r.pass).length,'FAIL',results.filter(r=>!r.pass).length);
});
