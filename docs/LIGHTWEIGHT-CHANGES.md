# Lightweight variant: decisions and limits

This variant targets a personal 8 GB Windows machine by reducing concurrency and memory budgets, while keeping all six services and the same business behavior. It is not a rewrite into a monolith and does not replace Kafka or PostgreSQL with mocks.

## What changed

1. The Spring `lite` profile reduces Hikari's maximum pool size from 10 to 3, sets minimum idle to zero, limits HTTP threads, reduces Kafka producer/consumer buffers, and disables tracing/Prometheus export.
2. Both scheduled payment work and the outbox relay retain separate scheduler capacity (pool size 2), so a slow broker relay does not occupy the only scheduler thread needed by the payment worker.
3. The JVM gets an explicit 128 MiB heap rather than a percentage of a large container limit. Serial GC is suitable for trying small heaps with few CPUs; it is not claimed to outperform G1 for larger workloads.
4. Kafka's broker heap and its health-check CLI heap are configured separately. Using only a broker heap setting can leave expensive probe JVMs consuming additional memory.
5. PostgreSQL keeps durable commit settings. Its lower connection/shared-buffer budget fits the reduced application pools. Redis still persists its data, and a full rate-limit cache fails requests rather than evicting counters to weaken throttling.
6. A builder container compiles the entire Maven reactor serially. It stops before application startup. Its 768 MiB limit and 384 MiB Maven heap are separate from the steady-state 3,312 MiB sum of base-container limits after the UI was added.
7. Runtime Docker image builds only copy already-built JARs. The launcher packages one image at a time and waits for each application's readiness endpoint during startup.
8. Application readiness uses a small Bash HTTP check instead of another Java process. Docker terminates a stuck probe at its configured timeout.
9. The Python launcher works with Windows `py`, Windows `python`, or Linux/macOS `python3`; subprocesses reuse the active interpreter. The optional batch file is a shortcut.
10. A named Compose project isolates lite volumes and containers. Nothing in the launcher removes volumes, prunes Docker data or stops another project.

## What was kept

Payment and ledger Java business implementations, schemas, journal immutability, double-entry constraints, idempotency keys, ownership checks, reserve/settle/release saga, exponential retry, circuit breakers, fraud decision persistence, Kafka dead-letter handling, outbox/inbox deduplication and reconciliation are unchanged.

Local observability is deliberately off. The old Grafana/Prometheus/OpenTelemetry reference files remain in `infra/observability`, but there is no observability profile in the lite Compose file. The full original archive contains the runnable dashboard configuration. Lite health endpoints and diagnostic commands remain available.

AWS/Kubernetes templates are retained as reference assets. They are not the laptop startup path and are not tuned to this memory budget. All measurements and validation claims must identify which variant was used.

## Memory interpretation

6 × 384 + 256 + 640 + 48 + 64 = 3,312 MiB, or approximately 3.23 GiB of base-container memory limits. The final 64 MiB is the Nginx transaction simulator. This arithmetic is not a measurement. The host needs additional memory for Windows, Docker/WSL and tools. The builder runs while this project's other containers are stopped. Image-building infrastructure and filesystem caches add their own overhead.

Do not reduce heaps arbitrarily if startup fails. First inspect `py scripts/lite.py report`, container logs, and Windows Task Manager. A container marked `oom=true` needs either more headroom, less work or a different architecture. An application exception can also be unrelated to memory.

There are no automatic Windows/WSL configuration changes in this project. If Docker reports less than 3.5 GiB for its VM, the launcher explains the risk but leaves machine configuration to the owner. Closing IntelliJ for the first run gives Windows additional room.

## Reading

- [Docker Compose memory limits and health checks](https://docs.docker.com/reference/compose-file/services/)
- [Docker Compose command parallelism](https://docs.docker.com/reference/cli/docker/compose/)
- [Java 21 launcher and JVM options](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html)

These sources document the configuration controls. They do not validate this application's memory consumption.
