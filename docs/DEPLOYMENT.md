> Lite image-build change: run `py scripts/lite.py start` to build JARs before packaging runtime images. The Kubernetes/AWS assets below remain reference assets for larger environments; they are not tuned to the lite budget. The observability services described below are in the original full archive, not the lite Compose configuration.

# Kubernetes and AWS deployment assets

No AWS resources are created by the local demo. The supplied Terraform provisions six ECR repositories only. Kubernetes templates deploy the applications onto an existing cluster with external PostgreSQL, Redis, Kafka and telemetry endpoints.

## Local image build

```bash
docker build --build-arg SERVICE=payment -t bank/payment-service:1.0.0 .
```

Repeat for gateway, account, ledger, fraud and notification. The image uses Java 21 and a non-root runtime user. Each build compiles the selected service and its shared module.

## AWS reference deployment

| Component | Suggested AWS target | Included here |
| --- | --- | --- |
| Container images | ECR | Terraform repositories with immutable tags and scan-on-push |
| Service replicas | EKS | Kubernetes Deployment and Service templates |
| Service databases | RDS PostgreSQL | SQL migrations; cluster/database provisioning remains operator work |
| Rate limiter | ElastiCache | Redis endpoint configuration; managed authentication/TLS needs explicit configuration |
| Events | MSK | Bootstrap configuration; managed TLS/SASL/IAM settings need explicit configuration |
| Edge | ALB with HTTPS | Not provisioned; only private ClusterIP gateway included |
| Credentials | Secrets Manager / external secrets | Secret names in manifests; controller integration remains operator work |
| Traces/metrics | Managed collectors or self-hosted stack | Local collector, Grafana and Prometheus configuration |

The default Compose connection settings are private-network plaintext settings. Do not assume an arbitrary MSK or ElastiCache deployment will accept them. Configure Spring Kafka `security.protocol`, SASL/SSL, Redis SSL/auth and JDBC TLS according to the chosen managed deployment before use. No banking-grade compliance claim follows from using AWS.

## Provision image repositories

From `infra/aws`, review `terraform plan` before applying. Terraform uses your configured AWS credentials and creates chargeable/account-owned resources only when you run it. Pin the generated provider lockfile for your team. Do not commit credentials, tfvars containing secrets or local state.

Build, tag and push each image to the corresponding ECR URL returned by Terraform using an immutable release tag. Authenticate Docker with the AWS CLI under your own identity; no credentials are embedded in this repository.

## Prepare application dependencies

1. Create an existing EKS or other Kubernetes cluster with connectivity to the dependencies.
2. Create six PostgreSQL databases and users: `bank_gateway`, `bank_payment`, `bank_account`, `bank_ledger`, `bank_fraud`, `bank_notification`. For production, use separate migration and runtime roles; the demo startup runs Flyway with the runtime connection.
3. Create `bank.events` and `bank.events.DLT` with matching partition counts. The local notification service declares 3 partitions and replication factor 1. For a replicated broker cluster, pre-create topics with the desired replication factor and disable app topic creation with `SPRING_KAFKA_ADMIN_AUTO_CREATE=false`. Configure `min.insync.replicas` and retention deliberately.
4. Create namespace `bank` using `infra/k8s/namespace.yml`.
5. Create a Secret named `<service>-secrets` for each service. Required keys: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `SERVICE_PASSWORD`. Provide a service-specific JDBC database URL. Add deployment-specific Redis/Kafka/TLS credentials there. Never commit the Secret values.
6. Set `AWS_ACCOUNT_ID`, `AWS_REGION`, `IMAGE_TAG`, `REDIS_HOST`, `KAFKA_BOOTSTRAP_SERVERS`, and `OTEL_ENDPOINT` in the rendering environment, then run `python3 infra/k8s/render.py`.
7. Review `infra/k8s/rendered/`, then apply it with kubectl. The application should become ready only after its database is available.
8. Use `kubectl -n bank port-forward svc/gateway 8080:8080` for private testing. Run the smoke check with demo tokens only in a demo environment. An external ingress is intentionally not created.

Kubernetes assets include startup/readiness/liveness probes, graceful termination, memory/CPU limits, read-only root filesystems, dropped capabilities and non-root UIDs. They do not include a NetworkPolicy, autoscaling, TLS termination, node provisioning, backups, deployment rollback orchestration or a disaster-recovery design. Set those up explicitly before allowing real users or real data.

## Before claiming scale

Measure contention on hot wallets, saga worker connection occupancy, relay backlog, consumer lag, p95/p99 latency and recovery time. Default polling and database-row locks prioritize clarity and correctness over peak throughput. No TPS or latency benchmark is supplied.
