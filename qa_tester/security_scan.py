import api_test as a,json,concurrent.futures
runtime=json.loads((a.OUT/'runtime.json').read_text(encoding='utf-8'));routes=[]
for route in runtime['routes']:
 for path in route['paths']:
  path=a.re.sub(r'\{[^}]+\}','999999999',path)
  for method in route['methods']:
   if method not in ['GET','POST','PUT','PATCH','DELETE']:continue
   if path in ['/api/health','/api/auth/login','/error']:continue
   routes.append((method,path,route['controller']))
def run(item):
 method,path,controller=item
 rows=[]
 for role,token in [('none',None),('invalid','invalid.token.signature')]:
  code,b,ms=a.call(method,path,{} if method in ['POST','PUT','PATCH'] else None,token)
  rows.append({'id':'BEG-01-'+role+'-'+method+path,'method':method,'path':path,'controller':controller,'role':role,'expected':[401],'status':code,'body':a.scrub(b),'ms':ms,'result':'PASS' if code==401 else 'FAIL'})
 return rows
results=[]
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
 for batch in pool.map(run,routes):results+=batch
(a.OUT/'security-scan.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8');print('Security scan',len(results),'fail',sum(r['result']=='FAIL' for r in results))
