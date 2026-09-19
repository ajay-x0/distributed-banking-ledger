# Internal API contracts

Public endpoints and examples are in the README. Backend services are private. Internal endpoints use HTTP Basic with user `service` and `SERVICE_PASSWORD`. These are development credentials; use private TLS/service identity in a real deployment.

| Service | Method and path | Body / result |
| --- | --- | --- |
| Account | GET `/internal/accounts/{accountId}/owner/{owner}` | `{ "owned": true }`; 403 or 404 otherwise |
| Ledger | POST `/internal/ledger/reserve` | `LedgerCommand` below; returns durable operation row |
| Ledger | POST `/internal/ledger/{paymentId}/settle` | No body; idempotent when already `POSTED` |
| Ledger | POST `/internal/ledger/{paymentId}/release` | No body; idempotent when already `RELEASED` |
| Ledger | GET `/internal/ledger/{paymentId}` | Raw operation state for investigation |
| Fraud | POST `/internal/fraud/check` | `{ "paymentId": "uuid", "owner": "alice", "amountMinor": 1000000 }` |

Ledger reserve body:

```json
{
  "paymentId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "owner": "alice",
  "transfer": {
    "source": "00000000-0000-0000-0000-000000000001",
    "destination": "00000000-0000-0000-0000-000000000002",
    "amountMinor": 1000000,
    "currency": "INR"
  }
}
```

Terminal contradictions are 409: a released operation cannot settle, and a posted operation cannot release. `REJECTED` means the attempted reservation was declined for insufficient available funds. Retrying it does not reevaluate later balances.

Event envelope:

```json
{
  "eventId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "aggregateId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  "type": "PaymentCOMPLETED",
  "version": 1,
  "data": { "state": "COMPLETED" }
}
```

Events use topic `bank.events`, partitioned by payment UUID. Separate outboxes and retry scheduling do not guarantee complete business-event ordering. Consumers must tolerate duplicate and out-of-order events. The implemented projection only needs terminal payment outcomes; ledger bookkeeping does not consume these events.
