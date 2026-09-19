#!/usr/bin/env python3
from pathlib import Path
from string import Template
import os
root=Path(__file__).resolve().parent
out=root/'rendered';out.mkdir(exist_ok=True)
for f in root.glob('*.yml'):
 (out/f.name).write_text(Template(f.read_text()).substitute(os.environ))
print('Rendered Kubernetes manifests. Review before applying.')
