// NOT RUN. Writes only new QAFIX employee/qualification records after all preflight gates.
const fs=require('node:fs'),{spawnSync}=require('node:child_process');
const tag='QAFIX-'+Date.now().toString(36),results=[],tokens={};let employeeId;
async function call(actor,method,path,body){const r=await fetch('http://localhost:2005'+path,{method,headers:{'Content-Type':'application/json',...(tokens[actor]?{Authorization:'Bearer '+tokens[actor]}:{})},...(body===undefined?{}:{body:JSON.stringify(body)}),signal:AbortSignal.timeout(20000)});let d;try{d=await r.json()}catch{d=null}return {status:r.status,data:d};}
async function check(name,actor,method,path,body,expected,verify){const r=await call(actor,method,path,body);const pass=expected.includes(r.status)&&(!verify||verify(r.data));results.push({name,actor,method,path,expected,actual:r.status,pass});console.log(pass?'PASS':'FAIL',name,r.status);if(!pass)throw Error(name);return r.data;}
const snapshot=()=>call('admin','GET',`/employees/${employeeId}/detail`).then(r=>r.data);
async function auditCount(){const r=await call('admin','GET',`/g1/audit?targetTable=employees&recordId=${employeeId}&size=100`);if(r.status!==200)throw Error('Cannot verify audit');return r.data.totalElements;}
(async()=>{
 const sql="SET NOCOUNT ON; SELECT DB_NAME() databaseName,(SELECT MAX(backup_finish_date) FROM msdb.dbo.backupset WHERE database_name=DB_NAME() AND type='D') lastFullBackup FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;";
 const probe=spawnSync('sqlcmd',['-S','localhost','-E','-C','-d','hrm_center_full','-b','-h','-1','-w','65535','-Q',sql],{encoding:'utf8'});
 if(probe.status!==0)throw Error('Read-only database preflight failed; no API writes.');
 const db=JSON.parse(probe.stdout.trim());console.log('Database:',db.databaseName);
 if(db.databaseName!=='hrm_center_full'||!db.lastFullBackup)throw Error('A verified full database backup is required before live writes.');
 if(process.env.QAFIX_RUNTIME_APPROVED!=='1')throw Error('Confirm backup recovery instructions, stopped jobs and new backend deployment before running.');
 for(const [actor,user]of [['admin','demo.admin'],['hr','demo.hr']]){const password=process.env['QAFIX_'+actor.toUpperCase()+'_PASSWORD'];if(!password)throw Error('Missing '+actor+' password environment variable (never logged).');const r=await call('','POST','/api/auth/login',{username:process.env['QAFIX_'+actor.toUpperCase()+'_USER']||user,password});if(r.status!==200)throw Error('Login failed for '+actor);tokens[actor]=r.data.accessToken;}
 const read=await call('admin','GET','/employees/8/detail');if(!/^[a-f0-9]{16}$/i.test(read.data?.version||''))throw Error('Backend does not expose the new version contract; no API writes.');
 const body={employeeCode:tag,fullName:'QAFIX kiểm thử hồ sơ',birthDate:'1990-01-01',hireDate:'2026-01-01',departmentId:2,positionId:4,email:tag+'@example.invalid',phone:'0901234588',identityNumber:'099'+String(Date.now()).slice(-9),address:'QAFIX địa chỉ giả lập'};
 for(const field of ['email','identityNumber','address'])for(const value of [undefined,null,'','  '])await check('required-'+field+'-'+String(value),'admin','POST','/employees',{...body,employeeCode:tag+'INVALID',[field]:value},[400]);
 for(const identityNumber of ['CCCDabc','1'.repeat(8),'1'.repeat(10),'1'.repeat(11),'1'.repeat(13),'12345678901a','123 456789012','1'.repeat(10000)])await check('identity-invalid-'+identityNumber.length,'admin','POST','/employees',{...body,employeeCode:tag+'ID',identityNumber},[400]);
 const created=await check('create-valid','admin','POST','/employees',body,[201]);employeeId=created.employeeId;if(!employeeId)throw Error('Missing employee ID');
 let old=await snapshot();const beforeAudit=await auditCount();
 for(const reason of [undefined,null,'','  ','x'.repeat(1001)])await check('reason-invalid-'+String(reason?.length),'admin','PUT',`/employees/${employeeId}`,{fullName:'QAFIX must not persist',version:old.version,reason},[400]);
 await check('missing-version','admin','PUT',`/employees/${employeeId}`,{fullName:'QAFIX must not persist',reason:'QAFIX correction'},[400]);
 if(await auditCount()!==beforeAudit)throw Error('Invalid writes produced an audit event');
 const updated=await check('fresh-update','admin','PUT',`/employees/${employeeId}`,{fullName:'QAFIX changed by A',version:old.version,reason:'QAFIX correction'},[200],d=>d.version!==old.version);
 await check('stale-update','hr','PUT',`/employees/${employeeId}`,{fullName:'QAFIX stale name',version:old.version,reason:'QAFIX stale correction'},[409]);
 old=await snapshot();if(old.fullName!=='QAFIX changed by A'||old.email!==body.email||old.identityNumber!==body.identityNumber||old.address!==body.address)throw Error('Profile or private-field preservation failed');
 const n=await auditCount();const race=await Promise.all(['admin','hr'].map((actor,i)=>call(actor,'PUT',`/employees/${employeeId}`,{fullName:'QAFIX race '+i,version:old.version,reason:'QAFIX race correction'})));
 const pass=JSON.stringify(race.map(r=>r.status).sort())==='[200,409]'&&(await auditCount())===n+1;results.push({name:'same-version-race-one-write-one-audit',pass,statuses:race.map(r=>r.status)});if(!pass)throw Error('Concurrent profile write failed');
 const degree={qualificationLevelId:2,major:'Quản trị',institution:'Trường QAFIX',graduationYear:2020,certificateNumber:tag+'-A'};
 const a=await check('degree-first','admin','POST',`/employees/${employeeId}/qualifications`,degree,[201],d=>d.isPrimary===true);
 await check('degree-identical','admin','POST',`/employees/${employeeId}/qualifications`,degree,[409]);
 await check('degree-normalized','admin','POST',`/employees/${employeeId}/qualifications`,{...degree,major:' QUẢN TRỊ ',institution:degree.institution.normalize('NFD')},[409]);
 const b=await check('degree-different','admin','POST',`/employees/${employeeId}/qualifications`,{...degree,certificateNumber:tag+'-B'},[201]);
 await check('degree-update-duplicate','admin','PUT',`/employee-qualifications/${b.employeeQualificationId}`,degree,[409]);
 await check('primary-removal-guard','admin','DELETE',`/employee-qualifications/${a.employeeQualificationId}`,undefined,[409]);
 await check('soft-remove-secondary','admin','DELETE',`/employee-qualifications/${b.employeeQualificationId}`,undefined,[204]);
 await check('recreate-soft-removed','admin','POST',`/employees/${employeeId}/qualifications`,{...degree,certificateNumber:tag+'-B'},[201]);
 const parallel=await Promise.all([1,2].map(()=>call('admin','POST',`/employees/${employeeId}/qualifications`,{...degree,certificateNumber:tag+'-C'})));
 results.push({name:'identical-qualification-race',pass:JSON.stringify(parallel.map(r=>r.status).sort())==='[201,409]',statuses:parallel.map(r=>r.status)});
})().catch(e=>{console.log('STOP',e.message);results.push({name:'execution',pass:false,error:e.message});process.exitCode=1;}).finally(()=>{fs.writeFileSync('qa_fix/evidence/live-after.json',JSON.stringify({tag,employeeId,results},null,2));});
