#!/usr/bin/env python3
"""Read-only integration check for UI hosting, proxying and demo authentication."""
import argparse
import json
import urllib.error
import urllib.request


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--base', default='http://localhost:3000')
args = parser.parse_args()


def request(path, token=None):
    headers = {'Authorization': 'Bearer ' + token} if token else {}
    req = urllib.request.Request(args.base + path, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            return response.status, response.headers, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.headers, error.read()


status, _, health = request('/health')
assert status == 200 and health == b'ok', f'UI health failed: {status} {health!r}'

status, _, page = request('/')
assert status == 200 and b'Banking Ledger' in page and b'Transaction Simulator' in page, 'UI page was not served'

status, headers, raw_token = request('/demo/token/alice')
assert status == 200, f'Demo token endpoint failed: {status}'
assert 'no-store' in headers.get('Cache-Control', ''), 'Demo token response must not be cached'
issued = json.loads(raw_token)
assert issued['subject'] == 'alice' and issued['scope'] == 'payments' and issued['token'], 'Malformed demo token response'

account = '00000000-0000-0000-0000-000000000001'
status, _, raw_balance = request('/api/accounts/' + account + '/balance', issued['token'])
assert status == 200, f'UI-to-Gateway proxy failed: {status}'
balance = json.loads(raw_balance)
assert balance['accountId'] == account and balance['currency'] == 'INR', 'Unexpected balance response'

status, _, _ = request('/demo/token/unknown-user')
assert status == 404, f'Unknown demo identity should be rejected, received {status}'

print('PASS: UI hosting, proxy, demo authentication, authenticated balance')
