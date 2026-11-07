import sys,json
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]));import api_test as a
original=json.loads((a.OUT/'http-results.json').read_text(encoding='utf-8'));state=json.loads((a.OUT/'qa-state.json').read_text(encoding='utf-8'));a.OUT=Path(__file__).parent;a.results=[];a.bootstrap();a.state=state
import re
password=re.search(r'`([^`]+)`:',(a.ROOT/'HRM_System/database/README_TAI_KHOAN.md').read_text(encoding='utf-8-sig')).group(1)
for role in ['employee','hrA','hrB']:
 c,b,_=a.call('POST','/api/auth/login',{'username':state['accounts'][role]['username'],'password':password})
 if c!=200:raise RuntimeError('QA role login unavailable: '+role)
 a.tokens[role]=b['accessToken']
emp=state['employees'][0]['employeeId']
for repeat in range(2):
 c,b,_=a.call('POST','/api/auth/login',{'username':state['accounts']['employee']['username'],'password':password});a.tokens['employee']=b['accessToken']
 a.check('BUG-001-HRB-'+str(repeat),'GET',f'/employee-requests/balances/{emp}',role='hrB',expected=(403,))
 for role in ['employee','hrA','admin']:a.check('BALANCE-ALLOWED-'+role+str(repeat),'GET',f'/employee-requests/balances/{emp}',role=role)
 c,b,_=a.call('POST','/api/auth/login',{'username':state['accounts']['employee']['username'],'password':password});a.tokens['logout']=b['accessToken']
 a.check('SES-05-LOGOUT-'+str(repeat),'POST','/api/auth/logout',{},'logout',(204,));a.check('SES-05-OLD-JWT-'+str(repeat),'GET','/api/auth/me',role='logout',expected=(401,))
 for query,field in [('', 'from'),('?from=2026-11-01','to'),('?to=2026-11-01','from'),('?from=bad&to=2026-11-01','from'),('?from=2026-11-01&to=bad','to')]:
  body=a.check('BUG-005-'+str(repeat)+query,'GET','/api/management/schedules/search'+query,expected=(400,))
  if body and field not in str(body):raise AssertionError('Missing parameter name in error')
  a.check('SESSION-STILL-VALID-'+str(repeat)+query,'GET','/api/auth/me')
for item in original:
 if item.get('result')!='PASS' or item.get('method')!='GET' or item.get('role') not in ['admin','none']:continue
 # Re-run only read cases and original status expectations; never repeat fixture mutations.
 a.check('REGRESSION-'+item['id'],'GET',item['path'],role=item['role'],expected=tuple(item['expected']))
print('API retest',len(a.results),'PASS',sum(x['result']=='PASS' for x in a.results),'FAIL',sum(x['result']=='FAIL' for x in a.results))
