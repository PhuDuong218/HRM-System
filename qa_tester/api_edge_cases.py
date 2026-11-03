import api_test as a,json,re
from datetime import date,timedelta
a.results=json.loads((a.OUT/'http-results.json').read_text(encoding='utf-8'));s=json.loads((a.OUT/'qa-state.json').read_text(encoding='utf-8'));a.bootstrap();a.state=s;prefix=s['prefix'];today=date.today();emp=s['employees'][0]['employeeId'];assert s['employees'][0]['employeeCode'].startswith('QA_')
password=re.search(r'`([^`]+)`:',(a.ROOT/'HRM_System/database/README_TAI_KHOAN.md').read_text(encoding='utf-8-sig')).group(1)
for key,acct in s['accounts'].items():
 c,b,_=a.call('POST','/api/auth/login',{'username':acct['username'],'password':password})
 if c==200:a.tokens[key]=b['accessToken']
kind=a.check('LTY-CREATE','POST','/leave-types',{'typeCode':prefix+'_LEAVE','typeName':prefix+' Phép năm','isPaid':True,'isActive':True},expected=(200,201))
if kind:
 lid=kind['leaveTypeId'];s['leaveTypeId']=lid
 a.check('LTY-POLICY','PUT',f'/employee-requests/leave-policies/{lid}',{'consumesEntitlement':True},expected=(204,))
 fund={'employeeId':emp,'leaveTypeId':lid,'periodStart':str(today.replace(month=1,day=1)),'periodEnd':str(date(today.year+1,1,1)),'entitledHours':16}
 ent=a.check('ENT-CREATE','POST','/leave-entitlements',fund,expected=(200,201))
 if ent:
  s['entitlementId']=ent['entitlementId']
  for i in range(2):a.check('ENT-OUTSCOPE-LEAK-'+str(i),'GET',f'/employee-requests/balances/{emp}',role='hrB',expected=(403,))
  for i in range(2):a.check('ENT-OUTSCOPE-DIRECT-'+str(i),'GET',f"/leave-entitlements/{ent['entitlementId']}",role='hrB',expected=(403,))
 for hours in [0,-1,100000,0.12345]:a.check('ENT-BAD-HOURS-'+str(hours),'POST','/leave-entitlements',{**fund,'entitledHours':hours},expected=(400,409))
 a.check('ENT-OVERLAP','POST','/leave-entitlements',fund,expected=(409,))
 a.check('LEAVE-BAD-RANGE','POST','/employee-requests/leave',{'leaveTypeId':lid,'leaveStart':str(today+timedelta(days=1))+'T17:00:00','leaveEnd':str(today+timedelta(days=1))+'T08:00:00','reason':prefix+' invalid'},'employee',(400,409))
 a.check('LEAVE-NO-SCHEDULE','POST','/employee-requests/leave',{'leaveTypeId':lid,'leaveStart':str(today+timedelta(days=2))+'T08:00:00','leaveEnd':str(today+timedelta(days=2))+'T17:00:00','reason':prefix+' no schedule'},'employee',(400,409))
for path in ['/employees/search?departmentId=abc','/employee-qualifications/search?yearFrom=abc','/employee-qualifications/search?yearFrom=2024&yearTo=2020','/employee-assignments/search?asOf=2025-02-29']:
 for i in range(2):a.check('BAD-TYPE-'+str(i)+path,'GET',path,expected=(400,))
original=s['/positions'][0]
for code in [original['positionCode'].lower(),' '+original['positionCode']+' ']:a.check('POS-DUP-VARIANT','POST','/positions',{'positionCode':code,'positionName':prefix+' duplicate','isActive':True},expected=(400,409))
# DELETE tests deliberately omitted after automatic review; no record deactivated here.
for path in ['/api/management/shifts','/api/management/schedules/search','/api/attendance/search','/api/overtime','/api/reports/personnel','/contracts?page=0&size=10','/allowance-types','/salary-components']:
 a.check('SMOKE-'+path,'GET',path,expected=(200,400,404))
a.save();print('Total HTTP',len(a.results),'raw status mismatches',sum(x['result']=='FAIL' for x in a.results))
