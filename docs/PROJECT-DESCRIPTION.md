> Lightweight variant: the business implementation below is retained. The local runtime now uses the lite profile; trace/Prometheus export and dashboard containers are disabled. See README.md and LIGHTWEIGHT-CHANGES.md for the actual startup path and unmeasured memory limits.

# Distributed Banking Ledger and Payment System

## Project description

A distributed Java backend that simulates INR transfers between customer accounts while handling duplicate requests, concurrent withdrawals, service failures and uncertain network outcomes. Six Spring Boot services separate API access, payment coordination, account profiles, ledger accounting, fraud decisions and event-driven notifications/analytics. PostgreSQL is the source of truth for money; Kafka carries durable business events; Redis provides request throttling. The payment workflow reserves funds, obtains a fraud decision, and either posts a balanced transfer or compensates by releasing the reservation. Docker Compose provides the local environment, with Kubernetes application templates and AWS ECR provisioning assets for a later deployment.

This project is designed to demonstrate engineering decisions that you can explain and test. It does not carry a guaranteed resume weight or hiring outcome, and should not be described as a live banking platform.

## Technologies and their actual roles

| Technology | How it is used |
| --- | --- |
| Java 21 | Service implementation, records for transfer commands, integer money arithmetic and concurrency tests. The code does not claim to depend on virtual threads. |
| Spring Boot 3.5.16 | Independent executable services, dependency configuration, REST controllers, scheduling and actuator endpoints. Version is pinned; it is not advertised as the newest major release. |
| Spring Security | JWT validation for user APIs, admin scopes, internal service authentication, and source-account ownership enforcement. |
| Spring Data JPA / Hibernate | Wallet entities with pessimistic row locking and profile entities with optimistic version checking. |
| Spring JDBC | Explicit SQL for idempotency insertion, saga state, journal writes, work claiming, inboxes and outbox publishing. JPA and JDBC use the same datasource transaction manager within a service. |
| PostgreSQL | Separate service databases, ACID transactions, uniqueness constraints, row/advisory locks, journal constraints and reconciliation queries. |
| Flyway | Versioned common outbox and service-specific schema migrations, plus transparent simulated opening data. |
| Kafka / Spring Kafka | At-least-once business-event delivery, keyed records, consumer retries and a dead-letter topic. |
| Redis / Spring Data Redis | Atomic per-user gateway rate limiting. It does not store an authoritative balance or determine whether a withdrawal is affordable. |
| Resilience4j | Separate circuit breakers around ledger, account and fraud calls. Dependency failures reduce repeated outbound calls until a recovery probe is allowed. |
| Docker / Docker Compose | Multi-stage Java 21 image builds and a local six-service environment with persistent data volumes. |
| Kubernetes | Application deployments, replica counts, services, health probes, resource limits and container security settings. Templates require external dependencies and secrets. |
| AWS / Terraform | Concrete ECR repository provisioning and a deployment guide mapping the services to EKS, PostgreSQL to RDS, Redis to ElastiCache and Kafka to MSK. These managed services have not been provisioned by this project delivery. |
| OpenTelemetry / Micrometer | Instrumented HTTP/Kafka operations and trace export to the collector and Jaeger. Scheduled work has its own execution context; the project does not persist and link a complete original trace context through saga/outbox rows. |
| Prometheus / Grafana | Scraped service metrics and provisioned dashboards for HTTP traffic, latency, heap usage, payment transitions and retries, and outbox retries. |
| JUnit 5 / Mockito / Testcontainers | Money rules, token validation, outbox acknowledgment behavior, HTTP failure semantics and PostgreSQL integration tests. |
| GitHub Actions | A Java 21 test job followed by Compose startup and an end-to-end smoke check. |

## How the important concepts are implemented

| Concept | Implementation and reason | Main code location |
| --- | --- | --- |
| Idempotency | Unique `(owner, idempotency_key)` maps retries to one payment. Payload mismatch returns 409. The payment UUID is reused for ledger reserve/settle/release and fraud decisions. This handles retries at both the external API and internal service boundary. | `PaymentService`, `LedgerService`, `FraudController` |
| Pessimistic locking | Wallet rows are selected in UUID order with `PESSIMISTIC_WRITE`. A transaction checks available funds while holding the same locks it uses to reserve or spend. | `WalletRepository.lockAll`, `LedgerService` |
| Optimistic locking | JPA `@Version` prevents lost profile edits. Clients submit the version they read; stale updates fail. Wallet entities also have a version, while spending uses pessimistic locks. | `AccountProfile`, `AccountService`, `Wallet` |
| ACID transactions | Settlement updates both balances, reduces the hold, inserts the journal, records ledger state and writes an outbox event in one ledger database transaction. There is no shared transaction across services. | `LedgerService.finish` |
| Double-entry bookkeeping | Every transfer has exactly two nonzero signed entries that sum to zero. In this customer-liability subledger, a negative delta reduces the source's balance and a positive delta increases the destination's balance. Opening capital is also journalled. This is not a complete bank general ledger or multicurrency accounting model. | Ledger Flyway migration, `LedgerService.finish` |
| Immutable accounting history | Triggers reject journal updates/deletes. Deferred constraint triggers check journal completeness and balance at commit. Normal corrections would require a new reversal journal; a public reversal workflow is intentionally not implemented. | `V2__ledger.sql` |
| Transactional outbox | Business state and the outgoing event commit together. A relay publishes pending records and marks them delivered only after Kafka acknowledgment. Broker failures leave them pending. | `Outbox`, `OutboxRelay` |
| At-least-once delivery and inbox | A crash after publish but before marking can resend an event. The consumer records the event ID and projection in one database transaction. Notification uniqueness additionally guards the payment-level effect. | `EventConsumer` |
| Saga and compensation | A durable orchestrator advances `NEW → RESERVED → APPROVED → COMPLETED`. Fraud rejection follows `RESERVED → RELEASING → REJECTED`. Releasing a reservation compensates the earlier reserve step. The debit and credit themselves remain in one ACID ledger transaction. | `PaymentWorker`, `payment` table |
| Retry with exponential backoff | Payment retries use 2, 4, 8… seconds, capped at 60 plus jitter, persisted in `next_attempt_at`. After eight failures, the previous step is retained for manual review. Outbox retries continue with capped delays; consumer retries are separately bounded. | `MoneyRules.retrySeconds`, `PaymentWorker.retry`, `OutboxRelay`, `KafkaConfig` |
| Dead-letter queue | Consumer records still failing after the configured retry policy are published to `bank.events.DLT`. Failed DLT publication is surfaced so the consumer does not treat it as successful recovery. The failure lab describes replay after fixing the cause. | `KafkaConfig` |
| Rate limiting | A Lua script increments a Redis counter and sets its expiry atomically. A signed user subject identifies the bucket. | `RateLimiter` |
| Circuit breaker | Separate Resilience4j breakers stop repeated calls to failing dependencies and later permit recovery checks. A breaker opening delays the saga; it does not claim the payment failed. | `PaymentWorker` |
| Reconciliation | Admin queries compare balances with signed journal sums, reservations with active holds, and journal totals with zero. | `LedgerController.reconciliation` |
| Authorization | User APIs validate JWTs; owner checks protect profiles, balances, entries and initiating-user payments. Admin-only operations require an admin scope. Internal credentials do not pass through the public gateway. | `SecurityConfig`, service/controller checks |

## Walkthrough: Alice sends Rs 10,000 to Bob

1. Alice submits a JWT, an idempotency key, two account IDs and `amountMinor=1000000`.
2. The gateway validates the token, applies the Redis request limit and forwards the request to payment.
3. Payment inserts the durable request and its acceptance event. It returns 202 before funds are settled.
4. A worker verifies the source profile ownership and asks the ledger to reserve funds using the payment UUID.
5. Ledger locks both wallets in a consistent order. Alice must have at least 1,000,000 available paise. Her reserved amount rises by that amount; neither posted balance changes yet.
6. Fraud persists a repeatable decision for that payment. The default demo rule allows transfers up to Rs 20,000.
7. If allowed, the worker requests settlement using the same payment UUID. Ledger atomically subtracts 1,000,000 from Alice, adds it to Bob, removes Alice's hold, writes the balanced journal and adds its outbox event.
8. Payment marks `COMPLETED` only after the ledger confirms `POSTED`. If that confirmation is lost, a retry returns the existing ledger result without a second debit.
9. The relays send committed events to Kafka. The notification/analytics consumer writes a deduplicated local notification record and updates a terminal-state count.
10. If fraud declines, the worker releases the reservation and records `REJECTED`. No transfer journal is posted.

Notification delivery is a durable database sink. The project does not send emails, SMS, push notifications, or call external providers. Adding a real provider requires its own delivery idempotency/retry contract.

## Failure behavior

| Situation | Intended response |
| --- | --- |
| Same request submitted twice | Same payment ID; input mismatch is 409. |
| Two withdrawals race | Database locks serialize changes; available funds include existing holds. |
| Payment service crashes after ledger reserve | Persisted saga is retried; reserve is idempotent under the same UUID. |
| Database transaction fails before commit | All that transaction's balance, journal, operation and outbox changes roll back. |
| Ledger commits but its response times out | Payment keeps the step pending and retries the same operation; it does not release funds based on a timeout. |
| Kafka is unavailable | Payment and ledger commits can proceed; outbox delivery catches up after recovery. |
| Consumer crashes after DB commit but before offset commit | Redelivery is deduplicated by the inbox. |
| Poisoned consumer record | Retry policy then DLT; replay only after investigating the cause. |
| Persistent dependency failure | Manual review with the exact previous step retained; reservations may remain held. |
| Redis is unavailable | Gateway returns 503; money safety does not depend on Redis availability. |

## Resume description

Built a distributed banking transfer simulator using Java 21, Spring Boot, Spring Security, PostgreSQL, Kafka and Redis. Implemented an ACID double-entry ledger with ordered row locking, idempotent APIs, a durable reserve–settle/release saga, and transactional outbox/inbox processing. Added retry and dead-letter handling, circuit breakers, reconciliation APIs, OpenTelemetry instrumentation, Prometheus/Grafana dashboards, Docker Compose, and Kubernetes/AWS deployment assets.

Use this after you can run and explain the implementation. Do not add throughput numbers, uptime claims, production deployments, or “zero data loss” guarantees without measurements and evidence. `VALIDATION.md` distinguishes executed checks from provided integration tests.

## Boundaries and next extensions

The implementation is intentionally limited to internal, same-currency transfers between seeded accounts. It does not implement KYC/AML, sanctions screening, interest/fees, external payment rails, multicurrency accounting, chargebacks, fraud ML, HA infrastructure, automatic disaster recovery or a public customer UI. The threshold fraud rule is deterministic demonstration logic.

Next meaningful engineering work: run the complete Docker suite; execute the failure lab; add a separate runtime database role; add asymmetric OIDC and service TLS; link persisted trace context across asynchronous boundaries; implement auditable reversal transactions; then load-test hot accounts and record p95/p99 latency, throughput, lock waits and recovery time. Each performance claim should specify hardware, concurrency, data distribution and test duration.

## Primary references

The design follows documented database and framework behavior; it does not claim these sources validate this project's implementation.

- [Spring Boot system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [PostgreSQL explicit locking](https://www.postgresql.org/docs/current/explicit-locking.html)
- [Spring Kafka error handling and dead-letter recovery](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html)
