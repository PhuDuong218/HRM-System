const fs=require('node:fs');
const results=[],tokens={};let requestId,before,quotaBody,quotaChanged=false;
const base='http://localhost:2005',tag='QA-LEAVE-FULL-'+Date.now();
async function req(actor,method,path,body){const r=await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(tokens[actor]?{Authorization:'Bearer '+tokens[actor]}:{})},...(body===undefined?{}:{body:JSON.stringify(body)}),signal:AbortSignal.timeout(20000)});const text=await r.text();let data;try{data=JSON.parse(text)}catch{data=text}return {status:r.status,data};}
async function check(name,actor,method,path,body,statuses,verify){const r=await req(actor,method,path,body);const pass=statuses.includes(r.status)&&(!verify||verify(r.data));results.push({name,status:r.status,expected:statuses,pass,response:r.data});console.log(pass?'PASS':'FAIL',name,r.status);if(!pass)throw Error(name);return r.data;}
function assertion(name,condition,data){results.push({name,pass:condition,data});console.log(condition?'PASS':'FAIL',name);if(!condition)throw Error(name);}
async function balance(){const r=await req('admin','GET','/employee-requests/balances/8');if(r.status!==200||!Array.isArray(r.data))throw Error('Cannot read balance');return r.data.find(b=>b.entitlementId===8);}
async function login(actor,username){const r=await req('','POST','/api/auth/login',{username,password:'Demo@2026!'});if(r.status!==200||!r.data.accessToken)throw Error('Login '+username+' HTTP '+r.status);tokens[actor]=r.data.accessToken;console.log('LOGIN OK',actor);}
(async()=>{
 for(const [actor,user]of [['admin','demo.admin'],['head','demo.head.hcn'],['employee','demo.emp008'],['hr','demo.hr']])await login(actor,user);
 await check('Xác minh đúng nhân viên DEMO008','admin','GET','/employees/8/detail',undefined,[200],r=>r.employeeCode==='DEMO008');
 before=await balance();assertion('1 — Đọc quỹ phép ban đầu',!!before,before);
 const q=await check('Đọc chi tiết quỹ','admin','GET','/leave-entitlements/8',undefined,[200]);
 quotaBody={employeeId:8,leaveTypeId:q.leaveTypeId,periodStart:q.periodStart,periodEnd:q.periodEnd,entitledHours:q.entitledHours,reason:tag};
 await check('2 — Chặn cấp quỹ trùng kỳ','admin','POST','/leave-entitlements',quotaBody,[409]);
 const adjusted=Number(before.entitledHours)+8; await check('3 — Tăng tổng quỹ thêm 8 giờ','admin','PUT','/leave-entitlements/8',{...quotaBody,entitledHours:adjusted},[200]);quotaChanged=true;
 let b=await balance();assertion('Điều chỉnh là thay tổng, không cộng dồn',+b.entitledHours===adjusted,b);
 await check('Khôi phục tổng quỹ trước luồng duyệt','admin','PUT','/leave-entitlements/8',quotaBody,[200]);quotaChanged=false;
 const created=await check('4 — Nhân viên nộp đơn nghỉ 09/11','employee','POST','/employee-requests/leave',{leaveTypeId:q.leaveTypeId,leaveStart:'2026-11-09T08:00:00',leaveEnd:'2026-11-09T17:00:00',reason:tag},[201]);requestId=created.requestId;
 assertion('Đơn mới có mã',!!requestId);
 b=await balance();assertion('Nộp đơn chưa trừ quỹ',+b.usedHours===+before.usedHours,b);
 await check('Chặn ADMIN duyệt trước cấp 1','admin','POST',`/employee-requests/${requestId}/approve`,{},[409]);
 await check('Trưởng phòng HCNS duyệt cấp 1','head','POST',`/employee-requests/${requestId}/head-decision`,{approve:true,note:tag},[200]);
 b=await balance();assertion('Duyệt cấp 1 chưa trừ quỹ',+b.usedHours===+before.usedHours,b);
 await check('ADMIN duyệt cuối','admin','POST',`/employee-requests/${requestId}/approve`,{note:tag},[200]);
 b=await balance();assertion('Nghỉ cả ca trừ đúng 8 giờ',+b.usedHours===+before.usedHours+8&&+b.remainingHours===+before.remainingHours-8,b);
 await check('5 — Giảm quỹ dưới số đã dùng bị chặn','admin','PUT','/leave-entitlements/8',{...quotaBody,entitledHours:4},[409]);
 await check('Duyệt lặp bị chặn','admin','POST',`/employee-requests/${requestId}/approve`,{},[409]);
 await check('6 — Hủy đơn đã duyệt trước giờ nghỉ','admin','POST',`/employee-requests/${requestId}/cancel-approved`,{reason:tag+' hoàn quỹ sau kiểm thử'},[200]);
 await check('Hủy lặp không hoàn thêm giờ','admin','POST',`/employee-requests/${requestId}/cancel-approved`,{reason:tag},[409]);
 b=await balance();assertion('Hoàn đúng số giờ, quỹ trở lại ban đầu',+b.entitledHours===+before.entitledHours&&+b.usedHours===+before.usedHours&&+b.remainingHours===+before.remainingHours,b);
 await check('7 — HR ngoài HCNS không đọc số dư','hr','GET','/employee-requests/balances/8',undefined,[403]);
})().catch(e=>{results.push({name:'Chạy kiểm thử bị dừng',pass:false,error:e.message});console.log('STOP',e.message);process.exitCode=1;}).finally(async()=>{
 try{
  if(requestId){const r=await req('admin','GET',`/employee-requests/${requestId}`);if(r.status===200&&!r.data.withdrawnAt){const approved=r.data.status==='DA_DUYET';const cleanup=await req(approved?'admin':'employee','POST',`/employee-requests/${requestId}/${approved?'cancel-approved':'withdraw'}`,{reason:tag+' cleanup'});assertion('Thu hồi đơn thử còn mở',[200,204].includes(cleanup.status),cleanup);}}
  if(quotaChanged){const r=await req('admin','PUT','/leave-entitlements/8',quotaBody);assertion('Khôi phục hạn mức sau lỗi',r.status===200,r);}
  if(before){const b=await balance();assertion('Kiểm tra số dư cuối phiên',JSON.stringify([b.entitledHours,b.usedHours,b.remainingHours])===JSON.stringify([before.entitledHours,before.usedHours,before.remainingHours]),b);}
 }catch(e){results.push({name:'CLEANUP ERROR',pass:false,error:e.message});process.exitCode=1;}
 fs.writeFileSync('qa_ui/full-leave-results.json',JSON.stringify({tag,requestId,results},null,2));
});

