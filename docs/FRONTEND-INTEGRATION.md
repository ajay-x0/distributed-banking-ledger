# Transaction Simulator Integration Guide

The transaction simulator is a static HTML/CSS/JavaScript application served by Nginx. It uses the existing API Gateway and does not call internal services directly.

## Runtime path

```text
Browser http://localhost:3000
  -> Nginx UI container
     -> /demo/* proxy -> Gateway :8080 (demo identity token)
     -> /api/*  proxy -> Gateway :8080 (authenticated business APIs)
        -> Payment / Account / Ledger / Notification services
```

The Nginx proxy gives the browser one origin, so no broad CORS configuration is required. PostgreSQL, Redis, Kafka, and internal service ports remain private on the Compose network.

## Security boundary

- `DemoTokenController` exists only in the Gateway module.
- It is created only when `bank.demo-auth-enabled=true`.
- Compose enables it with `DEMO_AUTH_ENABLED=true` for the local `lite` stack.
- The browser receives a 15-minute JWT but never receives `JWT_SECRET`.
- Only `alice`, `bob`, `charlie`, and `admin` can be selected.
- Disable or remove `DEMO_AUTH_ENABLED` outside a local demonstration.

This is a local convenience identity switcher, not signup or production authentication.

## Files added

| File | Responsibility |
| --- | --- |
| `frontend/index.html` | Accessible page structure and simulator controls |
| `frontend/styles.css` | Responsive visual layout |
| `frontend/app.js` | Token acquisition, API requests, polling, comparisons, and admin projection |
| `frontend/nginx.conf` | Static hosting and same-origin reverse proxy |
| `frontend/Dockerfile` | 64 MiB Nginx runtime image |
| `gateway-service/.../DemoTokenController.java` | Demo-only server-side JWT creation |

## Start from a clean checkout

From the repository root on Windows PowerShell:

```powershell
py scripts/lite.py check
py scripts/lite.py start
```

`start` builds the six Java images and the UI image, then starts infrastructure, domain services, Gateway, and UI with readiness checks.

Open:

```text
http://localhost:3000
```

The raw Gateway remains available at `http://localhost:8080` for Postman, PowerShell, and the smoke test.

## Integrate with an already-running backend

Because Java code and Compose changed, perform one rebuild:

```powershell
py scripts/lite.py start
```

Do not use `--skip-build` for this first integration run. Existing PostgreSQL, Redis, Kafka, and Maven volumes are preserved.

After the first successful build, routine restarts may use:

```powershell
py scripts/lite.py start --skip-build
```

## Verify the integration

```powershell
py scripts/lite.py status
py scripts/lite.py test
py scripts/lite.py stats
```

Expected results:

- Ten containers are healthy: PostgreSQL, Redis, Kafka, six Java services, and UI.
- `http://localhost:3000/health` returns `ok`.
- The existing smoke test still prints its PASS line.
- The UI integration test prints `PASS: UI hosting, proxy, demo authentication, authenticated balance`.
- The UI loads Alice, Bob, and Charlie balances.

## Use the simulator

1. Select Alice, Bob, or Charlie under **Act as**.
2. Select a scenario or enter source, destination, amount, currency, and idempotency key.
3. Click **Submit transfer**.
4. Observe the HTTP 202 response and saga-state polling.
5. Compare posted balances when the payment becomes terminal.
6. Click **Repeat same request** to prove replay safety.
7. Click **Test key conflict** to reuse the key with a changed amount and observe HTTP 409.
8. Select Administrator to inspect reconciliation and Kafka-derived analytics.

## Scenario expectations

| Preset | Request | Expected result |
| --- | --- | --- |
| Successful | ₹10,000 | `COMPLETED`; source debit and destination credit |
| Fraud decline | ₹20,000.01 | `REJECTED`; reservation released; posted balances unchanged |
| Insufficient funds | ₹1,000,000,000 | `REJECTED` during reservation with no posted debit |
| Invalid input | Same source and destination | HTTP 400; no payment created |

The simulator displays intermediate states only when polling happens to observe them. It does not assume every state will be visible because the worker can advance quickly.

## Rebuild only the UI during development

If only files under `frontend/` changed:

```powershell
docker compose --project-name banking-ledger-lite --file compose.yml build ui
docker compose --project-name banking-ledger-lite --file compose.yml up -d --no-deps --force-recreate ui
```

If Gateway or common security code changed, run the full launcher without `--skip-build`.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| UI does not open | `py scripts/lite.py logs ui` and port 3000 availability |
| “Demo authentication failed” | Gateway was rebuilt and has `DEMO_AUTH_ENABLED=true` |
| Balances fail to load | Gateway, Ledger, Redis, and token endpoint health |
| Payment remains active | Payment, Account, Ledger, and Fraud logs |
| Terminal payment but analytics absent | Kafka and Notification logs; projection is eventually consistent |
| HTTP 429 | Reduce polling/manual refreshes and wait for the 60-second window |

The UI never deletes data. Repeated successful demonstrations spend simulated funds from the chosen source account; reverse transfers can restore the intended demo balance.
