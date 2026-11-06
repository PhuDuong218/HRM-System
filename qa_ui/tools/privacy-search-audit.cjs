const fs=require('node:fs');const {request,test,results,tokens}=require('./full-system-audit.cjs');const f=JSON.parse(fs.readFileSync('qa_ui/full-audit/write-results.json')).fixture;
(async()=>{
 for(const [actor,username]of [['admin','demo.admin'],['hr','demo.hr'],['head','demo.head.dt']])tokens[actor]=(await request('','/api/auth/login','POST',{username,password:'Demo@2026!'})).data.accessToken;
 for(const actor of ['admin','hr','head']){
  await test('private-fields-authorized',actor,`/employees/${f.employeeId}/detail`,[200],d=>d.identityNumber==='099900000006'&&d.address==='Địa chỉ QA giả lập');
  await test('audit-private-authorized',actor,`/g1/audit?targetTable=employees&recordId=${f.employeeId}&size=100`,[200],d=>JSON.stringify(d).includes('099900000006'));
 }
 const sets=[];for(const keyword of ['Hân','han','Hân']){const r=await request('admin','/employees/search?size=100&keyword='+encodeURIComponent(keyword));sets.push({keyword,status:r.status,ids:r.data.content?.map(x=>x.employeeId).sort((a,b)=>a-b)});}
 results.push({id:'unicode-composed-decomposed-search',pass:sets.every(s=>s.status===200)&&sets.every(s=>JSON.stringify(s.ids)===JSON.stringify(sets[0].ids)),evidence:sets});
 console.log(results.at(-1).pass?'PASS':'FAIL','unicode-composed-decomposed-search');
})().catch(e=>results.push({id:'HARNESS',pass:false,error:e.message})).finally(()=>{fs.writeFileSync('qa_ui/full-audit/privacy-search-results.json',JSON.stringify({executedAt:new Date().toISOString(),results},null,2));console.log('TOTAL',results.length,'PASS',results.filter(r=>r.pass).length,'FAIL',results.filter(r=>!r.pass).length)});
