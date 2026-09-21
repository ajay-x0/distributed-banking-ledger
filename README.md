# Distributed Banking Ledger & Payment System

**Java 21 | Spring Boot | Microservices | PostgreSQL | Apache Kafka | Redis | Docker**

A resource-optimized implementation of a distributed banking ledger and payment processing system, designed to demonstrate reliable financial transactions, event-driven microservices, and distributed systems concepts on a Windows laptop with 8 GB RAM.

The Lite configuration retains six backend services, PostgreSQL, Apache Kafka, and Redis, along with core banking functionality such as account management, payment processing, double-entry bookkeeping, fraud validation, transaction authorization, and distributed payment orchestration using the Saga pattern.

The project is backend-focused and exposes REST APIs through an API Gateway. It does not currently include a graphical frontend. Banking operations can be performed through HTTP clients such as Postman, PowerShell, or automated API tests.

## 1. Project Overview

The Distributed Banking Ledger and Payment System simulates the backend of a digital banking platform.

It demonstrates how independent microservices coordinate to process payments while maintaining transaction consistency, preventing duplicate requests, recording financial activity, and recovering from certain failure scenarios.

### Core capabilities

* REST-based account management and payment processing.
* Distributed payment orchestration using the Saga pattern.
* Double-entry ledger for recording financial transactions.
* PostgreSQL-backed transactional persistence.
* Apache Kafka for asynchronous event-driven communication.
* Redis for supporting distributed application operations.
* Idempotency mechanisms to prevent duplicate payment processing.
* Authentication, authorization, and account ownership validation.
* Fraud validation and compensating transaction workflows.
* Automated smoke tests and service health checks.

**Scope:** This is an educational backend system using simulated accounts and funds. It does not connect to real banks or payment networks.

## 2. System Architecture

The application follows a microservices architecture consisting of six backend services and three infrastructure components.

### Backend services

| Component            | Responsibility                                                                                     |
| -------------------- | -------------------------------------------------------------------------------------------------- |
| API Gateway          | Provides the entry point for client requests and routes supported API operations.                  |
| Payment Service      | Coordinates payment processing, transaction state, idempotency, and distributed payment workflows. |
| Account Service      | Manages account-related operations, balances, and account-level transaction controls.              |
| Ledger Service       | Records financial activity using double-entry bookkeeping and maintains transaction records.       |
| Fraud Service        | Performs fraud validation and supports transaction approval or rejection decisions.                |
| Notification Service | Processes payment-related events and supports asynchronous notification workflows.                 |

### Infrastructure components

| Technology     | Purpose                                                                         |
| -------------- | ------------------------------------------------------------------------------- |
| PostgreSQL     | Persistent storage for account, payment, ledger, and service-related data.      |
| Apache Kafka   | Asynchronous messaging and event-driven communication between services.         |
| Redis          | Supports low-latency operations and the distributed application infrastructure. |
| Docker Compose | Builds, configures, starts, and manages the containerized application stack.    |

### Payment processing workflow

A typical payment moves through the following logical stages:

1. A client submits an authenticated payment request through the API Gateway.
2. The Payment Service validates the request and coordinates the payment workflow.
3. Account ownership, account state, available funds, and fraud conditions are checked as required.
4. The relevant account and ledger operations execute through the distributed payment workflow.
5. Transaction records are persisted, and payment-related events are published for asynchronous processing.
6. The payment reaches a terminal state after the required operations complete or the applicable compensation workflow finishes.

The system uses the Saga pattern to coordinate distributed operations across services. Local database transactions, durable workflow state, and compensating actions help maintain consistency when an operation fails.

**Important:** A distributed Saga is not equivalent to a single ACID transaction spanning every microservice. Each service maintains its own transactional boundaries, while the workflow coordinates consistency across services.

## 3. Lite Configuration and Resource Requirements

The Lite variant was created to reduce the memory requirements of running multiple Java microservices and supporting infrastructure on an 8 GB Windows laptop.

The application was smoke-tested on a Windows system with 8 GB installed RAM and approximately 3.49 GiB of memory reported to Docker.

During that validation, all nine base containers became healthy, and their combined measured memory consumption was approximately 2.25 GiB.

These figures represent a specific local test, not sustained-load performance or a guarantee that the application will run successfully on every 8 GB machine.

The configured base-container memory limits total 3,248 MiB (approximately 3.17 GiB). This excludes Windows, Docker Desktop, WSL overhead, and other applications.

For the initial build, close memory-intensive applications such as IntelliJ IDEA.

### Resource optimizations

| Component                 | Lite configuration                                                                    |
| ------------------------- | ------------------------------------------------------------------------------------- |
| Java microservices        | Six services, each with a 384 MiB container limit                                     |
| JVM heap                  | 32 MiB initial heap and 128 MiB maximum heap per Java service                         |
| JVM tuning                | Serial GC, reduced code cache and direct-memory budgets, bounded processor count      |
| Tomcat                    | 12 worker threads, 1 spare thread, 64 connections                                     |
| Database connection pools | Maximum 3 connections per service, with 0 minimum idle connections                    |
| PostgreSQL                | 256 MiB container limit, 32 MiB shared buffers, 30 connections                        |
| Apache Kafka              | 640 MiB container limit, 128–256 MiB broker heap, reduced worker-thread configuration |
| Redis                     | 48 MiB container limit, 16 MiB data limit, no eviction                                |
| Build container           | 768 MiB limit, 384 MiB Maven heap, sequential module builds                           |
| Startup                   | Sequential container startup with readiness checks                                    |
| Observability             | Trace export and Prometheus export disabled; no dashboard or collector containers     |

These optimizations reduce memory consumption but may also limit throughput and increase latency under load.

The Lite variant is intended for local development, functional testing, and learning rather than production deployment or high-volume performance testing.

## 4. Technology Stack

**Backend:** Java 21, Spring Boot, Spring Security, Spring Data JPA, REST APIs

**Databases and caching:** PostgreSQL, Redis

**Messaging:** Apache Kafka

**Build and deployment:** Maven, Docker, Docker Compose, Python 3

**Testing and automation:** Automated API smoke tests, Java integration tests, service health checks, GitHub Actions

**Engineering concepts:** Microservices, Saga pattern, idempotency, double-entry bookkeeping, database transactions, event-driven architecture, authorization, distributed consistency, and failure recovery.

## 5. Prerequisites

To run the Lite version on Windows, install:

* Docker Desktop with Linux containers and WSL 2 support.
* Python 3.
* Git, if cloning the project directly from GitHub.

A local JDK or Maven installation is not required for the documented Docker-based build workflow. The dedicated builder image includes Java 21 and Maven.

Ensure Docker Desktop is running before starting the application.

## 6. Installation and Startup

Clone the repository:

```bash
git clone https://github.com/ajay-x0/distributed-banking-ledger.git
```

Navigate to the project directory:

```bash
cd distributed-banking-ledger
```

Open PowerShell in the project directory.

Check Docker availability and its reported memory:

```bash
py scripts/lite.py check
```

Build and start the application:

```bash
py scripts/lite.py start
```

Alternatively, use the provided Windows launcher:

```powershell
.\start-lite.bat
```

If Python is installed under the `python` command instead of `py`, substitute that command.

On Linux or macOS:

```bash
python3 scripts/lite.py start
```

### What happens during startup?

The Lite launcher performs the following operations:

1. Creates the local environment configuration.
2. Validates the Docker Compose configuration.
3. Stops existing containers belonging to the Lite project.
4. Prepares the Kafka data volume for the official image's non-root user.
5. Builds the Java modules sequentially using a memory-limited builder container.
6. Packages the six Java runtime images.
7. Starts the nine base containers sequentially.
8. Waits for the configured health or readiness checks before proceeding.

Existing data volumes are preserved during normal startup.

The first build may require downloading Docker images, Java dependencies, and Maven artifacts. Maven dependencies are cached for subsequent builds.

Do not use `docker compose up --build` for the initial build. The Lite workflow uses a dedicated builder to generate the JAR files required by the runtime images.

**Note:** The Lite configuration uses port 8080, which is also used by the original full project. Stop the full project before starting the Lite stack.

## 7. Verify the Running System

Check the current container status:

```bash
py scripts/lite.py status
```

Run the automated end-to-end smoke test:

```bash
py scripts/lite.py test
```

The expected successful smoke-test summary is:

```text
PASS: transfer, duplicate, conflict, authorization,
fraud compensation, reconciliation, Kafka projection
```

### What does the smoke test verify?

The smoke test executes predefined checks against the running banking system.

These checks cover the following scenarios:

| Test scenario      | Purpose                                                                            |
| ------------------ | ---------------------------------------------------------------------------------- |
| Transfer           | Verifies the tested payment transfer workflow.                                     |
| Duplicate          | Checks idempotent handling of repeated payment requests.                           |
| Conflict           | Checks the response when an idempotency key is reused with different request data. |
| Authorization      | Checks selected authentication or authorization restrictions.                      |
| Fraud compensation | Tests the configured fraud-related compensation scenario.                          |
| Reconciliation     | Checks consistency between the tested financial records.                           |
| Kafka projection   | Verifies the tested asynchronous event-processing workflow.                        |

A successful result means that the specific assertions implemented in the smoke test passed.

It does not imply that every possible payment scenario, failure condition, concurrency issue, or production workload has been tested.

For hands-on verification, individual banking operations can also be performed manually using the REST APIs.

### Check resource consumption

```bash
py scripts/lite.py stats
```

This command displays container memory usage, CPU utilization, and process counts.

Check Windows Task Manager as well, because container-level memory measurements do not include the complete memory consumption of Windows, Docker Desktop, WSL, and other applications.

## 8. Manually Execute a Banking Transaction

The application exposes its backend API through:

```text
http://localhost:8080
```

This address is an API entry point, not a web application.

There is currently no graphical frontend or public signup endpoint.

### Demo accounts

The system includes three seeded accounts with simulated balances.

| Account | Initial balance | Account ID suffix |
| ------- | --------------: | ----------------- |
| alice   |        ₹100,000 | 0001              |
| bob     |        ₹100,000 | 0002              |
| charlie |        ₹100,000 | 0003              |

Payment amounts are represented in minor currency units.

For INR:

```text
₹1 = 100 paise

₹10,000 = 1,000,000 paise
```

The default demonstration fraud rule allows transfers up to ₹20,000.

### Example: Transfer ₹10,000 from Alice to Bob

The following PowerShell example demonstrates how to submit a payment request using the existing backend APIs.

Generate a demonstration authentication token:

```powershell
$token = py scripts/token.py alice
```

Prepare the HTTP request headers:

```powershell
$headers = @{
    Authorization = "Bearer $token"
    "Idempotency-Key" = [guid]::NewGuid().ToString()
}
```

Prepare the payment request:

```powershell
$body = @{
    source = "00000000-0000-0000-0000-000000000001"
    destination = "00000000-0000-0000-0000-000000000002"
    amountMinor = 1000000
    currency = "INR"
} | ConvertTo-Json
```

Submit the payment:

```powershell
$payment = Invoke-RestMethod `
    -Uri "http://localhost:8080/api/payments" `
    -Method Post `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $body

$payment
```

Retrieve the payment status:

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8080/api/payments/$($payment.id)" `
    -Headers @{ Authorization = "Bearer $token" }
```

Because the application uses distributed and asynchronous processing, the initial payment response may not represent the final transaction state.

Repeat the payment status request as needed to inspect the completed workflow.

### Idempotency verification

The payment API uses an idempotency key to identify repeated requests.

* Repeating the same payment request with the same idempotency key returns the existing payment instead of creating another payment.
* Reusing the same key with different payment data returns an HTTP 409 Conflict response.

This mechanism helps protect the system against duplicate payment processing caused by repeated client requests.

**Note:** All funds are simulated. The smoke tests can modify the seeded account balances, so repeated test execution may require restoring funds or starting with a fresh demonstration environment.

## 9. Observability and Monitoring

The original distributed banking architecture includes observability concepts involving OpenTelemetry, Prometheus, and Grafana.

These technologies serve different purposes:

| Technology    | Purpose                                                                     |
| ------------- | --------------------------------------------------------------------------- |
| OpenTelemetry | Provides application instrumentation and telemetry collection capabilities. |
| Prometheus    | Collects and stores time-series metrics from instrumented services.         |
| Grafana       | Visualizes collected metrics through monitoring dashboards.                 |

In a larger deployment, monitoring can help identify service failures, API latency, increased error rates, JVM memory pressure, and other operational problems.

### Monitoring in the Lite configuration

To reduce memory consumption, the Lite configuration disables trace export and Prometheus export and does not start dashboard or telemetry collector containers.

Grafana dashboards are therefore not part of the documented Lite startup or validation workflow.

The Lite configuration instead relies on Docker health checks, container statistics, application logs, and automated smoke tests for local operational verification.

## 10. Troubleshooting and Diagnostics

If startup fails or a service repeatedly restarts, inspect the running containers:

```bash
py scripts/lite.py status
```

Generate a diagnostic report:

```bash
py scripts/lite.py report
```

Inspect service logs:

```bash
py scripts/lite.py logs ledger
```

To inspect another service, replace `ledger` with the appropriate service name.

The diagnostic report is saved as:

```text
lite-diagnostics.txt
```

It contains container status, restart counts, out-of-memory indicators, and resource measurements.

The report does not intentionally dump environment credentials and is excluded from Git through `.gitignore`.

### Common troubleshooting areas

**Insufficient Docker memory:** Close unnecessary applications, check available system memory, and review Docker's reported resource limits.

**Port 8080 already in use:** Stop the original banking project or another application using the same port.

**Kafka startup or permission failure:** Inspect Kafka logs and the ownership of its persistent data volume. The Lite launcher includes volume-permission preparation for the official Kafka image.

**Service readiness failure:** Inspect the affected service's logs and dependency health before attempting another startup.

## 11. Daily Development Commands

| Action                                          | Command                                 |
| ----------------------------------------------- | --------------------------------------- |
| Check Docker configuration and available memory | `py scripts/lite.py check`              |
| Build or rebuild and start the application      | `py scripts/lite.py start`              |
| Start using existing images                     | `py scripts/lite.py start --skip-build` |
| Run automated smoke tests                       | `py scripts/lite.py test`               |
| View container resource consumption             | `py scripts/lite.py stats`              |
| Check container status                          | `py scripts/lite.py status`             |
| Generate a diagnostic report                    | `py scripts/lite.py report`             |
| Inspect payment service logs                    | `py scripts/lite.py logs payment`       |
| Stop the application while retaining data       | `py scripts/lite.py stop`               |

The `start` command intentionally stops the Lite stack before rebuilding so that compilation does not compete with the running services for memory.

Use `--skip-build` only when the required images already exist and the application code has not changed.

Stopping the stack preserves its persistent data volumes during normal operation.

## 12. Data Integrity and Reliability

The Lite configuration reduces resource consumption without intentionally removing the project's core transaction-safety mechanisms.

The following PostgreSQL durability settings remain enabled:

* `fsync`
* `synchronous_commit`
* `full_page_writes`

Kafka retains the configured producer acknowledgment and idempotence mechanisms, including `acks=all`.

The banking workflow retains its account balance locking, ledger constraints, authorization checks, outbox/inbox processing, and Saga state transitions.

These mechanisms address different reliability concerns, including concurrent balance modifications, duplicate requests, distributed transaction coordination, and failure recovery.

However, the local deployment uses single-node infrastructure and does not provide production-grade high availability.

The system is intended for learning, experimentation, and functional validation using simulated banking data.

## 13. Project Documentation

Additional technical documentation is available in the repository.

| File                          | Description                                                                          |
| ----------------------------- | ------------------------------------------------------------------------------------ |
| `docs/LIGHTWEIGHT-CHANGES.md` | Resource optimizations and differences between the Lite and original configurations. |
| `docs/VALIDATION.md`          | Validation procedures, completed checks, and remaining verification requirements.    |
| `docs/PROJECT-DESCRIPTION.md` | System architecture, engineering concepts, and concept-to-code mapping.              |
| `docs/API.md`                 | Internal service contracts and API documentation.                                    |
| `docs/FAILURE-LAB.md`         | Failure scenarios, outage simulation, and recovery exercises.                        |
| `.github/workflows/ci.yml`    | Automated CI workflow covering Java tests, Lite startup, and smoke testing.          |

The Lite configuration isolates most resource-related changes within its dedicated application profile, Docker Compose configuration, Dockerfiles, and launcher/build scripts.

Application-level business logic is retained from the original system.

## 14. CI/CD

The repository includes a GitHub Actions workflow for automated validation.

The workflow is configured to run Java tests, start the Lite environment, and execute the banking smoke test.

The CI pipeline helps detect build failures, application startup problems, and regressions covered by the implemented tests.

A successful workflow indicates that the configured checks passed for that particular run. It does not replace production performance testing, security assessment, or comprehensive failure testing.

## 15. Repository and Development Notes

The Lite configuration uses the Docker Compose project name:

```text
banking-ledger-lite
```

Its containers and persistent volumes are separate from those of the original `banking-ledger` project.

Both configurations use local port 8080 and should not be started simultaneously without changing their port configuration.

The Lite launcher does not migrate data from the original project's database or stop unrelated Docker projects.

When publishing the repository, exclude local environment files, generated build artifacts, compiled JARs, and diagnostic reports.

The repository's `.gitignore` covers these generated files.

The source code archive is relatively small, but the first build requires additional disk space for Docker images, Maven dependencies, and supporting infrastructure.

## 16. Project Scope and Future Enhancements

The current implementation focuses on backend engineering, distributed payment processing, transaction consistency, and resource-efficient local deployment.

Potential future enhancements include a React-based banking dashboard, browser-based account and transaction management, expanded automated test coverage, and a dedicated observability deployment using Prometheus and Grafana.

Additional load testing, resilience testing, and deployment hardening would be required before considering any production-style use.

---

**Disclaimer:** This project is intended for educational and demonstration purposes. All accounts, balances, and transactions use simulated funds. It is not a production banking platform and does not process real financial transactions.
