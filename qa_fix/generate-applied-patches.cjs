const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),{spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),manifest=JSON.parse(fs.readFileSync(path.join(__dirname,'backup_original/SHA256.json'),'utf8').replace(/^\uFEFF/,''));
const changed=[],diffs=new Map();
for(const entry of manifest){const original='qa_fix/backup_original/'+entry.path;
 const old=fs.readFileSync(path.join(root,original));if(crypto.createHash('sha256').update(old).digest('hex').toUpperCase()!==entry.sha256)throw Error('Backup mismatch '+entry.path);
 const current=fs.readFileSync(path.join(root,entry.path));if(old.equals(current))continue;
 const r=spawnSync('git',['diff','--no-index','--ignore-cr-at-eol','--no-ext-diff','--text','--',original,entry.path],{cwd:root,encoding:'utf8'});if(![0,1].includes(r.status))throw Error('Cannot diff '+entry.path);
 if(!r.stdout.trim())continue;
 diffs.set(entry.path,r.stdout.replaceAll('a/'+original,'a/'+entry.path));changed.push(entry.path);
}
for(const p of ['HRM_System/src/main/java/com/hrm/service/EmployeeWriteRules.java','HRM_System/src/test/java/com/hrm/service/EmployeeWriteFixRegressionTest.java','HRM_System/src/test/java/com/hrm/service/EmployeeProfileVersionFixTest.java','HRM_System/src/test/java/com/hrm/service/QualificationDuplicateFixTest.java','HRM_System/src/test/java/com/hrm/service/EmployeePrivacyWriteFixTest.java','frontend/test-tools/employee-write-version.test.cjs']){
 const txt=fs.readFileSync(path.join(root,p),'utf8');const lines=txt.replace(/\n$/,'').split('\n');diffs.set(p,'diff --git a/'+p+' b/'+p+'\nnew file mode 100644\n--- /dev/null\n+++ b/'+p+'\n@@ -0,0 +1,'+lines.length+' @@\n'+lines.map(l=>'+'+l).join('\n')+'\n');changed.push(p);
}
fs.writeFileSync(path.join(__dirname,'patches/ALL_APPLIED.diff'),[...diffs.values()].join(''));
const groups={
'QA-01.diff':p=>/EmployeeDTO|EmployeeUpdate|EmployeeContact|EmployeeWriteRules|EmployeeCreation|EmployeeService|EmployeeSupplement|PersonnelPrivacy|employee.service|employees\.(ts|html|css)|VersionFix/.test(p),
'QA-02.diff':p=>/EmployeeRequestDTO|EmployeeWriteFix|PrivacyWriteFix/.test(p),
'QA-03.diff':p=>/EmployeeRequestDTO|EmployeeUpdateRequestDTO|EmployeeContactPatchDTO|EmployeeWriteRules|employee-form.validation|employee-write-version/.test(p),
'QA-04.diff':p=>/EmployeeCreation|EmployeeContactService|EmployeeUpdateRequest|EmployeeContactPatch|EmployeeSupplement|EmployeeWriteRules|EmployeeProfileVersion/.test(p),
'QA-05.diff':p=>/EmployeeQualificationService|QualificationDuplicateFix|EmployeeWriteFix/.test(p)};
for(const [name,filter]of Object.entries(groups))fs.writeFileSync(path.join(__dirname,'patches',name),[...diffs].filter(([p])=>filter(p)).map(([,d])=>d).join(''));
fs.writeFileSync(path.join(__dirname,'evidence/changed-files.json'),JSON.stringify(changed,null,2));
const tests=[];for(const file of fs.readdirSync(path.join(root,'HRM_System/target/surefire-reports'))){if(!file.startsWith('TEST-')||!file.endsWith('.xml'))continue;
 const xml=fs.readFileSync(path.join(root,'HRM_System/target/surefire-reports',file),'utf8'),head=xml.match(/<testsuite\b[^>]+>/)?.[0]||'';const attr=n=>head.match(new RegExp(n+'="([^"]+)"'))?.[1];
 if(!['EmployeeWriteFixRegressionTest','EmployeeProfileVersionFixTest','QualificationDuplicateFixTest','EmployeePrivacyWriteFixTest','AuthLogoutRegressionTest','LeaveBalanceScopeRegressionTest','EmployeeTerminationServiceTest','ResignationExecutionServiceTest','PersonnelLeavePolicyTest','HrDepartmentScopeServiceTest','ScheduleQueryHrScopesTest','PayrollManagementAuthorizationTest','PayslipScopeTest'].some(n=>file.endsWith('.'+n+'.xml')))continue;
 tests.push({suite:attr('name'),tests:+attr('tests'),failures:+attr('failures'),errors:+attr('errors')});}
fs.writeFileSync(path.join(__dirname,'evidence/backend-summary.json'),JSON.stringify(tests,null,2));
console.log(JSON.stringify({changedFiles:changed.length,tests:tests.reduce((n,s)=>n+s.tests,0),failed:tests.reduce((n,s)=>n+s.failures+s.errors,0)}));
