#!/usr/bin/env python3
"""Development JWT issuer. Never put this symmetric signing secret in a public client."""
import base64,hashlib,hmac,json,time,os,argparse
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('subject',choices=['alice','bob','charlie','admin'],nargs='?',default='alice');a=p.parse_args()
config={}
f=Path(__file__).resolve().parents[1]/'.env'
if f.exists():config=dict(line.split('=',1) for line in f.read_text().splitlines() if '=' in line and not line.startswith('#'))
secret=os.getenv('JWT_SECRET',config.get('JWT_SECRET',''))
if len(secret)<32:raise SystemExit('Run python3 scripts/init_env.py first or set JWT_SECRET.')
def b64(b):return base64.urlsafe_b64encode(b).rstrip(b'=')
now=int(time.time());header=b64(json.dumps({'alg':'HS256','typ':'JWT'},separators=(',',':')).encode())
payload=b64(json.dumps({'iss':'bank-demo','aud':['bank-api'],'sub':a.subject,'scope':'admin' if a.subject=='admin' else 'payments','iat':now,'exp':now+3600},separators=(',',':')).encode())
body=header+b'.'+payload
print((body+b'.'+b64(hmac.new(secret.encode(),body,hashlib.sha256).digest())).decode())
