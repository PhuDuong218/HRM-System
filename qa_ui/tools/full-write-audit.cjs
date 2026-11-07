const fs=require('node:fs');const {request,test,results,tokens}=require('./full-system-audit.cjs');
const tag='QA6-'+Date.now().toString(36), fixture={tag};
function assert(id,pass,evidence){results.push({id,pass,evidence});console.log(pass?'PASS':'FAIL',id)}
function id(d,key='employeeId'){if(!d?.[key])throw Error('Missing '+key);return d[key]}
(async()=>{
 for(const [actor,username]of [['admin','demo.admin'],['hr','demo.hr'],['employee','demo.emp008']])tokens[actor]=(await request('','/api/auth/login','POST',{username,password:'Demo@2026!'})).data.accessToken;
 const position={positionCode:tag,positionName:'QA Hân chuyên viên',description:'Disposable QA concurrent duplicate',isActive:true};
 const races=await Promise.all([request('admin','/positions','POST',position),request('admin','/positions','POST',position)]);
 assert('C2-position-race',JSON.stringify(races.map(r=>r.status).sort())==='[201,409]',races.map(r=>({status:r.status,message:r.data?.message})));
 fixture.positionId=id(races.find(r=>r.status===201)?.data,'positionId');
 await test('C10-employee-catalog-write','employee','/positions',[403],null,'POST',{...position,positionCode:tag+'BLOCK'});
 await test('C1-position-case-duplicate','admin','/positions',[409],null,'POST',{...position,positionCode:tag.toLowerCase()});
 const body={employeeCode:tag,fullName:'QA Hân Đỗ',birthDate:'1997-02-03',hireDate:'2026-01-01',departmentId:2,positionId:fixture.positionId,email:tag+'@example.invalid',phone:'0901234588',identityNumber:'099900000006',address:'Địa chỉ QA giả lập',gender:'FEMALE'};
 const e=await test('D1-create-valid-employee','admin','/employees',[201],null,'POST',body);fixture.employeeId=id(e.data);const eid=fixture.employeeId;
 await test('D7-initial-assignment','admin',`/employees/${eid}/assignments`,[200],d=>d.content?.length===1);
 const detail=await request('admin',`/employees/${eid}/detail`);
 assert('A5-leading-zero-ID',detail.data.identityNumber===body.identityNumber,{matches:detail.data.identityNumber===body.identityNumber});
 await test('D1-duplicate-employee','admin','/employees',[409],null,'POST',body);
 await test('D3-future-birth','admin','/employees',[400],null,'POST',{...body,employeeCode:tag+'FUT',birthDate:'2099-01-01'});
 await test('D5-invalid-email','admin','/employees',[400],null,'POST',{...body,employeeCode:tag+'MAIL',email:'qa@@mail'});
 await test('D5-invalid-phone','admin','/employees',[400],null,'POST',{...body,employeeCode:tag+'PHONE',phone:'abc'});
 const missing={...body,employeeCode:tag+'EMPTY'};delete missing.email;delete missing.address;delete missing.identityNumber;
 const optional=await test('D5-required-private-contact-fields','admin','/employees',[400],null,'POST',missing);
 if(optional.status===201)fixture.missingFieldsEmployeeId=id(optional.data);
 await test('D1-blank-name','admin','/employees',[400],null,'POST',{...body,employeeCode:tag+'BLANK',fullName:' '});
 await test('D6-missing-department','admin','/employees',[404,409,400],null,'POST',{...body,employeeCode:tag+'NO',departmentId:99999999});
 const nq=await test('D11-update-requires-reason','admin',`/employees/${eid}`,[400],null,'PUT',{fullName:'QA no reason'});
 await test('D11-profile-update','admin',`/employees/${eid}`,[200],null,'PUT',{...body,fullName:'QA Hân cập nhật',reason:'QA kiểm tra giữ thông tin'});
 const after=await request('admin',`/employees/${eid}/detail`);
 assert('D11-profile-contact-retention',after.data.identityNumber===body.identityNumber&&after.data.address===body.address&&after.data.email===body.email,{identityRetained:after.data.identityNumber===body.identityNumber,addressRetained:after.data.address===body.address,emailRetained:after.data.email===body.email});
 const degree={qualificationLevelId:2,major:'QA Quản trị',institution:'Trường QA',graduationYear:2020,certificateNumber:tag+'A',issuedDate:'2020-07-01',expiryDate:null};
 const a=await test('D16-first-degree-auto-primary','admin',`/employees/${eid}/qualifications`,[201],d=>d.isPrimary===true,'POST',degree);fixture.degreeA=id(a.data,'employeeQualificationId');
 const b=await test('D16-add-degree-preserves-old','admin',`/employees/${eid}/qualifications`,[201],null,'POST',{...degree,certificateNumber:tag+'B',qualificationLevelId:3,graduationYear:2022});fixture.degreeB=id(b.data,'employeeQualificationId');
 await test('D16-primary-delete-requires-replacement','admin',`/employee-qualifications/${fixture.degreeA}`,[409],null,'DELETE');
 const primaryRace=await Promise.all([request('admin',`/employee-qualifications/${fixture.degreeA}/primary`,'PUT',{}),request('admin',`/employee-qualifications/${fixture.degreeB}/primary`,'PUT',{})]);
 assert('D16-primary-race-no-500',primaryRace.every(r=>r.status===200),primaryRace.map(r=>r.status));
 await test('D16-exactly-one-primary','admin',`/employees/${eid}/qualifications`,[200],d=>d.content?.length===2&&d.content.filter(x=>x.isPrimary).length===1);
 const futureDegree=await test('D15-future-graduation','admin',`/employees/${eid}/qualifications`,[400,409],null,'POST',{...degree,certificateNumber:tag+'FUT',graduationYear:2099});
 if(futureDegree.status===201)fixture.futureDegree=id(futureDegree.data,'employeeQualificationId');
 const badDates=await test('D15-expiry-before-issue','admin',`/employees/${eid}/qualifications`,[400,409],null,'POST',{...degree,certificateNumber:tag+'DATE',expiryDate:'2019-01-01'});
 if(badDates.status===201)fixture.invalidDateDegree=id(badDates.data,'employeeQualificationId');
 const duplicate=await test('D15-identical-degree','admin',`/employees/${eid}/qualifications`,[409],null,'POST',degree);
 if(duplicate.status===201)fixture.duplicateDegree=id(duplicate.data,'employeeQualificationId');
 // Source option stopped after editor would have fetched it: server must validate again.
 await test('C4-stop-unused-position','admin',`/positions/${fixture.positionId}`,[409],null,'PUT',{...position,isActive:false,reason:'QA đang có nhân viên'});
 const term={lastWorkingDate:'2026-11-20',decisionType:'MUTUAL_AGREEMENT',decisionNumber:tag+'EXIT',reason:'QA future decision, isolated record'};
 await test('K-future-termination-create','admin',`/employees/${eid}/termination-decisions`,[204],null,'POST',term);
 await test('K-future-termination-still-active','admin',`/employees/${eid}/detail`,[200],d=>d.employmentStatus==='DANG_LAM');
 const twice=await test('K-duplicate-termination-decision','admin',`/employees/${eid}/termination-decisions`,[409],null,'POST',term);
})().catch(e=>{results.push({id:'HARNESS',pass:false,error:e.message});console.log('STOP',e.message)}).finally(()=>{fs.writeFileSync('qa_ui/full-audit/write-results.json',JSON.stringify({executedAt:new Date().toISOString(),fixture,results},null,2));console.log('TOTAL',results.length,'PASS',results.filter(r=>r.pass).length,'FAIL',results.filter(r=>!r.pass).length)});

