// Rebuild the deliverable collection using only Node's standard library.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const directory=path.dirname(fileURLToPath(import.meta.url));
const auth=(token)=>token?{type:'bearer',bearer:[{key:'token',value:`{{${token}}}`,type:'string'}]}:{type:'noauth'};
let requestCount=0;
function request(name,method,url,status,{body,token='managerToken',mutation=false,needs=[],save,tests='',init=false,headers=[]}={}) {
  requestCount++;
  let raw=body===undefined?undefined:typeof body==='string'?body:JSON.stringify(body,null,2).replace(/"{{(\w*Id)}}"/g,'{{$1}}');
  const pre=[];
  if(token) pre.push(`if (!pm.environment.get('${token}')) { console.warn('SKIP: missing ${token}; import the runtime environment'); pm.execution.skipRequest(); }`);
  if(token==='employeeToken') pre.push(`if (pm.environment.get('employeeToken') && pm.environment.get('employeeToken')===pm.environment.get('managerToken')) { pm.execution.skipRequest(); throw new Error('employeeToken must not equal managerToken'); }`);
  if(mutation) pre.push(`if (String(pm.environment.get('runMutations')).toLowerCase() !== 'true') { console.warn('SKIP: set runMutations=true to run the G1PM test-data chain'); pm.execution.skipRequest(); }`);
  if(init) pre.push(`
if (String(pm.environment.get('runMutations')).toLowerCase()==='true' && pm.environment.get('managerToken')) {
  ['sourceDepartmentId','sourcePositionId','targetDepartmentId','targetPositionId','employeeId','headBId','headCId','headPositionId'].forEach(k=>pm.collectionVariables.unset(k));
  pm.collectionVariables.set('runTag','G1PM-'+Date.now()+'-'+Math.floor(Math.random()*10000));
  const vn=Date.now()+7*60*60*1000;
  const date=days=>new Date(vn+days*86400000).toISOString().slice(0,10);
  pm.collectionVariables.set('businessDate',date(0)); pm.collectionVariables.set('hireDate',date(-30));
  pm.collectionVariables.set('futureDate',date(1)); pm.collectionVariables.set('headStart',date(-3));
  pm.collectionVariables.set('replacementDate',date(-1));
}`);
  if(needs.length) pre.push(`if (String(pm.environment.get('runMutations')).toLowerCase()==='true' && pm.environment.get('managerToken')) {
  for(const k of ${JSON.stringify(needs)}) if(!/^[1-9][0-9]*$/.test(String(pm.collectionVariables.get(k)||''))) {
    pm.execution.skipRequest(); throw new Error('Missing '+k+': run the mutation folder from its first request');
  }
}`);
  let script=`pm.test('HTTP ${status}',()=>pm.response.to.have.status(${status}));\n`;
  if(save) script+=`if(pm.response.code===${status}) pm.collectionVariables.set('${save.variable}',pm.response.json()['${save.field}']);\n`;
  if(tests) script+=`if(pm.response.code===${status}) {\n${tests}\n}`;
  const result={name,request:{method,auth:auth(token),header:[...(raw===undefined?[]:[{key:'Content-Type',value:'application/json'}]),...headers],url:'{{baseUrl}}'+url},event:[
    {listen:'prerequest',script:{type:'text/javascript',exec:pre}},
    {listen:'test',script:{type:'text/javascript',exec:script.split('\n')}}
  ]};
  if(raw!==undefined) result.request.body={mode:'raw',raw,options:{raw:{language:'json'}}};
  return result;
}
const folder=(name,item)=>({name,item});
const unknownId=9007199254740991;
const validEmployee={employeeCode:'G1-ACCESS',fullName:'Kiểm tra quyền',birthDate:'1990-01-01',hireDate:'2020-01-01',departmentId:unknownId,positionId:unknownId};
const department={departmentCode:'G1-ACCESS',departmentName:'Kiểm tra quyền'};
const position={positionCode:'G1-ACCESS',positionName:'Kiểm tra quyền'};
const transfer={departmentId:unknownId,positionId:unknownId,effectiveFrom:'2020-01-01'};
const protectedRoutes=[
 ['Danh sách nhân viên','GET','/employees'],['Chi tiết cơ bản','GET','/employees/1'],['Hồ sơ đầy đủ','GET','/employees/1/detail'],
 ['Tìm nhân viên','GET','/employees/search'],['Tạo nhân viên','POST','/employees',validEmployee],
 ['PUT hồ sơ','PUT','/employees/1',{fullName:'Kiểm tra quyền'}],['PATCH liên hệ','PATCH','/employees/1/contact',{address:'Kiểm tra'}],
 ['DELETE tương thích','DELETE','/employees/1'],['Nghỉ việc','POST','/employees/1/terminate'],
 ['Danh sách phòng','GET','/departments'],['Chi tiết phòng','GET','/departments/1'],['Tạo phòng','POST','/departments',department],
 ['Sửa phòng','PUT','/departments/1',department],['Ngừng phòng','DELETE','/departments/1'],
 ['Danh sách chức vụ','GET','/positions'],['Chi tiết chức vụ','GET','/positions/1'],['Tạo chức vụ','POST','/positions',position],
 ['Sửa chức vụ','PUT','/positions/1',position],['Ngừng chức vụ','DELETE','/positions/1'],
 ['Danh sách phân công','GET','/employee-assignments'],['Lịch sử nhân viên','GET','/employees/1/assignments'],
 ['Điều chuyển','PUT','/employees/1/transfer',transfer],['Audit','GET','/g1/audit?targetTable=employees&recordId=1'],
 ['Trưởng phòng','GET','/departments/1/head'],['Lịch sử trưởng phòng','GET','/departments/1/head/history'],
 ['Ứng viên trưởng phòng','GET','/departments/1/head/candidates'],
 ['Bổ nhiệm','PUT','/departments/1/head',{employeeId:1,effectiveFrom:'2020-01-01',reason:'Kiểm tra quyền'}],
 ['Kết thúc nhiệm kỳ','POST','/departments/1/head/end',{effectiveFrom:'2020-01-01',nextPositionId:1,reason:'Kiểm tra quyền'}]
];
// Denial tests must never target an existing employee/catalog even if the wrong token is imported.
for(const route of protectedRoutes) route[2]=route[2].replace(/\/1(?=\/|$)/g,'/'+unknownId);
const health=[request('Health UP','GET','/health',200,{token:null,tests:`pm.test('UP',()=>pm.expect(pm.response.json().status).to.eql('UP'));`}),
 request('Health alias /api/health','GET','/api/health',200,{token:null})];
const anonymous=protectedRoutes.map(([name,method,url,body])=>request(name+' → 401',method,url,401,{body,token:null}));
anonymous.push(request('Header role/account tự khai không cấp quyền','GET','/employees',401,{token:null,headers:[{key:'X-Role',value:'ADMIN'},{key:'X-Account-Id',value:'1'}]}));
const employee=protectedRoutes.map(([name,method,url,body])=>request(name+' → 403',method,url,403,{body,token:'employeeToken'}));
const pageTests=`const j=pm.response.json(); pm.test('Phân trang',()=>{pm.expect(j.content).to.be.an('array');pm.expect(j.totalElements).to.be.a('number');});`;
const read=[];
for(const url of ['/employees','/employees/search?status=DANG_LAM','/departments','/positions','/employee-assignments']) {
  read.push(request('Đọc '+url,'GET',url,200,{tests:pageTests}));
  read.push(request('page âm '+url,'GET',url+(url.includes('?')?'&':'?')+'page=-1',400));
  read.push(request('size quá lớn '+url,'GET',url+(url.includes('?')?'&':'?')+'size=101',400));
}
for(const url of ['/employees/9223372036854775807','/departments/9223372036854775807','/positions/9223372036854775807',
 '/employees/9223372036854775807/assignments','/departments/9223372036854775807/head',
 '/g1/audit?targetTable=employees&recordId=9223372036854775807']) read.push(request('Không tồn tại '+url,'GET',url,404));
read.push(request('Trạng thái sai','GET','/employees/search?status=INVALID',400));
read.push(request('Audit table ngoài G1','GET','/g1/audit?targetTable=accounts&recordId=1',400));
read.push(request('JSON sai','POST','/employees',400,{body:'{invalid-json'}));
read.push(request('Thiếu tên nhân viên','POST','/employees',400,{body:{...validEmployee,fullName:' '}}));
read.push(request('ID phòng âm','POST','/employees',400,{body:{...validEmployee,departmentId:-1}}));
read.push(request('Email sai','PATCH','/employees/1/contact',400,{body:{email:'not-an-email'}}));
read.push(request('Điện thoại sai','PATCH','/employees/1/contact',400,{body:{phone:'123'}}));

const chain=[];
function add(name,method,url,status,options={}) { chain.push(request(String(chain.length+1).padStart(2,'0')+' — '+name,method,url,status,{...options,mutation:true})); }
const dep=(suffix,name,active)=>({departmentCode:'{{runTag}}-'+suffix,departmentName:name,...(active===undefined?{}:{isActive:active})});
const pos=(suffix,name,active)=>({positionCode:'{{runTag}}-'+suffix,positionName:name,...(active===undefined?{}:{isActive:active})});
const emp=(suffix,dept='sourceDepartmentId',p='sourcePositionId')=>({employeeCode:'{{runTag}}-'+suffix,fullName:'  Nhân viên G1 Postman  ',birthDate:'1990-01-01',hireDate:'{{hireDate}}',departmentId:`{{${dept}}}`,positionId:`{{${p}}}`,email:'g1.original@example.com',phone:'0901234567',address:'  Địa chỉ G1  ',identityNumber:'G1-TEST-IDENTITY'});
const save=(variable,field)=>({variable,field});
add('Tạo phòng nguồn','POST','/departments',201,{init:true,body:dep('D1','Phòng nguồn'),save:save('sourceDepartmentId','departmentId')});
add('Tạo chức vụ nguồn','POST','/positions',201,{body:pos('P1','Chuyên viên nguồn'),save:save('sourcePositionId','positionId')});
add('Tạo phòng đích','POST','/departments',201,{body:dep('D2','Phòng đích'),save:save('targetDepartmentId','departmentId')});
add('Tạo chức vụ đích','POST','/positions',201,{body:pos('P2','Chuyên viên đích'),save:save('targetPositionId','positionId')});
add('Mã phòng trùng','POST','/departments',409,{body:dep('D1','Trùng mã')});
add('Mã chức vụ trùng','POST','/positions',409,{body:pos('P1','Trùng mã')});
add('Sửa phòng','PUT','/departments/{{sourceDepartmentId}}',200,{needs:['sourceDepartmentId'],body:dep('D1','Phòng nguồn đã sửa')});
add('Không đổi mã phòng','PUT','/departments/{{sourceDepartmentId}}',400,{needs:['sourceDepartmentId'],body:dep('OTHER','Sai mã')});
add('Sửa chức vụ','PUT','/positions/{{sourcePositionId}}',200,{needs:['sourcePositionId'],body:pos('P1','Chuyên viên nguồn đã sửa')});
add('Không đổi mã chức vụ','PUT','/positions/{{sourcePositionId}}',400,{needs:['sourcePositionId'],body:pos('OTHER','Sai mã')});
add('Tạo nhân viên + phân công','POST','/employees',201,{needs:['sourceDepartmentId','sourcePositionId'],body:emp('E1'),save:save('employeeId','employeeId'),tests:`const j=pm.response.json();pm.test('Tên chuẩn hóa và đúng phòng',()=>{pm.expect(j.fullName).to.eql('Nhân viên G1 Postman');pm.expect(j.currentDepartmentId).to.eql(Number(pm.collectionVariables.get('sourceDepartmentId')));});`});
add('Mã nhân viên trùng','POST','/employees',409,{needs:['sourceDepartmentId','sourcePositionId'],body:emp('E1')});
add('Ngày vào làm tương lai','POST','/employees',400,{needs:['sourceDepartmentId','sourcePositionId'],body:{...emp('FUTURE'),hireDate:'{{futureDate}}'}});
add('Ngày sinh sau ngày vào làm','POST','/employees',400,{needs:['sourceDepartmentId','sourcePositionId'],body:{...emp('BADDATE'),birthDate:'{{businessDate}}'}});
add('Hồ sơ giữ địa chỉ/CCCD','GET','/employees/{{employeeId}}/detail',200,{needs:['employeeId'],tests:`const j=pm.response.json();pm.test('Thông tin đầy đủ',()=>{pm.expect(j.address).to.eql('Địa chỉ G1');pm.expect(j.identityNumber).to.eql('G1-TEST-IDENTITY');});`});
add('Lọc nhân viên theo phòng/chức vụ','GET','/employees/search?departmentId={{sourceDepartmentId}}&positionId={{sourcePositionId}}&keyword={{runTag}}',200,{needs:['employeeId','sourceDepartmentId','sourcePositionId'],tests:`pm.test('Tìm đúng một nhân viên',()=>pm.expect(pm.response.json().totalElements).to.eql(1));`});
add('Không ngừng phòng đang dùng','DELETE','/departments/{{sourceDepartmentId}}',409,{needs:['sourceDepartmentId']});
add('Không ngừng chức vụ đang dùng','DELETE','/positions/{{sourcePositionId}}',409,{needs:['sourcePositionId']});
add('PUT cũng không ngừng phòng đang dùng','PUT','/departments/{{sourceDepartmentId}}',409,{needs:['sourceDepartmentId'],body:dep('D1','Phòng nguồn đã sửa',false)});
add('PUT hồ sơ có audit','PUT','/employees/{{employeeId}}',200,{needs:['employeeId'],body:{fullName:'Nhân viên đã sửa G1',birthDate:'1990-01-01',email:'g1.put@example.com',phone:'0901234567',identityNumber:'G1-TEST-IDENTITY'}});
add('PATCH email không xóa trường khác','PATCH','/employees/{{employeeId}}/contact',200,{needs:['employeeId'],body:{email:'g1.patch@example.com'},tests:`const j=pm.response.json();pm.test('Giữ địa chỉ và điện thoại',()=>{pm.expect(j.address).to.eql('Địa chỉ G1');pm.expect(j.phone).to.eql('0901234567');});`});
add('PATCH chuỗi rỗng xóa địa chỉ','PATCH','/employees/{{employeeId}}/contact',200,{needs:['employeeId'],body:{address:''},tests:`pm.test('Địa chỉ được xóa',()=>pm.expect(pm.response.json().address).to.eql(null));`});
add('Đếm audit trước PATCH rỗng','GET','/g1/audit?targetTable=employees&recordId={{employeeId}}&size=100',200,{needs:['employeeId'],tests:`pm.collectionVariables.set('auditBeforeNoop',pm.response.json().totalElements);`});
add('PATCH rỗng không đổi dữ liệu','PATCH','/employees/{{employeeId}}/contact',200,{needs:['employeeId'],body:{}});
add('PATCH null giữ email','PATCH','/employees/{{employeeId}}/contact',200,{needs:['employeeId'],body:{email:null},tests:`pm.test('Giữ email',()=>pm.expect(pm.response.json().email).to.eql('g1.patch@example.com'));`});
add('Không audit thay đổi giả','GET','/g1/audit?targetTable=employees&recordId={{employeeId}}&size=100',200,{needs:['employeeId'],tests:`pm.test('Audit không tăng',()=>pm.expect(pm.response.json().totalElements).to.eql(Number(pm.collectionVariables.get('auditBeforeNoop'))));`});
add('Không điều chuyển tương lai','PUT','/employees/{{employeeId}}/transfer',400,{needs:['employeeId','targetDepartmentId','targetPositionId'],body:{departmentId:'{{targetDepartmentId}}',positionId:'{{targetPositionId}}',effectiveFrom:'{{futureDate}}'}});
add('Điều chuyển phòng/chức vụ','PUT','/employees/{{employeeId}}/transfer',200,{needs:['employeeId','targetDepartmentId','targetPositionId'],body:{departmentId:'{{targetDepartmentId}}',positionId:'{{targetPositionId}}',effectiveFrom:'{{businessDate}}'}});
add('Lịch sử liên tục [start,end)','GET','/employees/{{employeeId}}/assignments',200,{needs:['employeeId'],tests:`const rows=pm.response.json().content;pm.test('Hai phân công không có khoảng trống',()=>{pm.expect(rows.length).to.eql(2);pm.expect(rows[0].effectiveFrom).to.eql(pm.collectionVariables.get('businessDate'));pm.expect(rows[1].effectiveTo).to.eql(rows[0].effectiveFrom);});`});
add('Hồ sơ theo phòng mới','GET','/employees/{{employeeId}}/detail',200,{needs:['employeeId','targetDepartmentId'],tests:`pm.test('Phòng hiện tại',()=>pm.expect(pm.response.json().currentDepartmentId).to.eql(Number(pm.collectionVariables.get('targetDepartmentId'))));`});
add('Ngừng phòng nguồn sau điều chuyển','DELETE','/departments/{{sourceDepartmentId}}',200,{needs:['sourceDepartmentId']});
add('Ngừng chức vụ nguồn sau điều chuyển','DELETE','/positions/{{sourcePositionId}}',200,{needs:['sourcePositionId']});
add('Phòng nguồn vẫn tồn tại','GET','/departments/{{sourceDepartmentId}}',200,{needs:['sourceDepartmentId'],tests:`pm.test('Chỉ ngừng sử dụng',()=>pm.expect(pm.response.json().isActive).to.eql(false));`});
add('Chức vụ nguồn vẫn tồn tại','GET','/positions/{{sourcePositionId}}',200,{needs:['sourcePositionId'],tests:`pm.test('Chỉ ngừng sử dụng',()=>pm.expect(pm.response.json().isActive).to.eql(false));`});
add('Không tạo vào danh mục ngừng dùng','POST','/employees',409,{needs:['sourceDepartmentId','sourcePositionId'],body:emp('INACTIVE')});
add('Kích hoạt lại phòng nguồn','PUT','/departments/{{sourceDepartmentId}}',200,{needs:['sourceDepartmentId'],body:dep('D1','Phòng nguồn đã sửa',true)});
add('Kích hoạt lại chức vụ nguồn','PUT','/positions/{{sourcePositionId}}',200,{needs:['sourcePositionId'],body:pos('P1','Chuyên viên nguồn đã sửa',true)});
add('Tạo nhân viên ứng viên B','POST','/employees',201,{needs:['targetDepartmentId','targetPositionId'],body:emp('HEADB','targetDepartmentId','targetPositionId'),save:save('headBId','employeeId')});
add('Tạo nhân viên ứng viên C','POST','/employees',201,{needs:['targetDepartmentId','targetPositionId'],body:emp('HEADC','targetDepartmentId','targetPositionId'),save:save('headCId','employeeId')});
add('Phòng mới đang trống trưởng phòng','GET','/departments/{{targetDepartmentId}}/head',204,{needs:['targetDepartmentId']});
add('Bổ nhiệm B trong quá khứ','PUT','/departments/{{targetDepartmentId}}/head',200,{needs:['targetDepartmentId','headBId'],body:{employeeId:'{{headBId}}',effectiveFrom:'{{headStart}}',reason:'G1 Postman bổ nhiệm B'}});
add('B là trưởng phòng hiện tại','GET','/departments/{{targetDepartmentId}}/head',200,{needs:['targetDepartmentId','headBId'],tests:`pm.test('Trưởng phòng B',()=>pm.expect(pm.response.json().employeeId).to.eql(Number(pm.collectionVariables.get('headBId'))));`});
add('Ứng viên không chứa trưởng phòng','GET','/departments/{{targetDepartmentId}}/head/candidates?size=100',200,{needs:['targetDepartmentId','headBId'],tests:`pm.test('Không liệt kê trưởng phòng hiện tại',()=>pm.expect(pm.response.json().items.some(x=>x.employeeId===Number(pm.collectionVariables.get('headBId')))).to.eql(false));`});
add('Không cho trưởng phòng nghỉ trực tiếp','POST','/employees/{{headBId}}/terminate',409,{needs:['headBId']});
add('Không điều chuyển trưởng phòng bằng API thường','PUT','/employees/{{headBId}}/transfer',409,{needs:['headBId','sourceDepartmentId','sourcePositionId'],body:{departmentId:'{{sourceDepartmentId}}',positionId:'{{sourcePositionId}}',effectiveFrom:'{{businessDate}}'}});
add('Thay B bằng C, chỉ rõ chức vụ tiếp theo của B','PUT','/departments/{{targetDepartmentId}}/head',200,{needs:['targetDepartmentId','headCId','targetPositionId'],body:{employeeId:'{{headCId}}',effectiveFrom:'{{replacementDate}}',previousHeadPositionId:'{{targetPositionId}}',reason:'G1 Postman thay trưởng phòng'}});
add('Tra cứu lịch sử vẫn là B','GET','/departments/{{targetDepartmentId}}/head?asOf={{headStart}}',200,{needs:['targetDepartmentId','headBId'],tests:`pm.test('Giữ lịch sử B',()=>pm.expect(pm.response.json().employeeId).to.eql(Number(pm.collectionVariables.get('headBId'))));`});
add('Hiện tại là C','GET','/departments/{{targetDepartmentId}}/head',200,{needs:['targetDepartmentId','headCId'],tests:`pm.test('Trưởng phòng C',()=>pm.expect(pm.response.json().employeeId).to.eql(Number(pm.collectionVariables.get('headCId'))));`});
add('Kết thúc nhiệm kỳ C','POST','/departments/{{targetDepartmentId}}/head/end',204,{needs:['targetDepartmentId','targetPositionId'],body:{effectiveFrom:'{{businessDate}}',nextPositionId:'{{targetPositionId}}',reason:'G1 Postman kết thúc nhiệm kỳ'}});
add('Sau kết thúc phòng trống trưởng phòng','GET','/departments/{{targetDepartmentId}}/head',204,{needs:['targetDepartmentId']});
add('Lịch sử hai trưởng phòng','GET','/departments/{{targetDepartmentId}}/head/history',200,{needs:['targetDepartmentId'],tests:`pm.test('Có hai nhiệm kỳ',()=>pm.expect(pm.response.json().totalElements).to.eql(2));`});
add('Audit nhân viên có đủ hành động','GET','/g1/audit?targetTable=employees&recordId={{employeeId}}&size=100',200,{needs:['employeeId'],tests:`const rows=pm.response.json().content;pm.test('Đủ audit và JSON hợp lệ',()=>{const actions=rows.map(x=>x.action);['EMPLOYEE_CREATE','EMPLOYEE_PROFILE_UPDATE','EMPLOYEE_CONTACT_UPDATE','EMPLOYEE_TRANSFER'].forEach(a=>pm.expect(actions).to.include(a));rows.forEach(x=>{JSON.parse(x.beforeDataJson);JSON.parse(x.afterDataJson);pm.expect(x.occurredAt).to.match(/Z$/);});});`});
add('Audit phòng có bổ nhiệm/thay/kết thúc','GET','/g1/audit?targetTable=departments&recordId={{targetDepartmentId}}&size=100',200,{needs:['targetDepartmentId'],tests:`const rows=pm.response.json().content;pm.test('Audit phòng và trưởng phòng',()=>{pm.expect(rows.filter(x=>x.action==='DEPARTMENT_HEAD_APPOINT').length).to.eql(2);pm.expect(rows.some(x=>x.action==='DEPARTMENT_HEAD_END')).to.eql(true);});`});
add('Audit chức vụ có tạo/sửa/ngừng','GET','/g1/audit?targetTable=positions&recordId={{sourcePositionId}}&size=100',200,{needs:['sourcePositionId'],tests:`const actions=pm.response.json().content.map(x=>x.action);pm.test('Audit danh mục',()=>['POSITION_CREATE','POSITION_UPDATE','POSITION_DEACTIVATE'].forEach(a=>pm.expect(actions).to.include(a)));`});
add('Nghỉ việc E1','POST','/employees/{{employeeId}}/terminate',204,{needs:['employeeId']});
add('Giữ hồ sơ đã nghỉ','GET','/employees/{{employeeId}}/detail',200,{needs:['employeeId'],tests:`const j=pm.response.json();pm.test('Đã nghỉ và đúng ngày',()=>{pm.expect(j.employmentStatus).to.eql('DA_NGHI');pm.expect(j.terminationDate).to.eql(pm.collectionVariables.get('businessDate'));});`});
add('Không nghỉ việc lần hai','POST','/employees/{{employeeId}}/terminate',409,{needs:['employeeId']});
add('DELETE cũ cũng chặn lần hai','DELETE','/employees/{{employeeId}}',409,{needs:['employeeId']});
add('Không điều chuyển người đã nghỉ','PUT','/employees/{{employeeId}}/transfer',409,{needs:['employeeId','sourceDepartmentId','sourcePositionId'],body:{departmentId:'{{sourceDepartmentId}}',positionId:'{{sourcePositionId}}',effectiveFrom:'{{businessDate}}'}});
add('Audit nghỉ việc duy nhất','GET','/g1/audit?targetTable=employees&recordId={{employeeId}}&size=100',200,{needs:['employeeId'],tests:`pm.test('Chỉ một audit nghỉ việc',()=>pm.expect(pm.response.json().content.filter(x=>x.action==='EMPLOYEE_TERMINATE').length).to.eql(1));`});
add('Nghỉ việc B qua DELETE tương thích','DELETE','/employees/{{headBId}}',200,{needs:['headBId']});
add('Nghỉ việc C sau bàn giao','POST','/employees/{{headCId}}/terminate',204,{needs:['headCId']});

const collection={info:{name:'HRM G1 — Nhân viên, phòng ban, chức vụ, điều chuyển',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',description:'Run folders in order. Anonymous/forbidden checks never write. Folder 04 requires runMutations=true and creates G1PM-* fixtures in hrm_test. Test server uses opaque local session tokens; production uses the team JWT integration. See G1_BAN_GIAO.md.'},
 variable:[{key:'runTag',value:''}],item:[folder('00 — Health',health),folder('01 — Chưa đăng nhập: tất cả API phải 401',anonymous),folder('02 — Không đủ quyền: tất cả API phải 403',employee),folder('03 — Đọc dữ liệu và kiểm tra lỗi',read),folder('04 — Chuỗi đầy đủ ghi dữ liệu G1PM (bật runMutations)',chain)]};
fs.writeFileSync(path.join(directory,'g1.postman_collection.json'),JSON.stringify(collection,null,2)+'\n');
fs.writeFileSync(path.join(directory,'g1.postman_environment.json'),JSON.stringify({id:'b89ec818-94d7-4ee0-83b8-104f589aa011',name:'HRM G1 — template (tokens required)',values:[{key:'baseUrl',value:'http://127.0.0.1:2006',enabled:true},{key:'managerToken',value:'',enabled:true},{key:'employeeToken',value:'',enabled:true},{key:'runMutations',value:'false',enabled:true}],_postman_variable_scope:'environment'},null,2)+'\n');
console.log(`Generated ${requestCount} requests (${chain.length} in the mutation chain).`);
