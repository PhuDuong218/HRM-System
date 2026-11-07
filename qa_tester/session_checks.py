import api_test as a,json,re
a.results=json.loads((a.OUT/'http-results.json').read_text(encoding='utf-8'));s=json.loads((a.OUT/'qa-state.json').read_text(encoding='utf-8'));a.bootstrap();a.state=s
password=re.search(r'`([^`]+)`:',(a.ROOT/'HRM_System/database/README_TAI_KHOAN.md').read_text(encoding='utf-8-sig')).group(1)
acct=s['accounts']['qaAdmin'];assert acct['username'].startswith(s['prefix'])
for i in range(2):
 code,b,_=a.call('POST','/api/auth/login',{'username':acct['username'],'password':password})
 if code!=200:break
 a.tokens['qaAdmin']=b['accessToken'];a.check('LOGOUT-'+str(i),'POST','/api/auth/logout',{},'qaAdmin',(200,204));a.check('LOGOUT-OLD-TOKEN-'+str(i),'GET','/api/auth/me',role='qaAdmin',expected=(401,))
# Only the QA-created administrator is locked; bootstrap account stays untouched.
code,b,_=a.call('POST','/api/auth/login',{'username':acct['username'],'password':password})
if code==200:
 a.tokens['qaAdmin']=b['accessToken'];a.check('QA-LOCK','PUT',f"/api/admin/accounts/{acct['accountId']}",{'roleId':acct['roleId'],'username':acct['username'],'active':False})
 for i in range(2):a.check('LOCKED-TOKEN-'+str(i),'GET','/api/auth/me',role='qaAdmin',expected=(401,));a.check('LOCKED-LOGIN-'+str(i),'POST','/api/auth/login',{'username':acct['username'],'password':password},'none',(401,))
a.save();print('Session checks complete')
