"""Isolated real-JWT HTTP audit. Never connects to hrm_db. No secrets in evidence."""
from pathlib import Path
import os,sys,json,re,subprocess,secrets,time,threading,urllib.request,urllib.error,tempfile,datetime,base64,hmac,hashlib
ROOT=Path(__file__).resolve().parents[2]; QA=ROOT/'qa'; E=QA/'evidence'; JAVA=Path('C:/Program Files/Java/jdk-23/bin/java.exe')
props={}
for line in (ROOT/'HRM_System/src/main/resources/application-local.properties').read_text(encoding='utf-8-sig').splitlines():
 if '=' in line and not line.lstrip().startswith(('#','!')):k,v=line.split('=',1);props[k.strip()]=v.strip()
url=os.environ.get('SPRING_DATASOURCE_URL') or props.get('spring.datasource.url','')
if not re.search(r'databaseName=hrm_test(?:;|$)',url,re.I):raise SystemExit('Refusing non-hrm_test datasource')
server=re.match(r'jdbc:sqlserver://([^;]+)',url)[1].replace(':',',')
login=os.environ.get('SPRING_DATASOURCE_USERNAME') or props['spring.datasource.username'];secret=os.environ.get('SPRING_DATASOURCE_PASSWORD') or props['spring.datasource.password']
backup=json.loads((E/'database-backup.json').read_text(encoding='utf-8-sig'));assert backup['database']=='hrm_test' and backup['verifyOnly']=='PASS'
cp=str(E/'classes')+';'+str(ROOT/'HRM_System/target/classes')+';'+(E/'backend-classpath.txt').read_text().strip()
child=os.environ.copy();child.update(SPRING_DATASOURCE_URL=url,SPRING_DATASOURCE_USERNAME=login,SPRING_DATASOURCE_PASSWORD=secret,JWT_SECRET=secrets.token_urlsafe(48),QA_RUNTIME_EVIDENCE=str(E/'runtime-beans.json'))
sqlenv=os.environ.copy();sqlenv['SQLCMDPASSWORD']=secret
ui_only='--ui-only' in sys.argv
retest='--retest' in sys.argv
RESULT_FILE='http-retest-results.json' if retest else 'http-ui-session-results.json' if ui_only else 'http-results.json'
STATE_FILE='retest-audit-state.json' if retest else 'ui-audit-state.json' if ui_only else 'api-audit-state.json'
if retest:child['QA_RUNTIME_EVIDENCE']=str(E/'runtime-beans-retest.json')
results=[];tokens={};ids={};tag='QA_'+datetime.datetime.now().strftime('%Y%m%d_%H%M%S')+'_'+secrets.token_hex(2)
password=secrets.token_urlsafe(24)+'!aA1';base='http://127.0.0.1:2009';tmp=Path(tempfile.mkdtemp(prefix='hrm_qa_'));cred=tmp/'ui.json';process=None

def scrub(text):
 text=re.sub(r'jdbc:sqlserver://[^\s\]]+','[DATASOURCE REDACTED]',str(text));text=re.sub(r'eyJ[\w-]+\.[\w-]+\.[\w-]+','[JWT REDACTED]',text);text=re.sub(r'(Using generated security password:).*',r'\1 ***',text)
 for value in (secret,password,child['JWT_SECRET']):text=text.replace(value,'***')
 return text

def sanitize(value):
 if isinstance(value,dict):return {k:('***' if re.search('password|token|secret',k,re.I) else sanitize(v)) for k,v in value.items()}
 if isinstance(value,list):return [sanitize(v) for v in value]
 if isinstance(value,str):return scrub(value)
 return value

def sql(query):
 guard="SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON; SET ANSI_WARNINGS ON; SET ANSI_PADDING ON; SET ARITHABORT ON; SET CONCAT_NULL_YIELDS_NULL ON; SET NUMERIC_ROUNDABORT OFF; SET NOCOUNT ON; IF DB_NAME()<>'hrm_test' THROW 51000,'QA database guard',1; "
 p=subprocess.run(['sqlcmd','-S',server,'-U',login,'-C','-d','hrm_test','-b','-l','15','-W','-h','-1','-w','65535','-Q',guard+query],env=sqlenv,capture_output=True,encoding='utf-8',errors='replace')
 if p.returncode:raise RuntimeError('QA SQL failed: '+scrub(p.stdout+p.stderr)[:1800])
 return p.stdout.strip()

def request(method,path,body=None,role=None,token=None):
 headers={'Content-Type':'application/json'};token=token if token is not None else tokens.get(role)
 if token:headers['Authorization']='Bearer '+token
 data=json.dumps(body,ensure_ascii=False).encode('utf-8') if body is not None else None
 req=urllib.request.Request(base+path,data=data,headers=headers,method=method)
 try:
  with urllib.request.urlopen(req,timeout=30) as r:status=r.status;raw=r.read().decode('utf-8',errors='replace')
 except urllib.error.HTTPError as e:status=e.code;raw=e.read().decode('utf-8',errors='replace')
 try:content=json.loads(raw)
 except ValueError:content=raw
 return status,content

def check(case,method,path,expected,body=None,role=None,token=None,note=''):
 status,data=request(method,path,body,role,token);allowed=[expected] if isinstance(expected,int) else expected
 results.append(dict(id=case,method=method,path=path,role=role or 'anonymous',expected=allowed,actualStatus=status,status='PASS' if status in allowed else 'FAIL',request=sanitize(body),response=sanitize(data),note=note))
 save();return status,data

def save():
 (E/RESULT_FILE).write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')

def fixtures():
 assert sql('SELECT DB_NAME();')=='hrm_test'
 digest=subprocess.run([str(JAVA),'-cp',cp,'QaPasswordHash'],input=password+'\n',capture_output=True,text=True,check=True).stdout.strip()
 statements=[f"DECLARE @tag nvarchar(50)=N'{tag}';"]
 for key in ['A','B']:statements.append(f"INSERT dbo.departments(department_code,department_name) VALUES(@tag+'_{key}',@tag+' department {key}');")
 statements.append("INSERT dbo.positions(position_code,position_name) VALUES(@tag+'_P',@tag+' regular position');")
 actors={'admin':'ADMIN','hrA':'HR','hrB':'HR','employeeA':'EMPLOYEE','employeeB':'EMPLOYEE','other':'EMPLOYEE','retired':'EMPLOYEE'}
 for key,role in actors.items():
  status="'DA_NGHI'" if key=='retired' else "'DANG_LAM'";term="'2025-01-01'" if key=='retired' else 'NULL'
  statements.append(f"INSERT dbo.employees(employee_code,full_name,hire_date,employment_status,termination_date,address,identity_number) VALUES(@tag+'_{key}',@tag+' {key}','2020-01-01',{status},{term},'QA_PRIVATE_HOME_{key}','QA_PRIVATE_ID_{key}'); INSERT dbo.accounts(employee_id,role_id,username,password_hash,is_active) SELECT e.employee_id,r.role_id,@tag+'_{key}','{digest}',{0 if key=='retired' else 1} FROM dbo.employees e CROSS JOIN dbo.roles r WHERE e.employee_code=@tag+'_{key}' AND r.role_code='{role}';")
  if key!='retired':
   dept='B' if key in ['hrB','employeeB','other'] else 'A'
   statements.append(f"INSERT dbo.employee_assignments(employee_id,department_id,position_id,effective_from) SELECT e.employee_id,d.department_id,p.position_id,'2020-01-01' FROM dbo.employees e CROSS JOIN dbo.departments d CROSS JOIN dbo.positions p WHERE e.employee_code=@tag+'_{key}' AND d.department_code=@tag+'_{dept}' AND p.position_code=@tag+'_P';")
 for key,dept in [('hrA','A'),('hrB','B')]:statements.append(f"INSERT dbo.hr_department_scopes(hr_account_id,department_id,effective_from,created_by) SELECT h.account_id,d.department_id,'2020-01-01',a.account_id FROM dbo.accounts h CROSS JOIN dbo.departments d CROSS JOIN dbo.accounts a WHERE h.username=@tag+'_{key}' AND d.department_code=@tag+'_{dept}' AND a.username=@tag+'_admin';")
 sql('BEGIN TRY BEGIN TRAN; '+' '.join(statements)+' COMMIT; END TRY BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK; THROW; END CATCH;')
 for key in actors:
  values=sql(f"SELECT CONVERT(varchar(20),a.account_id)+'|'+CONVERT(varchar(20),e.employee_id) FROM dbo.accounts a JOIN dbo.employees e ON e.employee_id=a.employee_id WHERE a.username='{tag}_{key}';").split('|');ids[key]={'account':int(values[0]),'employee':int(values[1])}
 for key in ['A','B']:ids['department'+key]=int(sql(f"SELECT department_id FROM dbo.departments WHERE department_code='{tag}_{key}';"))
 ids['position']=int(sql(f"SELECT position_id FROM dbo.positions WHERE position_code='{tag}_P';"))
 (E/'fixture-manifest.json').write_text(json.dumps({'prefix':tag,'ids':ids,'credentials':'ephemeral outside repository; removed on shutdown'},indent=2),encoding='utf-8')
 cred.write_text(json.dumps({'username':tag+'_admin','hrUsername':tag+'_hrA','password':password}),encoding='utf-8');os.chmod(cred,0o600)
 (E/'runtime-credentials-location.json').write_text(json.dumps({'path':str(cred),'note':'Temporary QA credentials; do not export contents.'}),encoding='utf-8')

def api_tests():
 check('AUTH-01-health','GET','/api/health',200)
 for key in ['admin','hrA','hrB','employeeA','employeeB','retired']:
  status,data=check('AUTH-login-'+key,'POST','/api/auth/login',401 if key=='retired' else 200,{'username':tag+'_'+key,'password':password})
  if status==200:tokens[key]=data['accessToken']
 check('AUTH-02-wrong','POST','/api/auth/login',401,{'username':tag+'_admin','password':'wrong-password-for-QA'})
 check('AUTH-03-missing','POST','/api/auth/login',400,{})
 check('AUTH-04-invalid','GET','/api/auth/me',401,token='not-a-jwt')
 token=tokens['employeeA'];a,b,_=token.split('.');payload=json.loads(base64.urlsafe_b64decode(b+'='*(-len(b)%4)));payload['exp']=int(time.time())-60;payload['iat']=payload['exp']-60
 b=base64.urlsafe_b64encode(json.dumps(payload,separators=(',',':')).encode()).decode().rstrip('=');alg=json.loads(base64.urlsafe_b64decode(a+'='*(-len(a)%4)))['alg'];digest={'HS256':hashlib.sha256,'HS384':hashlib.sha384,'HS512':hashlib.sha512}[alg];sig=base64.urlsafe_b64encode(hmac.new(child['JWT_SECRET'].encode(),(a+'.'+b).encode(),digest).digest()).decode().rstrip('=')
 check('AUTH-05-expired','GET','/api/auth/me',401,token=a+'.'+b+'.'+sig)
 ea,eb=ids['employeeA']['employee'],ids['employeeB']['employee']
 for role in ['admin','hrA','employeeA']:
  status,data=check('RBAC-own-details-'+role,'GET',f'/employees/{ea}/detail',200,role=role)
  if status==200:
   visible='QA_PRIVATE_HOME_employeeA' in json.dumps(data)
   results.append(dict(id='RBAC-private-'+role,status='PASS' if visible==(role=='employeeA') else 'FAIL',actual={'privateVisible':visible},expected={'privateVisible':role=='employeeA'}))
 check('RBAC-other-employee','GET',f'/employees/{eb}/detail',403,role='employeeA')
 check('RBAC-hr-outside','GET',f'/employees/{eb}/detail',403,role='hrA')
 check('RBAC-hr-search','GET','/employees/search?keyword='+tag+'&page=0&size=100',200,role='hrA')
 for path in ['/payroll-batches','/payroll-periods','/audit-logs','/api/admin/hr-scopes','/employee-assignments','/api/management/shifts','/api/management/schedules','/api/management/attendance','/api/reports/employees']:
  check('RBAC-employee-'+path.strip('/').replace('/','-'),'GET',path,403,role='employeeA')
 runtime=json.loads((E/'runtime-beans.json').read_text());routes=runtime['routes'];count=0
 for item in routes:
  if 'GET' not in item['methods']:continue
  for path in item['paths']:
   if path in ['/api/health','/api/auth/login','/hello','/error']:continue
   concrete=re.sub(r'\{[^}]+\}',str(ea),path)
   if '*' in concrete:continue
   check('RBAC-anonymous-'+str(count),'GET',concrete,401);count+=1
 year=2400+secrets.randbelow(500)
 status,period=check('PAY-period-QA','POST','/payroll-periods',201,{'periodCode':tag+'_PAY','periodStart':f'{year}-01-01','periodEnd':f'{year}-02-01'},role='admin')
 if status==201:
  pid=period.get('periodId');status,batch=check('PAY-employee-create-draft','POST','/payroll-batches/draft',403,{'periodId':pid},role='employeeA')
  if status==403 and retest:
   status,batch=check('PAY-admin-create-draft','POST','/payroll-batches/draft',201,{'periodId':pid},role='admin')
  if status==201:
   bid=batch['batchId'];sql(f"INSERT dbo.payroll_details(batch_id,employee_id) VALUES({bid},{eb});")
   check('PAY-other-details','GET',f'/payroll-batches/{bid}/details',403,role='employeeA')
   check('PAY-employee-lock-spoof','POST',f'/payroll-batches/{bid}/lock?actorAccountId={ids["hrB"]["account"]}',403,role='employeeA')
   results.append(dict(id='PAY-lock-actor-evidence',status='OBSERVED',actual=sql(f"SELECT status+'|'+COALESCE(CONVERT(varchar(20),locked_by),'NULL') FROM dbo.payroll_batches WHERE batch_id={bid};"),note='Only newly created QA batch; employee caller must not lock or choose actor.'))
 save()

if (E/'api-audit-error.txt').exists():
 (E/'api-audit-error.txt').rename(E/('api-audit-attempt-'+datetime.datetime.now().strftime('%H%M%S')+'-error.txt'))
try:
 log=open(E/('qa-retest-server.log' if retest else 'qa-ui-server.log' if ui_only else 'qa-server.log'),'w',encoding='utf-8')
 args=[str(JAVA),'-cp',cp,'QaBackendLauncher','--server.address=127.0.0.1','--server.port=2009','--spring.profiles.active=local','--spring.jpa.show-sql=false','--spring.jpa.hibernate.ddl-auto=none','--hrm.task.api-enabled=true','--hrm.contracts.expiry-job-enabled=false','--personnel.resignations.scheduler-enabled=false','--hrm.attendance.missing-checkout-scan-enabled=false']
 process=subprocess.Popen(args,cwd=ROOT/'HRM_System',env=child,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
 def drain():
  for line in process.stdout:log.write(scrub(line));log.flush()
 thread=threading.Thread(target=drain,daemon=True);thread.start()
 for _ in range(150):
  if process.poll() is not None:raise RuntimeError('QA backend exited; see sanitized server log')
  try:
   if request('GET','/api/health')[0]==200 and (E/'runtime-beans.json').exists():break
  except (OSError,urllib.error.URLError):pass
  time.sleep(1)
 else:raise RuntimeError('QA backend startup timeout')
 fixtures();
 if not ui_only:api_tests()
 (E/STATE_FILE).write_text(json.dumps({'state':'API_COMPLETE_SERVER_RUNNING','prefix':tag,'pid':process.pid,'port':2009,'results':len(results),'fail':sum(r['status']=='FAIL' for r in results)}),encoding='utf-8')
 print('API audit complete; QA-only server kept for UI verification. No credentials printed.',flush=True)
 deadline=time.time()+(0 if '--exit-after-tests' in sys.argv else 3600)
 while time.time()<deadline and not(E/'stop-qa-server').exists():time.sleep(1)
except Exception as ex:
 (E/'api-audit-error.txt').write_text(scrub(repr(ex)),encoding='utf-8');print('QA audit blocked; see sanitized error evidence.',flush=True)
finally:
 save()
 if process is not None:process.terminate();process.wait(timeout=30)
 try:
  if ids:sql(f"UPDATE dbo.accounts SET is_active=0,token_version=token_version+1 WHERE username LIKE '{tag}[_]%';")
 except Exception:pass
 cred.unlink(missing_ok=True);tmp.rmdir()
 (E/STATE_FILE).write_text(json.dumps({'state':'STOPPED','prefix':tag,'stoppedAt':datetime.datetime.now().astimezone().isoformat(),'temporaryCredentialsRemoved':True}),encoding='utf-8')
