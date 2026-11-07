import urllib.request,urllib.error,json,time,datetime,concurrent.futures,re
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent; OUT=ROOT/'qa_tester/evidence'; BASE='http://127.0.0.1:2009'; results=[]; state={}; tokens={}; prefix='QA_'+datetime.datetime.now().strftime('%Y%m%d_%H%M%S')
def scrub(v):
 if isinstance(v,dict):return {k:('***' if re.search('password|token|secret',k,re.I) else scrub(x)) for k,x in v.items()}
 if isinstance(v,list):return [scrub(x) for x in v]
 if isinstance(v,str):return re.sub(r'eyJ[\w-]+\.[\w-]+\.[\w-]+','***',v)
 return v
def call(method,path,body=None,token=None):
 data=json.dumps(body,ensure_ascii=False).encode() if body is not None else None
 headers={'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 req=urllib.request.Request(BASE+path,data,headers,method=method);start=time.monotonic()
 try:
  with urllib.request.urlopen(req,timeout=25) as r:code=r.status;raw=r.read().decode('utf-8');hdr=dict(r.headers)
 except urllib.error.HTTPError as e:code=e.code;raw=e.read().decode('utf-8');hdr=dict(e.headers)
 except Exception as e:return 0,{'error':type(e).__name__},0
 try:value=json.loads(raw) if raw else None
 except:value=raw[:1000]
 return code,value,round((time.monotonic()-start)*1000)
def check(id,method,path,body=None,role='admin',expected=(200,),note=''):
 code,value,ms=call(method,path,body,tokens.get(role)); row={'id':id,'method':method,'path':path,'role':role,'input':scrub(body),'expected':list(expected),'status':code,'body':scrub(value),'ms':ms,'result':'PASS' if code in expected else 'FAIL','note':note};results.append(row);save();return value if code in expected else None
def save():
 (OUT/'http-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8');(OUT/'qa-state.json').write_text(json.dumps(state,ensure_ascii=False,indent=2),encoding='utf-8')
def bootstrap():
 check('HEALTH','GET','/api/health',role='none')
 readme=(ROOT/'HRM_System/database/README_TAI_KHOAN.md').read_text(encoding='utf-8-sig')
 password=re.search(r'`([^`]+)`:',readme).group(1)
 c,b,_=call('POST','/api/auth/login',{'username':'admin.sample','password':password})
 if c==200:tokens['admin']=b['accessToken'];state['bootstrapUser']='admin.sample';return
 for f in (ROOT/'HRM_System/target').glob('*runtime*.json'):
  try:v={x['key']:x['value'] for x in json.loads(f.read_text(encoding='utf-8-sig'))['values']}
  except:continue
  for who in ['manager','admin']:
   if v.get(who+'Username') and v.get(who+'Password'):
    c,b,_=call('POST','/api/auth/login',{'username':v[who+'Username'],'password':v[who+'Password']})
    if c==200 and isinstance(b,dict) and b.get('accessToken'):
     tokens['admin']=b['accessToken'];state['bootstrapUser']=v[who+'Username'];return
 raise RuntimeError('No existing active test administrator credential worked; no password reset performed')
def main():
 bootstrap();state['prefix']=prefix;save()
 for i,payload in enumerate([{}, {'username':' ','password':' '},{'username':prefix+'_missing','password':'invalid'}, {'username':"' OR 1=1--",'password':'invalid'}]):check('LOG-NEG-'+str(i),'POST','/api/auth/login',payload,'none',(400,401))
 check('LOG-ME','GET','/api/auth/me')
 roles=check('ROLES','GET','/api/admin/roles');state['roles']=roles
 for path,key,body in [('/departments','departmentId',{'departmentCode':prefix+'_A','departmentName':prefix+' Phòng A','description':'QA only','isActive':True}),('/departments','departmentId',{'departmentCode':prefix+'_B','departmentName':prefix+' Phòng B','isActive':True}),('/positions','positionId',{'positionCode':prefix+'_POS','positionName':prefix+' Chuyên viên','isActive':True}),('/qualification-levels','qualificationLevelId',{'levelCode':prefix+'_LV','levelName':prefix+' Đại học','isActive':True})]:
  item=check('CAT-CREATE-'+str(len(state)),'POST',path,body,expected=(201,200));
  if not item:raise RuntimeError('Catalog create failed; inspect evidence')
  state.setdefault(path,[]).append(item);save()
 for path,field in [('/departments','departmentCode'),('/positions','positionCode'),('/qualification-levels','levelCode')]:
  base=state[path][0];body={k:v for k,v in base.items() if k in [field,'departmentName','positionName','levelName','description','isActive']}
  check('CAT-DUP-'+path,'POST',path,body,expected=(409,));check('CAT-BLANK-'+path,'POST',path,{**body,field:'   '},expected=(400,))
 dep=state['/departments'][0]['departmentId'];pos=state['/positions'][0]['positionId'];year=datetime.date.today().year
 for i in range(5):
  data={'employeeCode':prefix+'_NV'+str(i),'fullName':prefix+' '+['Lê Ngọc Hân','Nguyễn Văn An','HR phòng A','HR phòng B','QA quản trị'][i],'birthDate':'1995-02-28','hireDate':str(datetime.date.today()-datetime.timedelta(days=3)),'departmentId':dep if i!=3 else state['/departments'][1]['departmentId'],'positionId':pos,'email':prefix.lower()+'_'+str(i)+'@example.test','phone':None}
  row=check('EMP-CREATE-'+str(i),'POST','/employees',data,expected=(201,200));
  if not row:raise RuntimeError('Employee create failed')
  state.setdefault('employees',[]).append(row);save()
 level=state['/qualification-levels'][0]['qualificationLevelId'];emp=state['employees'][0]['employeeId']
 for i in range(2):check('QUA-CREATE-'+str(i),'POST',f'/employees/{emp}/qualifications',{'qualificationLevelId':level,'major':prefix+' Kế toán','institution':prefix+' Trường Hà Nội','graduationYear':2020+i,'certificateNumber':prefix+'_CERT'+str(i),'isPrimary':True},expected=(201,200))
 paths=['/employees?page=0&size=10','/employees/search?keyword=han','/departments?page=0&size=10','/positions?page=0&size=10','/qualification-levels?page=0&size=10','/employee-assignments/search?keyword=han','/employee-qualifications/search?keyword=ha%20noi','/leave-types?page=0&size=10','/leave-entitlements?page=0&size=10','/employee-requests?page=0&size=10','/audit-logs?page=0&size=10','/payroll-periods','/payroll-batches','/contract-types','/contracts?page=0&size=10']
 for path in paths:
  check('READ-'+path,'GET',path);check('NOAUTH-'+path,'GET',path,role='none',expected=(401,));c,b,ms=call('GET',path,token='invalid.token.signature');results.append({'id':'BADTOKEN-'+path,'method':'GET','path':path,'status':c,'body':scrub(b),'result':'PASS' if c==401 else 'FAIL','expected':[401],'role':'invalid'});save()
 for path in ['/employees/search','/employee-assignments/search','/employee-qualifications/search','/departments','/positions','/qualification-levels','/leave-types','/leave-entitlements','/employee-requests']:
  for query in ['page=-1&size=10','page=0&size=0','page=0&size=100000']:
   check('PAGING-'+path+query,'GET',path+'?'+query,expected=(400,))
  for kw in ['%25','_','%27%20OR%201%3D1--','%3Cscript%3E']:
   check('SEARCH-'+path+kw,'GET',path+'?keyword='+kw)
 check('QUA-PRIMARY','GET',f'/employees/{emp}/qualifications');check('QUA-ALL','GET','/employee-qualifications/search')
 check('QUA-BADYEAR','POST',f'/employees/{emp}/qualifications',{'qualificationLevelId':level,'graduationYear':year+2},expected=(400,409))
 check('QUA-BADLEVEL','POST',f'/employees/{emp}/qualifications',{'qualificationLevelId':99999999,'graduationYear':2020},expected=(400,404))
 check('EMP-BADDATE','POST','/employees',{'employeeCode':prefix+'_FUTURE','fullName':prefix+' future','hireDate':'2099-01-01','departmentId':dep,'positionId':pos},expected=(400,409))
 print('HTTP complete',len(results),'passed',sum(r['result']=='PASS' for r in results),'failed',sum(r['result']=='FAIL' for r in results))
if __name__=='__main__':
 try:main()
 except Exception as e:state['blocked']=str(e);save();print('QA stopped dependent chain:',str(e))

