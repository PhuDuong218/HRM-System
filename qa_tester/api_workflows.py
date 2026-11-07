import api_test as a
from datetime import date,timedelta,datetime,timezone
from concurrent.futures import ThreadPoolExecutor
import json,re
from urllib.parse import quote
a.results=json.loads((a.OUT/'http-results.json').read_text(encoding='utf-8'));a.state=json.loads((a.OUT/'qa-state.json').read_text(encoding='utf-8'));s=a.state;prefix=s['prefix'];a.bootstrap();a.state=s
password=re.search(r'`([^`]+)`:',(a.ROOT/'HRM_System/database/README_TAI_KHOAN.md').read_text(encoding='utf-8-sig')).group(1)
roles={r['roleCode']:r['roleId'] for r in s['roles']};dep=s['/departments'][0]['departmentId'];depB=s['/departments'][1]['departmentId'];pos=s['/positions'][0]['positionId'];today=date.today()
# Only accounts attached to employees created by this QA session.
for index,role in [(0,'EMPLOYEE'),(1,'EMPLOYEE'),(2,'HR'),(3,'HR'),(4,'ADMIN')]:
 key=['employee','other','hrA','hrB','qaAdmin'][index];e=s['employees'][index];username=prefix+'_'+key
 result=a.check('ACCOUNT-CREATE-'+key,'POST','/api/admin/accounts',{'employeeId':e['employeeId'],'roleId':roles[role],'username':username,'password':password,'confirmPassword':password,'active':True},expected=(201,200))
 if not result:continue
 s.setdefault('accounts',{})[key]=result;a.save()
 code,b,_=a.call('POST','/api/auth/login',{'username':username,'password':password})
 if code==200:a.tokens[key]=b['accessToken']
 else:a.check('LOGIN-'+key,'POST','/api/auth/login',{'username':username,'password':password},'none')
for key,d in [('hrA',dep),('hrB',depB)]:
 if key in s.get('accounts',{}):a.check('SCOPE-'+key,'POST','/api/admin/hr-scopes',{'hrAccountId':s['accounts'][key]['accountId'],'departmentId':d,'effectiveFrom':(datetime.now(timezone.utc)-timedelta(hours=1)).replace(tzinfo=None).isoformat(timespec='seconds'),'reason':prefix+' scope'})
emp=s['employees'][0]['employeeId'];other=s['employees'][1]['employeeId'];level=s['/qualification-levels'][0]['qualificationLevelId']
for role in ['employee','other','hrA','hrB','qaAdmin']:
 if role not in a.tokens:continue
 a.check('AUTH-ME-'+role,'GET','/api/auth/me',role=role)
 paths=['/positions?page=0&size=10','/departments?page=0&size=10','/employees/search?keyword='+quote(prefix),'/employee-assignments/search?keyword='+quote(prefix),'/employee-qualifications/search?keyword='+quote(prefix),'/leave-types?page=0&size=10','/leave-entitlements?page=0&size=10','/employee-requests?page=0&size=10','/payroll-batches','/payroll-periods','/api/admin/accounts']
 for path in paths:
  allowed=role in ['hrA','hrB','qaAdmin'] and not(path.startswith('/api/admin') and role!='qaAdmin')
  # HR payroll permissions are independently configured: observe rather than assume data scope.
  a.check('RBAC-'+role+path,'GET',path,role=role,expected=(200,) if allowed else (403,))
 for path in [f'/employees/{emp}/detail',f'/employees/{emp}/qualifications',f'/employees/{emp}/assignments',f'/employee-requests/balances/{emp}']:
  expected=(200,) if role in ['employee','hrA','qaAdmin'] else (403,)
  if path.endswith('/assignments') and role=='employee':expected=(403,)
  a.check('IDOR-'+role+path,'GET',path,role=role,expected=expected)
# Search correctness including literal wildcard symbols, Unicode and tokens.
for q in ['han','HÂN','ngoc han','%','_']:
 path='/employees/search?keyword='+quote(q);a.check('EMP-SEARCH-'+q,'GET',path)
for q in ['han','ha noi','%','_']:
 a.check('QUA-SEARCH-'+q,'GET','/employee-qualifications/search?keyword='+quote(q))
# Create/switch/remove QA qualifications without touching existing data.
qual=a.check('QUA-LIST','GET',f'/employees/{emp}/qualifications');rows=(qual or {}).get('content',[])
if rows:
 for _ in range(2):a.check('QUA-PRIMARY-SWITCH','PUT',f"/employee-qualifications/{rows[-1]['employeeQualificationId']}/primary")
 a.check('QUA-PRIMARY-RESULT','GET',f'/employees/{emp}/qualifications')
 qid=rows[0]['employeeQualificationId'];a.check('QUA-REMOVE','DELETE',f'/employee-qualifications/{qid}',expected=(204,));a.check('QUA-DELETED-DETAIL','GET',f'/employee-qualifications/{qid}',expected=(404,))
# Request lifecycle on a newly created QA employee.
if 'employee' in a.tokens:
 resign=a.check('RES-CREATE','POST','/employee-requests/resignation',{'requestedLastWorkingDate':str(today+timedelta(days=10)),'reason':prefix+' resignation'},'employee',(201,))
 if resign:
  rid=resign['requestId'];s['resignationId']=rid
  a.check('RES-OUTSCOPE','POST',f'/employee-requests/{rid}/approve',{'approvedLastWorkingDate':str(today+timedelta(days=10))},'hrB',(403,))
  a.check('RES-EMPTY-REJECT','POST',f'/employee-requests/{rid}/reject',{'reason':'   '},'hrA',(400,))
  a.check('RES-REJECT','POST',f'/employee-requests/{rid}/reject',{'reason':prefix+' rejected'},'hrA')
  a.check('RES-REAPPROVE-REJECTED','POST',f'/employee-requests/{rid}/approve',{'approvedLastWorkingDate':str(today+timedelta(days=10))},'hrA',(409,))
  again=a.check('RES-RESUBMIT','POST','/employee-requests/resignation',{'requestedLastWorkingDate':str(today+timedelta(days=10)),'reason':prefix+' resubmit'},'employee',(201,))
  if again:
   rid=again['requestId'];s['resignationId']=rid
   a.check('RES-APPROVE','POST',f'/employee-requests/{rid}/approve',{'approvedLastWorkingDate':str(today+timedelta(days=10))},'hrA')
   a.check('RES-DOUBLE-APPROVE','POST',f'/employee-requests/{rid}/approve',{'approvedLastWorkingDate':str(today+timedelta(days=10))},'hrA',(409,))
   a.check('RES-NOT-EFFECTIVE','GET',f'/employees/{emp}/detail')
# Duplicate creation race (QA department, all repeated calls have the same code).
body={'departmentCode':prefix+'_RACE','departmentName':prefix+' race','isActive':True}
with ThreadPoolExecutor(max_workers=5) as pool:
 outcomes=list(pool.map(lambda _:a.call('POST','/departments',body,a.tokens['admin']),range(5)))
for i,(code,b,ms) in enumerate(outcomes):a.results.append({'id':'CAT-RACE-'+str(i),'method':'POST','path':'/departments','role':'admin','status':code,'body':a.scrub(b),'result':'PASS' if code in [200,201,409] else 'FAIL','expected':[201,409]})
s['raceSuccessCount']=sum(code in [200,201] for code,_,_ in outcomes);a.save()
# Validation on own new records; cases are deliberately unique and repeatable.
base={'employeeCode':prefix+'_BAD','fullName':prefix+' Bad','hireDate':str(today),'departmentId':dep,'positionId':pos}
for name,patch in [('blank-name',{'fullName':'   '}),('long-name',{'fullName':'x'*201}),('email',{'email':'bad-email'}),('phone',{'phone':'abc'}),('future-birth',{'birthDate':'2099-01-01'}),('missing-department',{'departmentId':99999999}),('missing-position',{'positionId':99999999})]:
 for repeat in range(2):a.check('EMP-VALIDATION-'+name+str(repeat),'POST','/employees',{**base,'employeeCode':prefix+'_BAD'+name,**patch},expected=(400,404,409))
# Soft termination/re-hire of QA employee only; never run global execute-due.
for repeat in range(2):a.check('TRANSFER-SELF-'+str(repeat),'PUT',f'/employees/{other}/transfer',{'departmentId':dep,'positionId':pos,'effectiveFrom':str(today),'reason':prefix+' same assignment'},expected=(400,409))
a.check('TRANSFER-VALID','PUT',f'/employees/{other}/transfer',{'departmentId':depB,'positionId':pos,'effectiveFrom':str(today),'reason':prefix+' transfer'})
a.check('TRANSFER-HISTORY','GET',f'/employees/{other}/assignments')
for role in ['hrA','hrB']:a.check('TRANSFER-SCOPE-'+role,'GET',f'/employees/{other}/detail',role=role,expected=(403,) if role=='hrA' else (200,))
a.check('EMP-TERMINATE','POST',f'/employees/{other}/terminate',expected=(200,204))
a.check('EMP-REHIRE-HR','POST',f'/employees/{other}/rehire',{'departmentId':dep,'positionId':pos,'effectiveDate':str(today),'reason':prefix+' rehire'},'hrA',(403,))
a.check('EMP-REHIRE-ADMIN','POST',f'/employees/{other}/rehire',{'departmentId':dep,'positionId':pos,'effectiveDate':str(today+timedelta(days=1)),'reason':prefix+' rehire'},expected=(200,400,409))
a.check('AUD-QA','GET',f'/g1/audit?targetTable=employees&recordId={emp}')
a.check('EMP-DETAIL-FINAL','GET',f'/employees/{emp}/detail')
a.save();print('Requests total',len(a.results),'fail',sum(r['result']=='FAIL' for r in a.results),'race successes',s['raceSuccessCount'])
