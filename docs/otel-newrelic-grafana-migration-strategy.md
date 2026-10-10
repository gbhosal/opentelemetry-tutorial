# OpenTelemetry + New Relic + Grafana Observability Strategy

## Goal

Establish OpenTelemetry (OTel) as the **standard telemetry and instrumentation layer** for distributed tracing, while preserving the existing New Relic APM experience and introducing Grafana-based tracing through Tempo.

The strategy is intentionally incremental:

- **Do not perform a big-bang replacement of New Relic.**
- **Do not require every application team to change observability backends.**
- Introduce an **OpenTelemetry Collector** as the central telemetry routing and processing layer.
- Continue using **New Relic as an APM destination** during the transition.
- Add **Grafana Tempo** as a distributed-tracing backend.
- Reuse the existing **Prometheus + Loki + Grafana** ecosystem.
- Gradually move application instrumentation from New Relic-specific agents toward OpenTelemetry where that proves operationally and functionally equivalent.

The target architectural principle is:

> **Instrument once with OpenTelemetry, collect centrally, and route telemetry to one or more backends without coupling applications to a specific observability vendor.**

---

## 1. Current State

Most Java applications currently run with the New Relic Java agent attached to the JVM:

```text
Java Application
      |
      | -javaagent:newrelic.jar
      v
New Relic APM
```

The existing observability environment also includes Prometheus, Loki, and Grafana.

A simplified representation is:

```text
                     Current

Java / Kubernetes / ECS / EC2 workloads
                  |
        +---------+---------+
        |                   |
      Metrics             Logs
        |                   |
        v                   v
   Prometheus             Loki
        \                   /
         \                 /
              Grafana

Java workloads
      |
      +-- New Relic Java Agent --> New Relic APM
```

The major gap is distributed tracing across services and infrastructure boundaries.

---

# 2. Why OpenTelemetry

OpenTelemetry is not another APM product. It provides a vendor-neutral framework for producing, collecting, processing, and exporting telemetry.

Its three primary signals are:

- **Metrics**
- **Logs**
- **Traces**

For this initiative, distributed tracing is the primary objective.

A trace represents the end-to-end journey of a request or operation. A trace is composed of spans, where each span represents a unit of work performed by a service, database, messaging system, or other component.

Example:

```text
Trace ID: abc123

HTTP request
    |
    v
order-service
    |
    +---- inventory-service
    |          |
    |          +---- PostgreSQL
    |
    +---- payment-service
               |
               +---- MSK
```

This makes it possible to answer questions that are difficult to answer from metrics and logs alone:

- Where did the request spend its time?
- Which downstream service caused the latency?
- Did a Kafka consumer contribute to the delay?
- Which database operation was responsible?
- Which exact request produced an error?

---

# 3. Target Architecture

The recommended target state is:

```text
                                  Grafana
                                     |
                  +------------------+------------------+
                  |                  |                  |
                  v                  v                  v
             Prometheus             Loki               Tempo
               Metrics              Logs              Traces
                  ^                  ^                  ^
                  |                  |                  |
                  +------------------+------------------+
                                     |
                              OTel Gateway
                                 Collectors
                                     ^
                 +-------------------+-------------------+
                 |                   |                   |
                 |                   |                   |
            EKS workloads       ECS workloads       EC2 workloads
                 |                   |                   |
          OTel Collector /     OTel sidecar or      OTel Collector
          node-local agent       agent pattern
                 ^                   ^                   ^
                 |                   |                   |
              Java/Go/etc.       Java/Go/etc.       Java/Go/etc.
```

During migration, New Relic remains another destination:

```text
                         OTel Collector
                              |
              +---------------+---------------+
              |               |               |
              v               v               v
          New Relic          Tempo            Loki
             APM              |                |
                              +-------+--------+
                                      |
                                    Grafana
```

Metrics can continue using the existing Prometheus architecture rather than being moved simply because OTel is introduced.

New Relic explicitly supports native OTLP ingestion and recommends OTLP as the preferred method for sending OpenTelemetry data to New Relic. The OpenTelemetry Collector is also recommended by New Relic as a common place to process and route OTel telemetry. See:

- https://docs.newrelic.com/docs/opentelemetry/best-practices/opentelemetry-otlp/
- https://docs.newrelic.com/docs/opentelemetry/get-started/collector-processing/opentelemetry-collector-processing-intro/

Grafana Tempo supports receiving traces through the OpenTelemetry Collector and describes the Collector as a vendor-neutral component for receiving, processing, and exporting telemetry. See:

- https://grafana.com/docs/tempo/latest/set-up-for-tracing/instrument-send/set-up-collector/

---

# 4. Key Architectural Idea: Instrumentation vs Backend

The most important design decision is to separate **instrumentation** from **telemetry storage and visualization**.

### Application should know about

```text
OpenTelemetry
     |
    OTLP
     |
     v
OTel Collector
```

### Application should not need to know about

```text
Tempo
New Relic
Loki
Prometheus
Grafana
```

The Collector decides where telemetry goes.

This provides backend independence:

```text
                        Application
                             |
                      OpenTelemetry
                             |
                             v
                      OTel Collector
                    /        |        \
                   /         |         \
                  v          v          v
             New Relic     Tempo       Loki
```

This also gives the organization a single place to implement:

- batching
- retry
- authentication
- attribute enrichment
- filtering
- redaction
- sampling
- routing
- rate limiting
- telemetry governance

New Relic explicitly identifies the Collector as a place to enrich telemetry, filter/transform data, and shift telemetry-processing overhead away from applications.

---

# 5. How New Relic Fits In

## 5.1 Recommended transitional model

The recommended migration model is:

```text
                   OpenTelemetry
                        |
                        v
                OTel Collector
                  /          \
                 /            \
                v              v
           New Relic          Tempo
              APM               |
                                v
                             Grafana
```

This means New Relic is retained rather than immediately replaced.

The immediate goal is not:

> Replace New Relic.

The immediate goal is:

> Introduce an open telemetry pipeline while preserving the existing APM investment.

New Relic documents an OpenTelemetry APM UI intended for services instrumented with OpenTelemetry and states that it provides the same core APM-style monitoring experience expected from traditional language agents. See:

https://docs.newrelic.com/docs/opentelemetry/get-started/apm-monitoring/opentelemetry-apm-ui/

New Relic also documents ingestion and mapping of OpenTelemetry spans through its OTLP endpoint:

https://docs.newrelic.com/docs/opentelemetry/best-practices/opentelemetry-best-practices-traces/

---

# 6. What Happens to the Existing New Relic Java Agent?

The existing New Relic Java agent is an important consideration.

It should **not** be removed from hundreds of applications immediately.

There are three broad approaches.

---

## Approach A — Keep the New Relic Java Agent Everywhere

```text
Java process
     |
     +-- New Relic Java Agent
              |
              v
         New Relic
```

### Advantages

- Lowest migration risk.
- Existing APM behavior remains unchanged.
- No immediate application changes.
- Existing New Relic dashboards and alerts remain available.

### Disadvantages

- Does not establish OpenTelemetry as the application instrumentation standard.
- Does not automatically provide OTel traces to Tempo.
- Leaves the organization dependent on New Relic's agent instrumentation model.
- A second Java agent may be required for OTel tracing, creating overlap and complexity.

### Recommendation

Use as the **baseline during transition**, not as the target state.

---

# 7. Approach B — Run New Relic and OpenTelemetry Java Agents Together

Technically, a JVM can be configured with multiple agents:

```bash
java \
  -javaagent:/opt/newrelic/newrelic.jar \
  -javaagent:/opt/opentelemetry/opentelemetry-javaagent.jar \
  -jar application.jar
```

Conceptually:

```text
                     Java process
                    /            \
                   /              \
          New Relic Agent      OTel Agent
               |                    |
               v                    v
          New Relic            OTel Collector
                                    |
                                    +----> Tempo
```

### Advantages

- Potentially allows New Relic and OTel to operate simultaneously.
- Useful for controlled migration experiments.
- Allows comparison of the two telemetry paths.

### Pitfalls

This should **not** become the organization-wide default.

Both agents may instrument overlapping frameworks, libraries, and operations, including:

- Spring
- HTTP clients
- Servlet APIs
- JDBC
- Kafka
- messaging libraries
- RPC frameworks

Potential consequences include:

- duplicate spans
- duplicate instrumentation
- bytecode instrumentation conflicts
- increased JVM startup and runtime overhead
- confusing service/transaction relationships
- difficult troubleshooting
- differences in trace context propagation

### Recommendation

Use this approach only as a **short-lived validation/pilot mechanism** where required.

---

# 8. Approach C — Move Application Instrumentation to OpenTelemetry

The strategic target is:

```text
Java application
      |
      +-- OTel Java Agent / SDK
                  |
                  v
             OTel Collector
              /         \
             /           \
            v             v
       New Relic         Tempo
                            |
                            v
                         Grafana
```

### Advantages

- Vendor-neutral instrumentation.
- One standard across Java, Go, Python, Node.js, etc.
- Easy to route telemetry to multiple backends.
- Enables Grafana Tempo tracing without making applications Tempo-specific.
- Provides a migration path away from New Relic-specific instrumentation if desired later.
- Better long-term observability portability.

### Disadvantages

- Some New Relic-specific agent behavior may not map one-to-one to generic OTel instrumentation.
- Application teams may depend on New Relic-specific features.
- Instrumentation coverage needs validation.
- The organization must establish OTel semantic conventions and resource attributes.
- Sampling and telemetry volume need to be managed carefully.

### Recommendation

This should be the **long-term target for new services and migrated services**.

---

# 9. Approach D — OpenTelemetry Collector as a Fan-out Layer

This is the preferred architecture for the platform.

```text
                         Application
                             |
                        OTel / OTLP
                             |
                             v
                     OTel Collector
                             |
             +---------------+---------------+
             |               |               |
             v               v               v
         New Relic          Tempo            Loki
             |               |               |
             v               +-------+--------+
        New Relic APM                |
                                  Grafana
```

The same telemetry can therefore support both ecosystems.

### Benefits

- Protects the existing New Relic investment.
- Introduces Grafana distributed tracing.
- Avoids vendor lock-in at the instrumentation layer.
- Allows backend changes without application redeployment.
- Centralizes telemetry policies.

This is the primary recommended architecture.

---

# 10. Workload Integration

## 10.1 EKS

For EKS, use an agent/gateway Collector architecture.

```text
Node 1                              Node 2

+------------------+                +------------------+
| Application Pods  |                | Application Pods  |
|        |          |                |        |          |
|        v          |                |        v          |
| OTel Collector    |                | OTel Collector    |
| DaemonSet         |                | DaemonSet         |
+--------+----------+                +--------+----------+
         |                                    |
         +----------------+-------------------+
                          |
                          v
                   OTel Gateway
                      Collectors
                          |
                +---------+---------+
                |         |         |
                v         v         v
             New Relic  Tempo      Loki
```

### Why agent + gateway

The node-local Collector can provide:

- local OTLP reception
- Kubernetes metadata enrichment
- local buffering
- batching
- lower application-to-network complexity

The gateway tier can provide:

- centralized sampling
- backend routing
- security policy
- filtering/redaction
- cross-cluster aggregation

Grafana's Tempo documentation explicitly describes Collector pipelines using receivers, processors, and exporters and recommends the Collector for centralized processing and forwarding of traces.

---

## 10.2 ECS

For ECS/Fargate, an OTel Collector sidecar is a natural pattern:

```text
ECS Task
+--------------------------------+
|                                |
| Application container           |
|        |                       |
|        | OTLP                  |
|        v                       |
| OTel Collector sidecar         |
+---------------+----------------+
                |
                v
          Central Collector
```

For ECS on EC2, either a sidecar or node/host-level Collector can be considered depending on the deployment model.

---

## 10.3 Standalone EC2

For EC2 applications:

```text
EC2
+--------------------------------+
| Java / Go / other application  |
|              |                 |
|              v                 |
|      OTel Collector            |
|      systemd / container       |
+--------------+-----------------+
               |
               v
        Central Collector
```

This creates a consistent telemetry architecture across Kubernetes and non-Kubernetes workloads.

---

# 11. Distributed Trace Context Propagation

The most important capability for distributed tracing is trace-context propagation.

A request may cross multiple boundaries:

```text
ALB
 |
v
EKS service A
 |
v
EKS service B
 |
v
MSK
 |
v
ECS service C
 |
v
PostgreSQL
```

The trace context must survive those boundaries.

For HTTP this commonly uses the W3C Trace Context standard, including `traceparent`.

The desired result is one trace:

```text
Trace ID: ABC123

ALB / HTTP
    |
    +-- service-A
          |
          +-- service-B
                |
                +-- Kafka producer
                      |
                      +-- MSK
                           |
                           +-- Kafka consumer
                                 |
                                 +-- service-C
                                       |
                                       +-- PostgreSQL
```

Kafka propagation is especially relevant to this environment because MSK/Confluent-based asynchronous flows are a major part of the application architecture.

This should be explicitly validated in the pilot rather than assumed.

---

# 12. Grafana Experience

The desired observability flow is:

```text
Prometheus metrics
        |
        v
      Grafana
        ^
        |
     Loki logs
        ^
        |
     Tempo traces
```

The major benefit is correlation.

### Trace → logs

A trace can contain:

```text
trace_id=abc123
span_id=def456
```

Those identifiers can be included in application logs.

Grafana can then use the trace context to navigate from a trace to the corresponding Loki entries.

### Metrics → traces

Prometheus metrics can be linked to representative traces through exemplars.

Conceptually:

```text
HTTP latency P99
      |
      +--- exemplar ---> Trace ABC123
                              |
                              v
                           Tempo
```

This creates a practical path from:

```text
Alert
  -> metric
     -> exemplar
        -> trace
           -> logs
              -> root cause
```

This is one of the strongest reasons to add tracing to the existing Prometheus/Loki/Grafana environment.

---

# 13. New Relic Experience: What Should We Expect?

The expectation should be **high functional parity for mainstream APM use cases, but not an assumption of 100% feature equivalence with the proprietary New Relic Java agent**.

New Relic now provides an OpenTelemetry APM UI and documents OpenTelemetry service monitoring through its APM experience.

New Relic maps incoming OTLP spans into its own data model and provides an APM experience for OTel-instrumented services.

References:

- https://docs.newrelic.com/docs/opentelemetry/get-started/apm-monitoring/opentelemetry-apm-ui/
- https://docs.newrelic.com/docs/opentelemetry/best-practices/opentelemetry-best-practices-traces/

However, the proprietary Java agent can provide New Relic-specific functionality that generic OTel instrumentation may not reproduce exactly.

Therefore the migration must validate the actual capabilities used by application teams rather than treating "OTel-compatible" as automatically identical to the existing Java agent.

---

# 14. Features to Validate During the Pilot

The following should be explicitly compared between the current New Relic Java agent and OTel → Collector → New Relic:

| Area | Validation |
|---|---|
| HTTP transactions | Request names, latency, status codes |
| Distributed tracing | Complete cross-service traces |
| Spring | Controller/service instrumentation |
| JDBC | SQL/database spans and query visibility |
| PostgreSQL/MySQL | Database correlation and latency |
| Kafka/MSK | Producer/consumer trace propagation |
| External HTTP | Downstream call visibility |
| Errors | Error capture and association with traces |
| Service map | Correct service relationships |
| Runtime/JVM | GC, heap, thread, CPU visibility where required |
| Attributes | Correct environment/service metadata |
| Sampling | Correct retained trace population |
| Log correlation | Trace IDs present in logs |
| Alerting | Existing alert use cases still work |
| Dashboards | Existing New Relic dashboards retain required data |
| Overhead | CPU, memory, startup time |
| Failure handling | Telemetry loss during Collector/backend outage |

The pilot should use a real production-like transaction rather than a synthetic test alone.

---

# 15. Major Pitfalls

## 15.1 Two agents instrumenting the same JVM

Avoid making this the steady-state design.

It can cause:

- duplicate instrumentation
- duplicate spans
- conflicting instrumentation
- higher resource consumption
- difficult support cases

Use dual-agent deployment only for controlled validation where necessary.

---

## 15.2 Trace volume and cost

Tracing can produce significantly more telemetry than traditional metrics.

A high-volume service can produce enormous numbers of spans.

Therefore sampling must be part of the design from the beginning.

A useful policy could eventually be:

```text
Errors                 100%
Very slow traces       100%
Critical business flow 100%
Normal requests         5-10%
Health checks           0%
```

Actual percentages should be established from measured telemetry volume and business requirements.

The Collector is a natural location for tail sampling because complete traces can be evaluated before deciding whether to retain them.

Grafana documents tail sampling and warns that trace-derived metrics need to be designed carefully relative to sampling. See:

https://grafana.com/docs/tempo/latest/metrics-from-traces/where-to-generate-metrics/

---

## 15.3 Missing trace context

A trace is only as good as its propagation.

Common breakpoints include:

```text
HTTP -> HTTP       usually straightforward
HTTP -> Kafka      needs propagation
Kafka -> Kafka     needs propagation
Kafka -> HTTP      needs propagation
Async jobs         needs explicit design
External systems   may not propagate context
```

The architecture should define where a trace is expected to continue versus where a new trace should intentionally begin.

---

## 15.4 Poor resource naming

Standardize:

```text
service.name
service.version
deployment.environment
cloud.provider
cloud.account.id
cloud.region
k8s.cluster.name
k8s.namespace.name
k8s.deployment.name
```

Do this early.

Otherwise data becomes inconsistent across New Relic, Tempo, Loki, and Prometheus.

---

## 15.5 High-cardinality attributes

Avoid using unbounded values as metric labels.

Examples of dangerous values include:

- user IDs
- request IDs
- order IDs
- arbitrary URLs
- full SQL text

These may be appropriate as trace/span attributes in controlled circumstances but can create serious metric cardinality problems if promoted to metric dimensions.

---

## 15.6 Sensitive data

Trace attributes can accidentally expose:

- authorization headers
- cookies
- email addresses
- customer identifiers
- request payload fragments
- SQL parameters

Telemetry needs its own security and privacy review.

The Collector should be considered an enforcement point for filtering, redaction, and normalization.

---

## 15.7 Collector becomes a critical dependency

Once applications depend on the Collector, Collector availability matters.

The design therefore needs:

- multiple Collector replicas
- queueing/retry
- resource limits
- health checks
- autoscaling where appropriate
- graceful failure behavior
- monitoring of the Collector itself

New Relic recommends retry behavior for transient OTLP failures, and the Collector exporters provide retry/queue mechanisms. See:

https://docs.newrelic.com/docs/opentelemetry/best-practices/opentelemetry-otlp/

---

## 15.8 Collector overload

The Collector is not infinitely scalable.

Monitor:

- CPU
- memory
- dropped spans
- rejected telemetry
- queue length
- export errors
- processing latency
- retry volume

Use batching and appropriate resource limits.

---

# 16. Recommended Platform Architecture

For this environment, the recommended pattern is:

```text
                         Applications
                              |
                  +-----------+-----------+
                  |           |           |
                 EKS         ECS         EC2
                  |           |           |
                  v           v           v
             OTel Agent   OTel Sidecar  OTel Agent
                  |           |           |
                  +-----------+-----------+
                              |
                              v
                      OTel Gateway Tier
                              |
            +-----------------+------------------+
            |                 |                  |
            v                 v                  v
        New Relic           Tempo               Loki
          APM                 |                  |
                              +--------+---------+
                                       |
                                     Grafana
                                       |
                                  Prometheus
```

More accurately, Prometheus can remain on its existing path and only integrate with OTel where there is a specific benefit.

The architecture should therefore avoid forcing every existing metric flow through OTel just because OTel is being introduced for tracing.

---

# 17. Deployment Model for EKS

Recommended initial EKS model:

```text
                EKS Cluster

      +-----------------------------+
      |                             |
      |  Application Pods           |
      |       |                     |
      |       v                     |
      |  OTel Collector             |
      |  DaemonSet / Agent          |
      |       |                     |
      +-------+---------------------+
              |
              v
       OTel Gateway Deployment
          /       |       \
         /        |        \
        v         v         v
   New Relic    Tempo      Loki
```

The Agent layer can enrich data with Kubernetes metadata.

The Gateway layer can apply centralized policies such as tail sampling and export routing.

---

# 18. Recommended Migration Plan

## Phase 0 — Inventory

Create an inventory of:

- Java applications using the New Relic agent.
- New Relic agent versions.
- How the agent is injected.
- Current New Relic APM features used by applications.
- Existing Prometheus and Loki integration.
- Application communication patterns.
- Kafka/MSK usage.
- ECS and EC2 workloads requiring instrumentation.

Categorize services as:

```text
Tier 1 - critical business transactions
Tier 2 - important services
Tier 3 - low-risk/internal services
```

---

## Phase 1 — Build OTel Collector infrastructure

Deploy:

```text
EKS
 |
 +-- OTel Agent Collector
 |
 +-- OTel Gateway Collector
```

Initially send a limited pilot's traces to:

```text
New Relic
Tempo
```

Do not change all applications yet.

---

## Phase 2 — Pilot two or three services

Select services that demonstrate different communication patterns.

Ideal pilot:

```text
Service A
  |
  +-- HTTP --> Service B
  |
  +-- Kafka --> Service C
  |
  +-- PostgreSQL
```

This provides enough complexity to validate real distributed tracing.

Compare OTel → New Relic against the existing New Relic Java agent experience.

---

## Phase 3 — Integrate Grafana

Add:

```text
Tempo datasource
Trace-to-logs correlation
Trace-to-metrics correlation
Exemplars where appropriate
```

The desired investigation workflow becomes:

```text
Alert
  |
  v
Prometheus
  |
  v
Exemplar / Trace
  |
  v
Tempo
  |
  v
Loki
```

---

## Phase 4 — Establish sampling policy

Start conservatively.

For example:

```text
Errors                100%
Slow traces            100%
Business-critical      100%
Normal traffic          5-10%
Health checks           0%
```

Tune using actual telemetry volume and operational requirements.

---

## Phase 5 — Expand by workload class

After Java validation:

```text
Java
  -> Go
  -> Python
  -> Node.js
  -> ECS
  -> EC2
```

Use the same Collector architecture wherever practical.

---

## Phase 6 — Evaluate New Relic agent retirement

Only after equivalent functionality has been demonstrated should individual applications be considered for removal of the New Relic Java agent.

New services should preferably start with OpenTelemetry rather than introducing a new vendor-specific instrumentation dependency.

---

# 19. Success Criteria

The program should be considered successful when the following are demonstrated for pilot applications:

### Distributed tracing

```text
ALB -> EKS -> EKS -> MSK -> EKS/ECS -> Database
```

appears as a coherent trace.

### New Relic

Application teams can still perform the core APM workflows they depend on.

### Grafana

Operators can:

- find traces
- navigate from metrics to traces
- navigate from traces to logs
- identify slow downstream dependencies
- investigate errors using one correlated workflow

### Operational resilience

Telemetry failures do not cause application failures.

### Resource overhead

OTel instrumentation and Collector resource usage remain within an agreed budget.

### Data governance

Sensitive information is not unintentionally exported into telemetry backends.

---

# 20. Recommended Decision

## Adopt OpenTelemetry as the strategic instrumentation standard.

## Keep New Relic as an approved telemetry destination during the migration.

## Introduce Grafana Tempo as the distributed-tracing backend.

## Use the OpenTelemetry Collector as the central telemetry routing and processing layer.

The recommended architecture is therefore:

```text
                           OpenTelemetry
                                |
                                v
                        OTel Collector
                                |
              +-----------------+----------------+
              |                 |                |
              v                 v                v
          New Relic            Tempo             Loki
              |                 |                |
              v                 +--------+-------+
          New Relic                    Grafana
             APM                         ^
                                        |
                                   Prometheus
```

The strategic objective is not to replace New Relic immediately.

The strategic objective is to eliminate the application's dependency on a particular telemetry backend.

That means the organization can eventually choose:

```text
OpenTelemetry
      |
      +--> New Relic
      +--> Tempo
      +--> Loki
      +--> Prometheus
      +--> future backend
```

without changing the fundamental instrumentation model of every application.

---

# 21. Immediate Next Steps

1. **Inventory existing New Relic Java-agent usage and versions.**
2. **Deploy a small OTel Collector pilot environment in EKS.**
3. **Select two or three Java services for instrumentation testing.**
4. **Validate OTel → New Relic APM parity for those services.**
5. **Send the same traces to Tempo.**
6. **Configure Grafana trace/log correlation with Loki.**
7. **Test HTTP → Kafka/MSK → consumer → database propagation.**
8. **Measure CPU, memory, telemetry volume, and dropped spans.**
9. **Define enterprise resource naming and telemetry security standards.**
10. **Publish a reusable EKS/ECS/EC2 instrumentation pattern.**

The first technical milestone should be a working end-to-end trace such as:

```text
ALB
 |
v
EKS Java Service A
 |
 +---- HTTP ----> EKS Java Service B
 |
 +---- Kafka ----> MSK ----> EKS/ECS Java Service C
 |
 +---- JDBC ----> PostgreSQL

              |
              v
      OpenTelemetry Collector
             /       \
            v         v
       New Relic     Tempo
                        |
                        v
                     Grafana
                        |
                        v
                       Loki
```

That proof-of-concept will establish whether the architecture delivers the required New Relic experience while giving the organization the distributed-tracing capability currently missing from the Grafana observability stack.

---

## Reference Documentation

### New Relic

- OpenTelemetry APM UI: https://docs.newrelic.com/docs/opentelemetry/get-started/apm-monitoring/opentelemetry-apm-ui/
- New Relic OTLP endpoint: https://docs.newrelic.com/docs/opentelemetry/best-practices/opentelemetry-otlp/
- OpenTelemetry data in New Relic: https://docs.newrelic.com/docs/opentelemetry/best-practices/opentelemetry-data-overview/
- OpenTelemetry traces in New Relic: https://docs.newrelic.com/docs/opentelemetry/best-practices/opentelemetry-best-practices-traces/
- OpenTelemetry Collector processing: https://docs.newrelic.com/docs/opentelemetry/get-started/collector-processing/opentelemetry-collector-processing-intro/

### Grafana / Tempo

- Tempo introduction: https://grafana.com/docs/tempo/latest/introduction/
- OpenTelemetry Collector with Tempo: https://grafana.com/docs/tempo/latest/set-up-for-tracing/instrument-send/set-up-collector/
- Tempo instrumentation: https://grafana.com/docs/tempo/latest/set-up-for-tracing/instrument-send/set-up-instrumentation/
- Metrics from traces and sampling: https://grafana.com/docs/tempo/latest/metrics-from-traces/where-to-generate-metrics/
