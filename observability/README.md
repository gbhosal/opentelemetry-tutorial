# Local Grafana backends

Docker Compose stack for the OpenTelemetry demo:

| Service | Role | Host URL |
| --- | --- | --- |
| Grafana | Explore metrics, logs, and traces | http://localhost:3000 |
| Mimir | Metrics store (Prometheus-compatible) | http://localhost:9009 |
| Loki | Log store, including native OTLP | http://localhost:3100 |
| Tempo | Trace store | http://localhost:3200 |

Grafana opens without a login prompt. The local admin user is `admin` / `admin` if you need the login form. Storage is on the container filesystem, so `docker compose down` discards ingested data. This layout is for a laptop demo, not a production deployment.

## Start from WSL

Use a WSL distro that has Docker, or Docker Desktop with the WSL integration enabled for that distro.

```bash
cd /mnt/c/Users/gbhosal/git/opentelemetry-tutorial/observability
docker compose up -d
docker compose ps
```

The same `docker compose up -d` command works from PowerShell in this folder when Docker Desktop is running.

## Check that the backends are ready

```bash
curl -sf http://localhost:3000/api/health && echo
curl -sf http://localhost:3100/ready && echo
curl -sf http://localhost:3200/ready && echo
curl -sf http://localhost:9009/ready && echo
```

Each command should print a short ready response. Loki, Tempo, and Mimir can take half a minute on the first start.

In Grafana, open **Connections → Data sources**. Tempo, Loki, and Mimir should already be listed. Explore will stay empty until an application sends telemetry.

## Demo applications

Order, Payment, PostgreSQL, and the OpenTelemetry Collector are in `../services`. Start this stack first so the `observability` network exists, then follow `services/README.md`.

The Collector exports to:

| Signal | Endpoint inside Compose |
| --- | --- |
| Traces | `tempo:4317` (OTLP gRPC) |
| Logs | `http://loki:3100/otlp` |
| Metrics | `http://mimir:9009/otlp` |

Tempo also writes span metrics and service-graph metrics to Mimir, which is what Grafana uses for the service map and the trace-to-metrics links.

## Stop

```bash
docker compose stop    # keep ingested data
docker compose down    # remove containers and ingested data
```
