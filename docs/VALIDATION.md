# Lightweight validation report

## Executed for this variant

- Nine Python launcher workflow tests passed. They use mocked Docker commands and verify project isolation, stop-before-build ordering, build-before-start ordering, sequential readiness gates, skip-build behavior, failure handling, Linux-container validation, scoped statistics, limited diagnostic output, and automatic Kafka volume ownership preparation.
- The Compose YAML and its resource settings were checked structurally with Python. The Compose binary download was incomplete, so native `docker compose config` validation was not completed here.
- Bash syntax checks passed for the build script, PostgreSQL initializer and application health probe.
- The health probe was executed against a local test HTTP server: status 200 returned success; status 503 returned failure. This is not a Spring application startup test.
- Python, YAML, JSON and Maven XML parsing and resource/configuration invariants were checked.
- Java business source and database migrations were compared with the original archive to confirm they were unchanged.

## Executed on the target Windows laptop

On 19 September 2026, the owner ran the lite stack on Windows with 8 GB installed RAM and Docker Desktop using WSL 2. Docker reported 3.49 GiB available to its VM.

- PostgreSQL, Redis, Kafka, account, ledger, fraud, notification, payment, and gateway all reached healthy status.
- The live smoke test passed: transfer, duplicate request, idempotency conflict, authorization, fraud compensation, reconciliation, and Kafka projection.
- `docker stats` reported approximately 2.25 GiB total container memory after the test.
- Individual readings were approximately: account 315.5 MiB, fraud 275.8 MiB, gateway 307.9 MiB, Kafka 369.1 MiB, ledger 341.6 MiB, notification 300.3 MiB, payment 309.3 MiB, PostgreSQL 73.6 MiB, and Redis 10.2 MiB.
- The initial Kafka start exposed a named-volume ownership error. The launcher now performs the required ownership preparation automatically before startup, and its workflow test verifies that command.

## Remaining validation limits

- Java 21 compilation and the newly added `LiteProfileTest`: Java 21/Maven downloads could not be completed in this session.
- The real PostgreSQL Testcontainers integration suite has not been reported from the target laptop.
- Sustained load, latency percentiles, throughput, long-running stability, forced process-crash recovery, disk exhaustion, and restart-after-host-reboot tests have not been measured on the 8 GB laptop.

The 3.17 GiB figure remains a sum of configured container limits. The separate 2.25 GiB reading is a single post-smoke-test measurement and should not be presented as a peak or production capacity result.

## Baseline evidence

`BASELINE-VALIDATION.md` and `baseline-test-evidence.txt` preserve the original project's build/test report. Those earlier results do not validate the lite profile or its resource settings. Business Java and database schemas were retained, but the operating environment has changed.

## Reproduce

Local workflow tests (Python standard library only):

```powershell
py -m unittest discover -s scripts/tests -v
```

On your laptop, with Docker Desktop running:

```powershell
py scripts/lite.py start
py scripts/lite.py test
py scripts/lite.py stats
py scripts/lite.py report
```

On a Java 21/Maven machine with Docker, or through the included GitHub Actions workflow:

```bash
mvn -B -ntp verify
```

The existing PostgreSQL integration tests require Docker and do not silently skip. The added `LiteProfileTest` verifies that the profile overrides imported defaults while retaining producer acknowledgments, idempotence, manual consumer acknowledgment and schema validation.
