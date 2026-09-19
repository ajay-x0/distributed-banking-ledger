#!/usr/bin/env python3
from pathlib import Path
import secrets,os
root=Path(__file__).resolve().parents[1]
p=root/'.env'
if p.exists():
 print('.env already exists; preserving credentials.')
else:
 values={key:secrets.token_urlsafe(36) for key in ['DB_PASSWORD','JWT_SECRET','SERVICE_PASSWORD','GRAFANA_PASSWORD']}
 fd=os.open(p,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
 with os.fdopen(fd,'w') as f:f.write(''.join(f'{k}={v}\n' for k,v in values.items()))
 print('Created local .env. Do not commit it.')
