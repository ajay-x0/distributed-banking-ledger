# Validation report

Delivery validation performed on 11 September 2026.

## Executed successfully

- Java 21 clean build and executable JAR packaging for all six Spring Boot services and the shared module.
- All integration-test source files compiled with Java 21; integration tests were not executed.
- 18 selected unit/component tests passed, with zero failures/errors/skips: MoneyRulesTest (5), JwtValidationTest (5), OutboxRelayTest (2), PaymentWorkerTest (6).
- MoneyRulesTest includes 10,000 deterministic randomized money-conservation examples within one test.
- 12 SQL checks passed using PGlite, a WebAssembly PostgreSQL runtime: common and service migrations for five business services, seed totals, acceptance of balanced journals, rejection of empty/unbalanced journals, journal update/delete rejection, and reservation bounds.
- Docker Compose v2.35.1 `config --quiet` passed using generated test values, without requiring a Docker daemon.
- YAML, JSON, Maven XML and Python files parsed successfully; PostgreSQL initializer passed Bash syntax checking.

The build was first checked with a Java 17 compatibility override, then clean-built and tested using Java 21 without overriding the project's Java version. Mockito uses its supported subclass mock maker for these tests, avoiding runtime agent attachment. That setting changes test instrumentation, not application behavior.

The SQL checks are useful validation of migrations and database constraints. PGlite is not evidence of concurrent locking behavior, PostgreSQL network operation, production durability or a full application deployment. The temporary PGlite tooling is not a project runtime dependency.

## Not executed here

- Docker-based Testcontainers integration suite, including concurrent database transactions.
- Docker image builds and Compose end-to-end smoke test.
- Kafka broker/consumer/DLT behavior against a running broker.
- Kubernetes application startup or AWS Terraform deployment.
- Sustained load, throughput, latency benchmarks or real process-kill recovery drills.

This workspace had no Docker daemon or PostgreSQL server. A successful Java compilation cannot replace these integration checks. GitHub Actions is configured to run `mvn verify`, Compose startup and the smoke test on a Docker-enabled runner; no remote CI run is claimed.

## Reproduce

Fast tests:

```bash
mvn -B -ntp -Dtest=MoneyRulesTest,JwtValidationTest,OutboxRelayTest,PaymentWorkerTest -Dsurefire.failIfNoSpecifiedTests=false verify
```

Full integration gate on a machine with Java 21, Maven and Docker:

```bash
mvn -B -ntp verify
python3 scripts/init_env.py
docker compose config --quiet
docker compose up -d --build
python3 scripts/smoke.py --wait 240
```

Review and fix any integration failure before presenting the system as end-to-end verified. The full test suite does not silently skip missing Docker.
