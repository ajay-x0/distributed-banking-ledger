#!/usr/bin/env python3
"""Live end-to-end check; needs running Compose stack. No third-party Python dependencies."""
import argparse,json,subprocess,sys,time,urllib.request,urllib.error,uuid
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--base',default='http://localhost:8080');p.add_argument('--wait',type=int,default=120);args=p.parse_args()
root=Path(__file__).resolve().parents[1]
def token(user):return subprocess.check_output([sys.executable,str(root/'scripts/token.py'),user],text=True).strip()
alice,bob,admin=token('alice'),token('bob'),token('admin')
A='00000000-0000-0000-0000-000000000001';B='00000000-0000-0000-0000-000000000002'
def call(path,method='GET',body=None,key=None,auth=alice):
 headers={'Authorization':'Bearer '+auth,'Content-Type':'application/json'}
 if key:headers['Idempotency-Key']=key
 req=urllib.request.Request(args.base+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
 try:
  with urllib.request.urlopen(req,timeout=10) as r:return r.status,json.load(r)
 except urllib.error.HTTPError as e:return e.code,json.loads(e.read() or b'{}')
def expect(condition,message):
 if not condition:raise AssertionError(message)
def wait_payment(pid):
 end=time.time()+90
 while time.time()<end:
  status,result=call('/api/payments/'+pid)
  expect(status==200,f'Poll failed: {status} {result}')
  if result['state'] in ['COMPLETED','REJECTED','MANUAL_REVIEW']:return result
  time.sleep(1)
 raise AssertionError('Payment did not finish within 90 seconds')
end=time.time()+args.wait
while True:
 try:
  status,before=call('/api/accounts/'+A+'/balance')
  if status==200:break
 except (OSError,ValueError):pass
 if time.time()>end:raise SystemExit('Services not ready within wait period; inspect docker compose logs.')
 time.sleep(2)
transfer={'source':A,'destination':B,'amountMinor':1000000,'currency':'INR'};key='smoke-'+str(uuid.uuid4())
status,created=call('/api/payments','POST',transfer,key);expect(status==202,f'Create failed: {created}');pid=created['id']
status,repeated=call('/api/payments','POST',transfer,key);expect(status==202 and repeated['id']==pid,'Duplicate produced different payment')
status,_=call('/api/payments','POST',{**transfer,'amountMinor':100},key);expect(status==409,'Changed payload should conflict')
finished=wait_payment(pid);expect(finished['state']=='COMPLETED',f'Transfer did not complete: {finished}')
_,after=call('/api/accounts/'+A+'/balance');expect(before['balanceMinor']-after['balanceMinor']==1000000,'Unexpected debit')
status,_=call('/api/payments/'+pid,auth=bob);expect(status==404,'Another user can read payment')
status,_=call('/api/accounts/'+A+'/balance',auth=bob);expect(status==403,'Another user can read balance')
status,_=call('/api/admin/reconciliation');expect(status==403,'Non-admin can reconcile')
_,denied=call('/api/payments','POST',{**transfer,'amountMinor':2000001},'smoke-'+str(uuid.uuid4()));finished=wait_payment(denied['id']);expect(finished['state']=='REJECTED','Fraud check did not reject')
_,released=call('/api/accounts/'+A+'/balance');expect(released['reservedMinor']==0,'Rejected transfer leaked reservation');expect(released['balanceMinor']==after['balanceMinor'],'Fraud rejection changed balance')
status,reconciliation=call('/api/admin/reconciliation',auth=admin);expect(status==200 and all(not v for v in reconciliation.values()),f'Reconciliation failed: {reconciliation}')
end=time.time()+60
while True:
 status,analytics=call('/api/admin/analytics',auth=admin)
 if status==200 and any(x['name']=='PaymentCOMPLETED' and x['value']>=1 for x in analytics):break
 if time.time()>end:raise AssertionError('Notification projection did not catch up')
 time.sleep(2)
print('PASS: transfer, duplicate, conflict, authorization, fraud compensation, reconciliation, Kafka projection')
