from pathlib import Path
from decimal import Decimal as D, ROUND_HALF_UP, getcontext
import json
import sys
getcontext().prec=40
actual=json.loads(Path(sys.argv[1] if len(sys.argv)>1 else 'qa/evidence/payroll-policy-probe.json').read_text(encoding='utf-8'))
def money(n):return n.quantize(D('1'),rounding=ROUND_HALF_UP)
def tax(n):
 if n<=0:return D(0)
 brackets=[(D(5000000),D('.05')),(D(10000000),D('.1')),(D(18000000),D('.15')),(D(32000000),D('.2'))]
 total=D(0);old=D(0)
 for end,rate in brackets:
  total+=max(D(0),min(n,end)-old)*rate
  if n<=end:return money(total)
  old=end
 return money(total+(n-old)*D('.25'))
rows=[]
for c in actual['engineCases']:
 a=c['input'];base,days,paid,ot,meal,other=map(D,a[1:7]);basis=a[7]
 daily=base/D(22) if basis=='THANG' else base if basis=='NGAY' else base*8
 work=money(daily*(days+paid));overtime=money(daily/8*ot*D('1.5'));gross=work+overtime+meal+other
 insurance=money((base if basis=='THANG' else base*22 if basis=='NGAY' else base*8*22)*D('.105'))
 taxable=gross-min(meal,D(730000))-insurance-D(11000000);pit=tax(taxable);net=gross-insurance-pit
 status='FAIL' if net!=D(str(c['net'])) else 'PASS-THEO-CODE' if taxable>D(5000000) else 'PASS'
 rows.append(dict(case=c['case'],kind='calculation-engine only; not database reconciliation',expectedGross=str(gross),expectedInsurance=str(insurance),expectedTaxable=str(taxable),expectedTax=str(pit),expectedNet=str(net),actualNet=str(c['net']),delta=str(D(str(c['net']))-net),status=status))
Path(sys.argv[2] if len(sys.argv)>2 else 'qa/evidence/payroll-oracle-results.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
for r in rows:print(r['case'],r['status'],'net delta',r['delta'])
