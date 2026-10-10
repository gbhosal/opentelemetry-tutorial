# OpenTelemetry: Capabilities, Architecture, and Observability PoC

**Working content and presenter notes — not a PowerPoint deck**  
**Audience:** Engineering, Cloud Platform, DevOps, and application teams  
**Suggested duration:** 30–40 minutes, plus discussion  
**Narrative:** Why tracing is needed → what OpenTelemetry provides → how it fits our platform → how to prove value without prematurely replacing New Relic.

## Purpose and framing

This document develops the presentation data points and the accompanying presenter notes. It is based primarily on the supplied **OpenTelemetry + New Relic + Grafana Observability Strategy**. Additional technical details are identified through links to official OpenTelemetry, New Relic, and Grafana documentation.

The intended recommendation is incremental: adopt OpenTelemetry as the strategic instrumentation standard, retain New Relic as an approved destination during the transition, and introduce Grafana Tempo for distributed tracing while reusing the existing Prometheus, Loki, and Grafana ecosystem. This is not a proposal for a big-bang New Relic replacement.

---

# Section 1 — Overview of OpenTelemetry

## Slide 1 — Title and objective

### On-slide data points
- **OpenTelemetry: A common instrumentation layer for distributed observability**
- Extend the current observability stack with end-to-end distributed tracing.
- Preserve the existing New Relic APM experience during the transition.
- Add Grafana Tempo tracing and connect traces with existing metrics and logs.

### Suggested visual
A simple “current → target” visual: application services currently send APM data to New Relic and metrics/logs to Prometheus/Loki; the target introduces OpenTelemetry and a Collector routing layer.

### Presenter notes
“Today’s focus is not to replace one observability product with another. It is to establish a consistent way for applications to generate telemetry and to give the platform more flexibility about where that telemetry goes. The immediate capability gap we want to address is distributed tracing. We want to follow a request across service boundaries and asynchronous systems, while protecting the New Relic experience teams already use.”

“By the end, we should be able to explain OpenTelemetry’s role, understand the important building blocks, and agree on a PoC that can test the design against our real traffic patterns.”

---

## Slide 2 — The observability question: what happened to this request?

### On-slide data points
- Metrics tell us **that** something is wrong: latency, errors, saturation, traffic.
- Logs help explain **what** an individual component recorded.
- Traces show **where a request went** and where time or errors accumulated.
- Distributed systems require evidence across service, database, and messaging boundaries.

### Example transaction
`ALB → Java service A → Java service B → Kafka/MSK → consumer service C → PostgreSQL`

### Suggested visual
A single request shown as a horizontal journey, with a latency breakdown beneath each service/dependency.

### Presenter notes
“Metrics, logs, and traces answer different but complementary questions. A latency alert might tell us that Service A’s p99 has increased. Its logs may show errors, but operators can still spend time joining evidence from multiple services. A distributed trace puts the work on a common timeline and helps us identify whether the time was spent in Service A, a downstream HTTP call, Kafka processing, or a database query.”

“Tracing is not a replacement for logs or metrics. We should keep each signal where it is useful and make it possible to move between them during an investigation.”

---

## Slide 3 — What OpenTelemetry is—and is not

### On-slide data points
- OpenTelemetry (OTel) is an **open, vendor-neutral framework** for generating, collecting, and exporting telemetry.
- It provides APIs, SDKs, instrumentation libraries, a protocol (OTLP), and the Collector.
- It is **not** an APM backend, telemetry database, or visualization product.
- Backends remain responsible for storing, querying, alerting on, and visualizing data.

### Suggested visual
Four boxes in a row: **Instrument → Collect/process → Store/query → Explore/alert**, with OTel covering primarily the first two and integrating with backend products for the latter stages.

### Presenter notes
“Think of OpenTelemetry as the common telemetry plumbing and instrumentation standard. It helps applications produce telemetry consistently and provides a standard path for collecting and exporting it. It does not, by itself, give us the full user interface or retention and query engine.”

“This distinction matters architecturally. We should avoid coupling application code to Tempo or New Relic-specific telemetry APIs where a supported OpenTelemetry interface can meet the requirement. We still choose backends based on user experience, operating model, capabilities, security, and cost.”

**Reference:** https://opentelemetry.io/docs/ (official project documentation)

---

## Slide 4 — The three telemetry signals

### On-slide data points
- **Traces:** request flow and causal relationships across components.
- **Metrics:** numerical measurements over time, suitable for trends, dashboards, and alerting.
- **Logs:** timestamped events and diagnostic detail.
- The signals become more useful when they share consistent service/resource identity and trace context.

### Suggested visual
Three columns (traces / metrics / logs) feeding a single investigation workflow.

### Presenter notes
“An example helps: a metric alerts us that a service is slow; a trace shows which dependency is responsible; logs provide the detailed application messages for the affected operation. The design objective is correlation, not forcing every signal into the same product or pipeline.”

“OpenTelemetry supports all three signals. For this initiative, distributed tracing is our first priority. Existing Prometheus and Loki paths can remain in place unless there is a specific benefit to changing them.”

**Reference:** OpenTelemetry Java docs describe telemetry such as metrics, logs, and traces: https://opentelemetry.io/docs/languages/java/

---

## Slide 5 — How OpenTelemetry is assembled

### On-slide data points
- **API:** instrumentation interface used by libraries/applications.
- **SDK:** runtime implementation that records, processes, and exports telemetry.
- **Instrumentation:** automatic instrumentation agents/libraries or explicit manual instrumentation.
- **OTLP:** standard protocol for transporting telemetry.
- **Collector:** receives, processes, and exports telemetry to configured destinations.

### Suggested visual
`Application/library → OTel API/SDK or auto-instrumentation → OTLP → Collector → backend(s)`

### Presenter notes
“The API and SDK separate the application’s instrumentation interface from its implementation. Automatic instrumentation can capture common framework operations with limited code changes. Manual instrumentation is available where we need business-specific spans or context. OTLP provides the standard export protocol. The Collector then gives platform engineering a common place to process and route telemetry.”

“Not every application needs both auto-instrumentation and custom spans from day one. We should begin with the most valuable coverage and add manual instrumentation when automatic instrumentation does not describe an important business operation.”

**References:** https://opentelemetry.io/docs/ ; https://opentelemetry.io/docs/languages/java/

---

## Slide 6 — What a distributed trace contains

### On-slide data points
- A **trace** represents a distributed operation.
- A **span** represents one timed unit of work within that operation.
- Spans can have parent-child relationships and carry attributes, events, status, and timestamps.
- A trace ID groups spans that belong to the same trace; a span ID identifies an individual span.
- Trace context must be propagated between participating services and libraries.

### Suggested visual
A waterfall trace with a root HTTP span, child service spans, a database span, and a messaging producer/consumer segment.

### Presenter notes
“Each span contributes a piece of the story: which operation ran, when it started and ended, whether it failed, and relevant attributes. Parent-child relationships express the structure of work. The shared trace ID lets the backend assemble spans from different processes.”

“The key phrase is ‘participating services.’ A coherent trace is not guaranteed merely because a Collector exists. Instrumentation and context propagation must work at each boundary. Kafka and other asynchronous paths deserve explicit validation because context must be carried in message metadata and restored by the consumer instrumentation.”

---

## Slide 7 — The OpenTelemetry Collector: a shared telemetry pipeline

### On-slide data points
- **Receivers** accept telemetry (for example, OTLP).
- **Processors** batch, enrich, filter, transform, sample, or redact telemetry.
- **Exporters** send telemetry to configured destinations.
- Pipelines can route a telemetry signal to one or more backends.
- The Collector centralizes platform policy and reduces backend coupling in applications.

### Suggested visual
`EKS/ECS/EC2 apps → Collector receivers → processors → exporters → New Relic + Tempo`.
Show existing Prometheus/Loki flows alongside it instead of implying they must all move immediately.

### Presenter notes
“The Collector is the operational control point in the proposed architecture. It can enrich telemetry with environment or Kubernetes metadata, batch data, filter unwanted attributes, apply sampling, and export to configured systems. It is also where we can standardize policy instead of reimplementing it in every service.”

“However, the Collector is not infinitely scalable and should not become a single point of failure. We need to monitor its capacity, queueing, export errors, dropped telemetry, and availability. A gateway tier should be deployed with redundancy and explicit failure behavior.”

**Reference:** https://grafana.com/docs/tempo/latest/set-up-for-tracing/instrument-send/set-up-collector/otel-collector/

---

## Slide 8 — Backend independence: instrument once, route deliberately

### On-slide data points
- Applications emit OpenTelemetry telemetry through the defined instrumentation/export path.
- The Collector can route the same trace stream to **New Relic and Tempo**.
- New Relic remains available for its existing APM workflows.
- Tempo enables trace exploration in Grafana alongside Prometheus and Loki.
- Backend choice can evolve without requiring every application to adopt backend-specific instrumentation.

### Suggested visual
`OpenTelemetry-instrumented service → OTel Collector → New Relic APM` and `→ Tempo → Grafana`, with existing `Prometheus + Loki → Grafana` shown as retained paths.

### Presenter notes
“This is the central architecture principle: decouple how telemetry is produced from where it is stored or viewed. We can keep New Relic as an approved destination while proving the Grafana tracing experience. The same design gives us optionality in the future.”

“Multi-backend export still needs deliberate implementation. We must configure and test credentials, network access, retries, queueing, data governance, and the cost of sending the same telemetry to multiple systems. We should not assume every signal needs to be duplicated to every backend.”

**References:** https://docs.newrelic.com/docs/opentelemetry/best-practices/opentelemetry-otlp/ ; https://grafana.com/docs/tempo/latest/set-up-for-tracing/instrument-send/set-up-collector/otel-collector/

---

# Section 2 — Capabilities and interesting concepts

## Slide 9 — Automatic vs. manual instrumentation

### On-slide data points
- **Automatic instrumentation** captures supported framework/library operations with relatively few code changes.
- **Manual instrumentation** adds business-specific spans, attributes, or events.
- Instrumentation coverage varies by language, framework, and library version.
- Standardize service identity and environment attributes early.

### Suggested visual
A service diagram with auto-captured HTTP/server/client/database spans and an explicit “place order” business span.

### Presenter notes
“For Java, an agent can instrument supported framework and library calls, making it a practical starting point for a pilot. But automatic instrumentation cannot know every business concept. A span called ‘place order’ or ‘validate claim’ may be useful when the business workflow itself matters to diagnosis.”

“We should create a service instrumentation standard covering service.name, service.version, deployment environment, cloud and Kubernetes metadata where appropriate. Consistent naming is essential when comparing New Relic and Tempo and when filtering dashboards.”

**Reference:** https://opentelemetry.io/docs/languages/java/

---

## Slide 10 — Context propagation across synchronous and asynchronous boundaries

### On-slide data points
- HTTP usually propagates W3C Trace Context through headers such as `traceparent` when the instrumentation supports it.
- Kafka/MSK requires trace context to be injected into and extracted from message metadata.
- Consumers, background jobs, and external systems may need additional integration or explicit design.
- Validate one coherent trace across HTTP → Kafka → consumer → database.

### Suggested visual
`HTTP request → producer span → Kafka message carries context → consumer span → database span`.

### Presenter notes
“Context propagation is what connects the spans into a trace. For HTTP, instrumentation commonly propagates trace context in request headers. For Kafka, the producer has to put the context into message headers and the consumer has to extract it. If that part is missing, we may see separate traces rather than one related end-to-end journey.”

“The PoC must test the actual Java libraries, Kafka clients, framework versions, and deployment configurations used by our applications. We should explicitly decide how to handle batch jobs, scheduled work, retries, and messages that intentionally start a new trace.”

---

## Slide 11 — Sampling: manage trace volume without losing the important story

### On-slide data points
- Traces can create substantial telemetry volume at high request rates.
- **Head sampling** decides early, before the complete trace outcome is known.
- **Tail sampling** decides after spans arrive, enabling policies based on errors, latency, or attributes.
- Tail sampling requires trace-aware routing/collection and sufficient buffering/capacity.
- Start with measured traffic and business priorities; do not treat example percentages as a universal policy.

### Example policy to test—not a final standard
- Keep error and unusually slow traces at a higher rate.
- Retain critical business transactions according to requirements.
- Sample routine successful traffic at a measured rate.
- Exclude noisy health-check traces where safe.

### Suggested visual
All traces enter the gateway; a sampling decision retains some traces for storage. Show metrics generation before sampling where full traffic counts must be preserved.

### Presenter notes
“Sampling is both a cost and an observability design decision. Head sampling is efficient but cannot know whether a trace that looks normal at the beginning will later fail. Tail sampling can retain traces based on the completed outcome, but it needs a collector design that can assemble or route spans by trace and hold them long enough to decide.”

“We should not copy the illustrative numbers in the strategy directly into production. First measure span volume, understand retention and cost, and agree on the traces that must be retained. Also decide where metrics are generated: if traces are sampled before metrics are derived from them, those metrics may describe only the retained sample.”

**Reference:** https://grafana.com/docs/tempo/latest/metrics-from-traces/where-to-generate-metrics/

---

## Slide 12 — Correlating traces, logs, and metrics in Grafana

### On-slide data points
- **Trace → logs:** navigate from a span in Tempo to related Loki logs when logs include shared identifiers such as trace IDs.
- **Logs → trace:** configure Loki fields/derived fields to link from a log line to Tempo.
- **Metrics → trace:** use exemplars where instrumentation and the metrics pipeline support them.
- Trace-derived RED metrics and service graphs can complement existing metrics, but generation and sampling strategy must be coordinated.

### Suggested visual
`Prometheus alert/latency panel → exemplar/trace → Tempo span → matching Loki logs`.

### Presenter notes
“The aim is a single operational investigation journey, not necessarily a single storage backend. Operators can begin with a metric alert, inspect a representative trace, and jump to the log entries from the affected service.”

“Some wiring is required. Trace-to-logs correlation needs both Tempo and Loki datasource configuration and a shared identifier in the logs. Metrics-to-traces navigation needs suitable exemplar data. These connections should be part of the PoC acceptance criteria; they do not automatically appear just because all three tools are installed.”

“Trace-derived metrics also need care. If we sample away spans before deriving request metrics, counts and service relationships may represent only the sampled traffic. The design should explicitly choose whether to generate trace-derived metrics before or after sampling.”

**References:** https://grafana.com/docs/grafana/latest/datasources/tempo/configure-tempo-data-source/configure-trace-to-logs/ ; https://grafana.com/docs/tempo/latest/metrics-from-traces/where-to-generate-metrics/

---

## Slide 13 — Resource attributes, semantic conventions, and cardinality

### On-slide data points
- Use consistent service identity: `service.name`, `service.version`, and deployment environment.
- Enrich with relevant cloud/Kubernetes attributes where supported.
- Prefer standardized semantic attributes rather than ad-hoc naming.
- Avoid unbounded metric labels such as user IDs, order IDs, request IDs, or arbitrary URLs.
- Keep sensitive information out of telemetry by default; filter/redact where appropriate.

### Suggested visual
A telemetry record split into **identity/context**, **operation attributes**, and **sensitive/high-cardinality fields to avoid or govern**.

### Presenter notes
“Good telemetry has consistent names and enough context to answer questions. Without consistent service and environment identity, it is harder to compare the same service across environments or backends. Semantic conventions help different services describe similar operations in consistent ways.”

“Metric labels are particularly sensitive to cardinality because each unique label combination can create another time series. IDs and arbitrary URL values generally do not belong as metric dimensions. Such values might occasionally be useful in a controlled trace span, but they should be reviewed for privacy and cost before being emitted.”

“Telemetry is data leaving the application and crossing backend boundaries. We need a data-handling standard for authorization headers, cookies, customer data, SQL parameters, and payload fragments.”

---

## Slide 14 — Operational design: resilience, security, and cost

### On-slide data points
- Run Collectors redundantly; monitor saturation, queues, retries, export errors, and dropped telemetry.
- Configure batching, bounded memory, retry/queue behavior, and sensible resource limits.
- Ensure telemetry pipeline failures do not fail business requests.
- Protect credentials and network paths to New Relic and Tempo.
- Define retention, access control, redaction, and sampling policies.

### Suggested visual
A Collector gateway surrounded by operational controls: HA, health checks, queue/retry, resource limits, monitoring, security policy.

### Presenter notes
“Adding a Collector creates a platform component we operate and need to observe. The Collector’s own metrics and logs should be monitored. We must test what happens during a backend outage and avoid blocking application request processing on telemetry export.”

“Reliability is not the same as guaranteed retention. In-memory queues, retries, and persistent queues each have different guarantees and tradeoffs. We should define the acceptable loss window, size queues based on measured traffic, and load-test the configuration rather than relying only on defaults.”

“Because telemetry may contain sensitive values, the pipeline should be secured with authentication, network restrictions, secret management, filtering, and access controls at the destinations.”

---

# Section 3 — Observability PoC: OpenTelemetry + New Relic + Grafana

## Slide 15 — Current state and the capability gap

### On-slide data points
- Most existing Java applications use the New Relic Java agent for APM.
- Existing observability includes Prometheus, Loki, and Grafana.
- The target capability is distributed tracing across service and infrastructure boundaries.
- The PoC should preserve required New Relic workflows while proving traces in Grafana Tempo.

### Suggested visual
Current architecture on the left; a highlighted “distributed trace correlation” gap in the middle; target architecture on the right.

### Presenter notes
“The supplied strategy identifies New Relic Java-agent instrumentation as the current pattern for most Java applications and an existing Prometheus/Loki/Grafana ecosystem. The gap we are addressing is distributed tracing across the full transaction path.”

“The first step is not removing the current Java agent everywhere. We inventory how it is deployed and which APM features teams depend on, then validate an OTel path on a small number of services.”

**Source:** supplied strategy, sections 1 and 18–21.

---

## Slide 16 — Target PoC architecture

### On-slide data points
- Java pilot services emit OTLP telemetry using an agreed OpenTelemetry instrumentation path.
- A Collector gateway processes and routes traces to **New Relic and Tempo**.
- Grafana queries Tempo for traces and continues to use Loki and Prometheus for their existing roles.
- The PoC focuses first on traces; do not move every metric/log pipeline without a clear reason.

### Suggested visual

```text
EKS Java Service A ──HTTP──> EKS Java Service B
        │                         │
        └── PostgreSQL             └── Kafka/MSK ──> Consumer Service C
                         │
                   OTLP traces
                         ▼
                  OTel Collector(s)
                    /          \
                   ▼            ▼
              New Relic        Tempo
                  APM             │
                                  ▼
                               Grafana ◄── Loki (logs)
                                  ▲
                              Prometheus (metrics)
```

### Presenter notes
“This is the primary target design from the supplied strategy, simplified for a presentation. The same telemetry path can feed New Relic and Tempo. Grafana becomes the experience for exploring Tempo traces and correlating them with Loki logs and Prometheus metrics.”

“We should begin with a small pilot and prove network routes, authentication, data mapping, and user workflows. Prometheus can remain on its existing path unless there is a clear reason to move it.”

**References:** https://docs.newrelic.com/docs/opentelemetry/best-practices/opentelemetry-otlp/ ; https://grafana.com/docs/tempo/latest/set-up-for-tracing/instrument-send/set-up-collector/otel-collector/

---

## Slide 17 — What happens to the existing New Relic Java agent?

### On-slide data points
- **Option A:** keep the New Relic agent temporarily; safest baseline, but does not by itself provide OTel traces to Tempo.
- **Option B:** run both agents briefly for a controlled comparison; higher risk of overlapping instrumentation and duplicate spans.
- **Option C:** move selected services to OTel instrumentation and export to New Relic + Tempo; strategic target.
- Do not assume the OpenTelemetry APM experience is identical to every proprietary Java-agent feature.

### Suggested visual
A 3-column option comparison: migration risk, learning value, and strategic fit.

### Presenter notes
“The strategy outlines three realistic options. Keeping New Relic in place has the lowest immediate risk but does not establish OTel as the instrumentation standard. Running both agents can support a time-limited comparison, but the agents may instrument the same HTTP, Spring, JDBC, Kafka, or messaging operations. This can increase runtime overhead and cause duplicate or conflicting telemetry.”

“The more strategic pattern is to migrate a selected service to OTel instrumentation and send its data to both backends. Before removing the New Relic agent, we must validate the capabilities the service actually uses: transactions, errors, database visibility, Kafka propagation, service maps, JVM data, alerts, and dashboards.”

“New Relic documents an OpenTelemetry APM UI and native OTLP ingestion, but that is not proof of one-to-one feature equivalence with its proprietary agent. Feature parity is a test result, not an assumption.”

**References:** https://docs.newrelic.com/docs/opentelemetry/get-started/apm-monitoring/opentelemetry-apm-ui/ ; https://docs.newrelic.com/docs/opentelemetry/best-practices/opentelemetry-otlp/

---

## Slide 18 — Workload patterns: EKS, ECS, and standalone EC2

### On-slide data points
- **EKS:** consider node-local Collector agents for local reception/metadata enrichment plus a gateway tier for centralized policy and routing.
- **ECS/Fargate:** a task sidecar can receive OTLP from the application and forward to a central Collector.
- **ECS on EC2 / standalone EC2:** choose sidecar or host-level Collector based on deployment, network, and operational constraints.
- Standardize endpoint configuration, service identity, authentication, and resource attributes across environments.

### Suggested visual
Three columns for EKS, ECS, and EC2, each showing application → local Collector/sidecar → gateway.

### Presenter notes
“The design should be consistent without forcing one deployment mechanism everywhere. EKS benefits from Kubernetes metadata enrichment and may use a node-local agent plus gateway. In Fargate, the task sidecar pattern is a natural option. For EC2 services, we can run a Collector as a service or use a container pattern according to operational preferences.”

“The PoC should validate at least one EKS path first. After the pattern is proven, we can publish a reusable platform standard and extend it to ECS and EC2.”

**Source:** supplied strategy, sections 10, 16, and 17.

---

## Slide 19 — PoC scope and execution plan

### On-slide data points
**Phase 0 — Inventory**
- Identify candidate Java services, New Relic agent versions/injection, dependencies, and APM features used.

**Phase 1 — Platform foundation**
- Deploy a small, redundant OTel Collector pilot in EKS.
- Configure secure OTLP ingestion and export to New Relic and Tempo.

**Phase 2 — Instrumentation pilot**
- Select two or three services covering HTTP, Kafka/MSK, and PostgreSQL.
- Validate trace context and attributes end to end.

**Phase 3 — Grafana workflow**
- Configure Tempo and trace-to-logs correlation with Loki; test metrics-to-traces where exemplars are supported.

**Phase 4 — Measure and decide**
- Compare user experience, feature coverage, overhead, data volume, and reliability.

### Suggested visual
A 5-phase roadmap with explicit gates before expanding scope.

### Presenter notes
“The pilot should include enough real dependency diversity to prove distributed tracing rather than just showing a single-service trace. The ideal flow is an HTTP request entering Service A, a downstream call to Service B, a Kafka message consumed by Service C, and database activity.”

“We should use production-like traffic or a representative test workload, not rely only on a synthetic hello-world app. Each phase has a gate: verify telemetry arrives, verify trace continuity, verify operator workflows, then measure operational cost and decide whether to expand.”

**Source:** supplied strategy, sections 18–21.

---

## Slide 20 — PoC acceptance criteria: what does “success” mean?

### On-slide data points
1. **Trace completeness:** expected HTTP → service → Kafka/MSK → consumer → database spans appear in a coherent trace where propagation is supported.
2. **New Relic usefulness:** the pilot service retains the APM workflows the team relies on.
3. **Grafana usefulness:** operators can find traces in Tempo and navigate to related Loki logs; test metrics-to-trace links where supported.
4. **Data quality:** service/environment attributes are consistent and sensitive attributes are not unintentionally exported.
5. **Resilience:** a backend or Collector problem does not fail the business request; telemetry loss and recovery behavior are understood.
6. **Overhead and cost:** CPU, memory, startup impact, spans per second, dropped telemetry, and backend ingestion are measured against agreed thresholds.

### Suggested visual
A pass/partial/fail scorecard, with each criterion having an owner and evidence link.

### Presenter notes
“Success should be evidence-based. It is not enough to see a trace in Tempo. We need the spans to be complete enough for the selected use case, service identity to be consistent, and the user workflow to be useful. We need the New Relic experience compared against a recorded baseline.”

“Operational acceptance matters just as much: we must know how the pipeline behaves under load, when a backend is unavailable, and when attributes are filtered. Before starting, agree on thresholds and document the baseline so the final decision is transparent.”

---

## Slide 21 — Key risks and mitigations

### On-slide data points
| Risk | Mitigation / validation |
|---|---|
| Duplicate instrumentation with dual Java agents | Use only for a time-limited test; inspect spans and runtime overhead. |
| Broken HTTP/Kafka trace context | Exercise actual clients/frameworks and inspect context injection/extraction. |
| New Relic feature gaps | Build a feature-by-feature comparison against current agent usage. |
| Trace volume and ingestion cost | Measure first; set an explicit sampling and retention policy. |
| High-cardinality or sensitive attributes | Standardize resource attributes; filter/redact before export. |
| Collector overload or outage | Multiple replicas, limits, queue/retry policy, self-monitoring, load testing. |
| Misleading trace-derived metrics after sampling | Choose metric-generation point based on sampling policy. |

### Suggested visual
Risk table with severity and mitigation owner added during PoC planning.

### Presenter notes
“These are design risks to prove down, not reasons to avoid OpenTelemetry. The main discipline is to avoid treating a working demo as production readiness. We need to test the actual code paths, telemetry volume, failure modes, and current New Relic dependencies before broad adoption.”

“Sampling deserves special attention: storing a subset of traces is often sensible, but metrics generated from that subset may not represent the full request population. We should design tracing and metrics together.”

---

## Slide 22 — Recommendation and next decisions

### On-slide data points
- Adopt OpenTelemetry as the strategic instrumentation standard for new and migrated services.
- Retain New Relic as an approved telemetry destination during migration.
- Introduce Tempo for Grafana-based distributed tracing.
- Keep existing Prometheus and Loki paths where appropriate; integrate rather than migrate by default.
- Start with two or three Java services and expand only after the acceptance criteria pass.

### Decisions requested
- Approve the pilot scope and candidate services.
- Agree on the required New Relic feature-parity checklist.
- Agree on a trace-volume, retention, and data-governance measurement plan.
- Assign owners for Collector platform operations and Grafana correlation configuration.

### Presenter notes
“The recommendation is an incremental architecture change. We do not need to choose between New Relic and Grafana immediately. We can standardize instrumentation, retain New Relic, and add tracing capability to Grafana, then use evidence from a small pilot to decide how quickly to migrate individual services.”

“The first milestone should be one coherent trace through a representative application flow, visible in both New Relic and Tempo, with a documented comparison of functionality, cost, overhead, and failure behavior.”

**Source:** supplied strategy, sections 20–21.

---

# Appendix — Technical guardrails and discussion prompts

## Terminology to keep consistent
- **Telemetry:** emitted observability data, including traces, metrics, and logs.
- **Trace:** end-to-end representation of an operation across components.
- **Span:** one timed operation within a trace.
- **Context propagation:** carrying trace context across process or messaging boundaries.
- **OTLP:** OpenTelemetry Protocol used to transmit telemetry.
- **Collector:** component that receives, processes, and exports telemetry.
- **Tempo:** trace backend in the Grafana ecosystem.
- **Exemplar:** a metric data point that can reference a representative trace, when supported and configured.

## Statements to avoid overclaiming
- Do not say OTel automatically provides every New Relic Java-agent feature.
- Do not say traces will automatically correlate with logs and metrics without propagation, fields, and datasource configuration.
- Do not present example sampling percentages as production recommendations before volume and business requirements are measured.
- Do not imply Prometheus and Loki must be moved to the Collector as part of the first tracing PoC.
- Do not imply telemetry export errors are guaranteed never to lose data; describe the actual queueing and retry behavior after it is tested.
- Do not claim a single trace across all systems until the chosen libraries and propagation boundaries have been demonstrated.

## Questions for PoC kickoff
1. Which two or three Java services best represent the HTTP, Kafka/MSK, and database paths?
2. Which New Relic APM features are mandatory for each candidate service?
3. Which trace attributes and log fields are permitted, sensitive, or prohibited?
4. What is the expected throughput and acceptable telemetry loss window during backend outages?
5. Who owns the Collector, Tempo, and Grafana datasource lifecycle and on-call support?
6. What thresholds define acceptable CPU, memory, latency overhead, ingestion volume, and cost?

## Official references

### OpenTelemetry
- Project documentation: https://opentelemetry.io/docs/
- Java instrumentation and SDK documentation: https://opentelemetry.io/docs/languages/java/

### New Relic
- OTLP endpoint and export guidance: https://docs.newrelic.com/docs/opentelemetry/best-practices/opentelemetry-otlp/
- OpenTelemetry APM UI: https://docs.newrelic.com/docs/opentelemetry/get-started/apm-monitoring/opentelemetry-apm-ui/
- OpenTelemetry APM introduction: https://docs.newrelic.com/docs/opentelemetry/get-started/apm-monitoring/opentelemetry-apm-intro/

### Grafana / Tempo
- OpenTelemetry Collector with Tempo: https://grafana.com/docs/tempo/latest/set-up-for-tracing/instrument-send/set-up-collector/otel-collector/
- Trace-to-logs correlation: https://grafana.com/docs/grafana/latest/datasources/tempo/configure-tempo-data-source/configure-trace-to-logs/
- Where to generate metrics from traces: https://grafana.com/docs/tempo/latest/metrics-from-traces/where-to-generate-metrics/

### Primary internal working source
- Supplied file: `otel-newrelic-grafana-migration-strategy(1).md` — OpenTelemetry + New Relic + Grafana Observability Strategy.
