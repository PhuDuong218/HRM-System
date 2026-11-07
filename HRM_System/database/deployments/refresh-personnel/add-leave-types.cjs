// Add missing categories through audited APIs. Never reset existing data/policies.
const fs = require('node:fs');
const categories = [
  {typeCode:'THAI_SAN', typeName:'Nghỉ thai sản', isPaid:false, evidenceRequired:true, retroactiveDays:7},
  {typeCode:'VIEC_RIENG', typeName:'Nghỉ việc riêng không lương', isPaid:false, evidenceRequired:false, retroactiveDays:0}
];
async function main() {
  const path=require('node:path');
  const candidates=[process.env.LOCALAPPDATA+'/HRM-Center/TAI_KHOAN_MOI.txt',path.resolve(__dirname,'../../../target/data-refresh-private/TAI_KHOAN_MOI.txt')];
  const match=candidates.filter(p=>fs.existsSync(p)).map(p=>fs.readFileSync(p,'utf8').match(/Tên đăng nhập: quantri\r?\nMật khẩu: ([^\r\n]+)/)).find(m=>m&&m[1].trim());
  if (!match) throw Error('Missing local administrator credentials');
  const login = await fetch('http://localhost:2005/api/auth/web/login', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({username:'quantri',password:match[1]})});
  if (!login.ok) throw Error('Administrator login HTTP '+login.status);
  const {accessToken} = await login.json();
  async function call(path, method='GET', body) {
    const response = await fetch('http://localhost:2005'+path, {method, headers:{Authorization:'Bearer '+accessToken, 'Content-Type':'application/json', 'X-Change-Reason':Buffer.from('Bổ sung danh mục nghỉ cho quy trình đơn từ').toString('base64')}, ...(body ? {body:JSON.stringify(body)} : {})});
    if (!response.ok) throw Error(method+' '+path+' HTTP '+response.status);
    return response.status===204?null:response.json();
  }
  const existing=(await call('/leave-types?size=100')).content;
  for (const category of categories) {
    let row=existing.find(t=>t.typeCode===category.typeCode);
    if (!row) row=await call('/leave-types','POST',{typeCode:category.typeCode,typeName:category.typeName,isPaid:category.isPaid,isActive:true});
    // Resume safely after partial failure; keep any already configured policy/rules.
    const policyResponse=await fetch('http://localhost:2005/employee-requests/leave-policies/'+row.leaveTypeId,{headers:{Authorization:'Bearer '+accessToken}});
    if(policyResponse.status===404) await call('/employee-requests/leave-policies/'+row.leaveTypeId,'PUT',{consumesEntitlement:false});
    else if(!policyResponse.ok) throw Error('Policy lookup HTTP '+policyResponse.status);
    const rules=await call('/employee-requests/leave-rules/'+row.leaveTypeId);
    if(!existing.some(t=>t.leaveTypeId===row.leaveTypeId)) await call('/employee-requests/leave-rules/'+row.leaveTypeId,'PUT',{...rules,retroactiveDays:category.retroactiveDays,minimumNoticeHours:0,maximumHours:null,minimumServiceMonths:0,evidenceRequired:category.evidenceRequired});
    console.log(category.typeCode+': ready (no annual entitlement deduction)');
  }
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
