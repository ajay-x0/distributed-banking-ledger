# Failure lab

Run on simulated local data. Capture payment IDs, balances, journal counts, state transitions and logs before and after each exercise. Do not describe these drills as verified until you execute them.

## Duplicate requests and concurrent spending

The automated Testcontainers suite creates isolated wallets for each test. `LedgerIntegrationTest` submits 20 concurrent Rs 10 reservations against Rs 100 available and requires exactly 10 accepts, then verifies the stored hold. It also races identical operations and transfers in opposite directions. `PaymentIntegrationTest` races 20 submissions using one key and requires one payment ID.

Run `mvn verify` with Docker running. A failing assertion is a release gate, not a reason to lower the concurrency or disable the test.

## Stop the payment worker

Submit a payment and immediately run:

```bash
docker compose kill -s SIGKILL payment
docker compose up -d payment
```

Poll the same payment ID through the gateway. Timing is nondeterministic: the worker may have completed before the kill. For deterministic transaction rollback and lost-response coverage, use `LedgerIntegrationTest.crashBeforeCommitRollsBackEveryEffect` and `PaymentWorkerTest.lostSettlementResponseRetriesSameOperation`. A true process-kill drill complements these tests rather than replacing them.

Expected: one journal, one debit and one credit. Repeating the original API key must still return the original payment ID.

## Broker unavailable

```bash
docker compose stop kafka
```

Submit a new payment. The ledger can settle without the broker, while the outbox retains unpublished events. Inspect the count:

```bash
docker compose exec postgres psql -U postgres -d bank_payment -c "SELECT count(*) FROM outbox WHERE published_at IS NULL;"
docker compose start kafka
```

After recovery, unpublished rows drain and analytics eventually catch up. Outbox events published before a lost acknowledgment may be delivered again; inbox deduplication should preserve one notification per payment.

## Fraud service unavailable

```bash
docker compose stop fraud
```

Submit an affordable transfer. Available balance falls when the reservation is made, while posted balance remains unchanged. The worker retries with backoff and eventually records `MANUAL_REVIEW` after eight failures. Depending on scheduling, this takes several minutes.

```bash
docker compose start fraud
ADMIN_TOKEN=$(python3 scripts/token.py admin)
```

After checking the recorded `resume_state`, send an authenticated POST to `/api/admin/payments/{id}/resume` with the admin token. The worker continues the previous step. Do not manually edit balances or release uncertain settlements.

## PostgreSQL unavailable

```bash
docker compose stop postgres
```

New payment requests should fail rather than acknowledge an unpersisted request. In-flight calls may have an uncertain outcome; keep their idempotency keys. Restart PostgreSQL, retry with the same keys and reconcile.

```bash
docker compose start postgres
```

## Poison record and dead-letter topic

Insert malformed input using the Kafka console producer:

```bash
printf '%s\n' 'not-json' | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:9092 --topic bank.events
```

Inspect the DLT:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic bank.events.DLT --from-beginning --max-messages 1
```

A malformed JSON event cannot be fixed merely by retrying. Inspect its exception headers and source coordinates; repair the producer/schema or application fault first. For a valid record whose processing bug has been fixed, re-publish the original envelope to `bank.events`, retaining `eventId` and the original payment UUID as key. Record the operator action. The project has no automatic DLT replay daemon or “replay everything” command.

## Reconciliation and evidence

Call `/api/admin/reconciliation` with an admin token. All three arrays should be empty: unbalanced journals, balance mismatches and reservation mismatches. Notifications and analytics may lag because they are eventually consistent; ledger balances do not.

A successful reconciliation checks the current accounting relationships. It is not proof of HA, throughput, regulatory compliance or recovery from disk loss. The single-node development Kafka and PostgreSQL configuration deliberately has no high-availability guarantee.
