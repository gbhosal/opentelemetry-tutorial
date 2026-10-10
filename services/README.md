# Order and Payment demo

Two Java 17 Spring Boot services, PostgreSQL, and an OpenTelemetry Collector. The Java agent is attached in each image's `java` command. Neither application depends on OpenTelemetry.

Start the Grafana stack in `../observability` first. It creates the Docker network this file joins.

```bash
cd /mnt/c/Users/gbhosal/git/opentelemetry-tutorial/observability
docker compose up -d

cd /mnt/c/Users/gbhosal/git/opentelemetry-tutorial/services
docker compose up -d --build
```

| Service | URL |
| --- | --- |
| Order | http://localhost:8081 |
| Payment | http://localhost:8082 |
| PostgreSQL | `localhost:5432`, user `demo`, password `demo`, databases `orderdb` and `paymentdb` |
| Collector OTLP | `localhost:4317` (gRPC), `localhost:4318` (HTTP) |

Place a batch of orders:

```bash
bash place-orders.sh 20
```

About 7 in 10 payments approve in under 100 ms, about 2 in 10 approve after 500–2000 ms, and about 1 in 10 is declined. A declined payment is stored as `DECLINED` and returned as HTTP 500 so the Java agent marks that span as an error. Order then stores `FAILED` and returns HTTP 502.

In Grafana at http://localhost:3000, open Explore:

- Tempo: `{ resource.service.name = "order-service" && name = "POST /orders" }`
- Loki: `{service_name="payment-service"}`
- Mimir: `sum by (job, http_route, http_response_status_code) (http_server_request_duration_count)`

A paid order trace contains the order HTTP span, an `INSERT` into `orders`, the HTTP call to Payment, Payment's `INSERT`, and an `UPDATE` of the order. The slow approvals show the extra time on the Payment server span, before its database span. Use the trace's logs link to open the matching `delayMs` line in Loki.

```bash
docker compose stop
docker compose down    # also drops the Postgres volume when used with -v
```
