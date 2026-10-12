# OpenTelemetry FAQ

## Java agent vs embedded API and SDK

### What is the difference between the Java agent and an embedded API and SDK?

Both can emit the same OTLP. A backend cannot tell them apart from the protocol. They differ in where instrumentation lives, what it can see without application changes, and who upgrades it.

The agent is `opentelemetry-javaagent.jar`, passed on the JVM command line. It loads before `main`, rewrites supported libraries as their classes load, and installs its own SDK. Coverage includes servers and clients such as Spring MVC, HTTP clients, JDBC, Logback, and the JVM. Application code stays unchanged. Configuration is environment variables such as `OTEL_SERVICE_NAME` and `OTEL_EXPORTER_OTLP_ENDPOINT`. The agent version is the jar on the command line, independent of the application build.

The API is the instrumentation interface: `Tracer`, `Span`, `Meter`, and context propagation. The SDK is the implementation that samples, batches, and exports. Embedding them means those libraries are normal application dependencies. The service builds an `OpenTelemetrySdk` in code, or pulls in autoconfigure or the Spring Boot starter so the SDK starts with the process. Exporters, resource attributes, and processors are then part of the application artifact.

The API alone does not record anything. Something in-process has to install an SDK. That something is either the agent or the embedded SDK.

### Does the agent already contain the API and SDK?

Yes. The agent jar ships the API, the SDK, exporters, and the bytecode instrumentation. Attaching it is enough to export OTLP. An application that only wants custom spans can depend on `opentelemetry-api` and call `Span.current()` or `GlobalOpenTelemetry`. The agent supplies the SDK behind that API. The application does not also embed `opentelemetry-sdk`.

### What shows up with no application changes?

| | Java agent | Embedded SDK only |
| --- | --- | --- |
| Incoming HTTP | Spans and `http.server.request.duration` | Only if the app or an instrumentation library creates them |
| Outbound HTTP | Client span and `http.client.request.duration` | Same requirement |
| JDBC | Spans for each statement | Same requirement |
| Connection pool and JVM | `db.client.connections.*` and `jvm.*` | Same requirement |
| Logs with trace context, exported over OTLP | Logback instrumentation injects the active span and ships the log record | Needs the log appender bridge or the starter |
| Business steps the libraries do not see | Invisible until the app sets an attribute or starts a span | Same. The SDK records only what something asks it to record |

"Embedded SDK only" means the SDK is on the classpath and no instrumentation library or agent is installed. Framework calls then produce no spans. Teams that embed telemetry usually add those libraries, or use the Spring Boot starter, which bundles a large part of the agent's coverage as dependencies.

### How do you add a business span or attribute?

Depend on the API and use the current span, for example an attribute such as `payment.status=DECLINED` or a child span around a business step. With the agent attached, that attribute lands on the existing span and is exported with everything else.

Embedding the SDK is a separate choice. It is how you install telemetry when `-javaagent` is not available. It is not required for a custom attribute on a service that already runs the agent.

### How are they configured and upgraded?

The agent reads `OTEL_*` environment variables and system properties. Changing the endpoint, protocol, or service name is a process setting. Upgrading instrumentation means replacing the agent jar and restarting the process. Application code does not change.

An embedded SDK can read the same environment variables when it is started through autoconfigure. A hand-built `SdkTracerProvider` uses whatever the code sets. That configuration is reviewed and released with the service. Each service can drift onto a different SDK version.

The agent keeps its classes in a classloader isolated from the application. An embedded SDK shares the application classloader, so its API version has to agree with any other library on the classpath that also imports OpenTelemetry.

### How do startup and runtime differ?

The agent spends time at startup rewriting supported libraries, and it adds some memory for that instrumentation. The same libraries cost a similar amount when the Spring Boot starter instruments them from the classpath.

An SDK with only a few manual spans does less work, and it also sees less: no HTTP, JDBC, or JVM series unless those instruments are registered.

The agent has to be allowed on the JVM command line. A platform that forbids `-javaagent`, or a GraalVM native image that cannot rewrite bytecode at runtime, is a case for the embedded SDK and compile-time instrumentation.

### When is the agent the better default?

Use the agent when the goal is coverage of frameworks you do not own, without a release of every service. It is also the better default when platform engineering wants one instrumentation version across services and can set the JVM command.

### When is an embedded SDK the better fit?

Embed the SDK when the process cannot load a Java agent, when the telemetry has to ship inside the application artifact, or when a library must record spans in any host application and cannot assume an agent is present. A shared library should depend on the API, and leave the SDK to the application or the agent.

Embed it when tests need an in-memory exporter. Production can still use the agent. Tests can install `OpenTelemetrySdk` and assert the spans the application code created.

### Can the agent and an embedded SDK run together?

Running both installs two SDKs and, if the starter or instrumentation libraries are also present, instruments the same frameworks twice. The trace then contains duplicate spans, and the two paths can disagree about context propagation.

Pick one owner for the SDK:

- Agent attached, application code uses only the API for business attributes.
- SDK embedded, no `-javaagent`. Add the starter or individual instrumentation libraries for framework spans.

A vendor agent beside the OpenTelemetry agent has the same overlap. Treat two Java agents as a short-lived comparison, not the steady state.

### Where does the Spring Boot starter fit?

`opentelemetry-spring-boot-starter` is the embedded path with batteries included. It is a dependency that starts the SDK and instruments Spring, HTTP clients, JDBC, and logging without `-javaagent`. Configuration still uses `OTEL_*` settings, usually through application properties.

The starter is versioned and released with the service, lives on the application classpath, and can support native images that the agent cannot. The agent covers a wider set of libraries.

Use the starter or the agent, not both on the same process.

### Do correlation and routing change?

No. Either path exports OTLP. Trace IDs on spans and on OTLP log records are what a backend uses to move between a trace and its logs. Collector routing is the same for agent telemetry and SDK telemetry.

## Node.js and Python

### Does OTLP support Node.js and Python?

Yes. OTLP is the wire protocol, not a Java feature. Official Node.js and Python SDKs export traces, metrics, and logs over OTLP/gRPC and OTLP/HTTP. Any Collector that accepts OTLP can store that data. The conventional ports are 4317 for gRPC and 4318 for HTTP. Backends select the service with `service.name`.

Neither runtime has a JVM-style agent jar. Each has a zero-code launcher that plays the same role: it starts the SDK and patches supported libraries before the application code runs. Business spans still use the language API.

The examples below use HTTP/protobuf on port 4318. Set `OTEL_EXPORTER_OTLP_ENDPOINT` to the Collector address for the environment.

### How do you instrument a Node.js application?

Zero-code instrumentation loads `@opentelemetry/auto-instrumentations-node` before the application. That package covers common servers and clients (`http`, Express, Fastify, `pg`, and others) plus Pino and Winston logs.

```bash
npm install @opentelemetry/api @opentelemetry/auto-instrumentations-node
```

```bash
export OTEL_SERVICE_NAME=node-service
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
export OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
export OTEL_TRACES_EXPORTER=otlp
export OTEL_METRICS_EXPORTER=otlp
export OTEL_LOGS_EXPORTER=otlp
export NODE_OPTIONS="--require @opentelemetry/auto-instrumentations-node/register"
node app.js
```

`NODE_OPTIONS` must stay in front of the application entrypoint so the SDK starts before the HTTP framework or the database driver is required.

A business span uses the API only. The register hook already installed the SDK:

```javascript
const { trace } = require("@opentelemetry/api");

trace.getTracer("node-service").startActiveSpan("charge", (span) => {
  span.setAttribute("payment.status", "APPROVED");
  span.end();
});
```

### How do you instrument a Python application?

Zero-code instrumentation uses `opentelemetry-instrument`, the wrapper installed by `opentelemetry-distro`. `opentelemetry-bootstrap` detects libraries already installed in the environment, such as Flask, Django, requests, and psycopg, and installs the matching instrumentation packages.

```bash
pip install opentelemetry-distro opentelemetry-exporter-otlp
opentelemetry-bootstrap -a install
```

```bash
export OTEL_SERVICE_NAME=python-service
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
export OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
export OTEL_TRACES_EXPORTER=otlp
export OTEL_METRICS_EXPORTER=otlp
export OTEL_LOGS_EXPORTER=otlp
export OTEL_PYTHON_LOGGING_AUTO_INSTRUMENTATION_ENABLED=true
opentelemetry-instrument python app.py
```

`OTEL_PYTHON_LOGGING_AUTO_INSTRUMENTATION_ENABLED` attaches the standard-library logger to the active span and lets the OTLP log exporter ship those records. Traces and metrics are exported without that variable.

A business span uses the API only:

```python
from opentelemetry import trace

tracer = trace.get_tracer("python-service")
with tracer.start_as_current_span("charge") as span:
    span.set_attribute("payment.status", "APPROVED")
```

### How do Node.js and Python telemetry show up beside other services?

`OTEL_SERVICE_NAME` becomes `service.name`. Query traces by that resource attribute, logs by the `service_name` label, and metrics by the `job` label.

HTTP metric names follow the same semantic conventions when the instrumentation is current, so a server histogram is `http_server_request_duration`. Runtime metrics differ by language. Node reports its event loop and heap. Python reports its process. JVM series such as `jvm_memory_used` come from the Java runtime only.

## Excluding URLs from traces

The Docker health checks in [services/docker-compose.yml](../services/docker-compose.yml) open a TCP socket and send no HTTP request, so they have no URL to exclude. The settings below apply to an HTTP path such as `/actuator/health` or `/health`.

Drop the request in the process when the whole trace should disappear, including JDBC and outbound HTTP spans created while handling it. Drop it in the Collector when every service should follow one rule and the services cannot be reconfigured. The Collector image in this demo is `otel/opentelemetry-collector-contrib:0.162.0`, which includes the filter processor.

A trace sampler and a trace filter leave `http.server.request.duration` in place. That histogram is recorded in the process. Tempo builds `traces_spanmetrics_*` from spans that arrive at Tempo, so a Collector trace filter also keeps those URLs out of the span metrics.

### How does the Java agent exclude URLs?

The agent jar on the service images is 2.31.1. It has no environment variable that lists URLs to skip. From 2.21 the jar includes `RuleBasedRoutingSampler`. From 2.26 that sampler is set with declarative configuration. Agent support for declarative configuration is experimental.

```text
java -javaagent:/otel/opentelemetry-javaagent.jar -Dotel.config.file=/otel/otel-config.yaml -jar /app/app.jar
```

With `-Dotel.config.file` set, the SDK reads that file. The `service` detector reads `OTEL_SERVICE_NAME`. Reference `OTEL_RESOURCE_ATTRIBUTES` and `OTEL_EXPORTER_OTLP_ENDPOINT` from the file so the values in `services/docker-compose.yml` still apply. Declare a `meter_provider` and a `logger_provider` the same way, with `/v1/metrics` and `/v1/logs`, for those signals to keep exporting.

`parent_based` consults the parent sampling decision. An incoming request has no parent, so `root` runs. `rule_based_routing` then drops server spans whose `url.path` matches a rule. The unsampled decision is what child spans inherit, so the JDBC and HTTP client spans for that request are dropped with the server span. A top-level `rule_based_routing` sampler, with no `parent_based` wrapper, only considers server spans. Other spans in the same request still export.

`url.path` is the path this agent records, without the query string. Each `pattern` is a regular expression against the whole value. `/actuator.*` covers `/actuator` and `/actuator/health`.

```yaml
file_format: "1.0"

resource:
  attributes_list: ${OTEL_RESOURCE_ATTRIBUTES}
  detection/development:
    detectors:
      - service:

tracer_provider:
  processors:
    - batch:
        exporter:
          otlp_http:
            endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:-http://localhost:4318}/v1/traces
  sampler:
    parent_based:
      root:
        rule_based_routing:
          fallback_sampler:
            always_on:
          span_kind: SERVER
          rules:
            - action: DROP
              attribute: url.path
              pattern: /actuator.*
            - action: DROP
              attribute: url.path
              pattern: /(health|ready|live)(/.*)?
```

The Spring Boot starter uses the same `tracer_provider.sampler` block in its declarative file. Attach the agent or embed the starter, not both.

### How does Node.js exclude URLs?

`@opentelemetry/auto-instrumentations-node/register` reads exporter and resource settings from the environment. It does not read a URL exclude list. `OTEL_NODE_DISABLED_INSTRUMENTATIONS` turns off an instrumentation package. It does not filter paths.

Start the SDK in a module that runs before the application, and pass hooks to the HTTP instrumentation. `ignoreIncomingRequestHook` receives the Node.js incoming message. `request.url` is the path plus the query string. `ignoreOutgoingRequestHook` receives the client request options. `request.path` is that request's path plus query. Return `true` to skip the span.

```javascript
const { NodeSDK } = require("@opentelemetry/sdk-node");
const { getNodeAutoInstrumentations } = require("@opentelemetry/auto-instrumentations-node");

const healthPath = /^\/(health|ready|live)(\/|\?|$)/;

const sdk = new NodeSDK({
  instrumentations: [
    getNodeAutoInstrumentations({
      "@opentelemetry/instrumentation-http": {
        ignoreIncomingRequestHook(request) {
          return healthPath.test(request.url ?? "");
        },
        ignoreOutgoingRequestHook(request) {
          return healthPath.test(request.path ?? "");
        },
      },
    }),
  ],
});

sdk.start();
```

```bash
node -r ./tracing.js app.js
```

Keep the `OTEL_SERVICE_NAME` and `OTEL_EXPORTER_OTLP_*` variables from the zero-code example. Use this entrypoint in place of `NODE_OPTIONS=--require @opentelemetry/auto-instrumentations-node/register`. Loading both starts two SDKs.

### How does Python exclude URLs?

`opentelemetry-instrument` reads comma-separated regular expressions from `OTEL_PYTHON_EXCLUDED_URLS`. Each expression is searched against the URL, so `health` also matches a URL that only contains those letters. Anchor the expression to the path.

```bash
export OTEL_PYTHON_EXCLUDED_URLS='/(health|ready|live)(/|\?|$)'
opentelemetry-instrument python app.py
```

A library-specific variable replaces the general list for that library. It does not add to it. The library names are Django, Falcon, FastAPI, Flask, Pyramid, Requests, Starlette, Tornado, urllib, and urllib3.

```bash
export OTEL_PYTHON_FASTAPI_EXCLUDED_URLS='/(health|ready|live)(/|\?|$)'
export OTEL_PYTHON_REQUESTS_EXCLUDED_URLS='/(health|ready|live)(/|\?|$)'
```

The first variable applies to FastAPI server spans. The second applies to `requests` client spans. Other libraries still use `OTEL_PYTHON_EXCLUDED_URLS` when that variable is set.

### How does the Collector exclude URLs?

The filter processor drops any span that matches one condition. `IsMatch` uses RE2. A dot matches any character, so anchor a path. `url.path` is the server path. A client span records the destination on `url.full`. `http.route` is the route template when the framework set one, such as `/orders/{id}`.

Put the filter on the traces pipeline ahead of `batch`. The processor is alpha. Dropping a server span here leaves its child spans in the export, which the processor documents as orphaned telemetry. Logs for that request still go to Loki. They can carry a trace id that Tempo does not have.

```yaml
processors:
  filter/exclude_urls:
    error_mode: ignore
    trace_conditions:
      - 'IsMatch(span.attributes["url.path"], "^/(health|ready|live)(/.*)?$")'
      - 'IsMatch(span.attributes["url.path"], "^/actuator(/.*)?$")'
      - 'IsMatch(span.attributes["http.route"], "^/(health|ready|live|actuator)(/.*)?$")'
      - 'IsMatch(span.attributes["url.full"], "^https?://[^/]+/(health|ready|live|actuator)(/|\\?|$)")'

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, filter/exclude_urls, batch]
      exporters: [otlp_grpc/tempo]
```

To drop the in-process HTTP server histogram for those routes, add `metric_conditions` on the same processor and put that processor on the metrics pipeline. The route is the `http.route` datapoint attribute. A missing attribute does not match.

```yaml
processors:
  filter/exclude_urls:
    error_mode: ignore
    trace_conditions:
      - 'IsMatch(span.attributes["url.path"], "^/(health|ready|live)(/.*)?$")'
      - 'IsMatch(span.attributes["url.path"], "^/actuator(/.*)?$")'
      - 'IsMatch(span.attributes["http.route"], "^/(health|ready|live|actuator)(/.*)?$")'
      - 'IsMatch(span.attributes["url.full"], "^https?://[^/]+/(health|ready|live|actuator)(/|\\?|$)")'
    metric_conditions:
      - 'metric.name == "http.server.request.duration" and IsMatch(datapoint.attributes["http.route"], "^/(health|ready|live|actuator)(/.*)?$")'

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, filter/exclude_urls, batch]
      exporters: [otlp_grpc/tempo]
    metrics:
      receivers: [otlp]
      processors: [memory_limiter, filter/exclude_urls, batch]
      exporters: [otlp_http/mimir]
```

## Custom business metrics

### How do I record a business counter such as orders created or payments recorded?

Automatic instrumentation counts HTTP requests, database calls, and runtime resources. It does not know that an order was inserted or that a payment was stored. The application records those with the OpenTelemetry Metrics API. The Java agent, the Node.js register hook, or `opentelemetry-instrument` supplies the SDK that exports them. Application code depends on the API only.

Create each counter once at startup. Call `add(1)` after the business write succeeds, so a failed insert is not counted. Attributes must stay low-cardinality. `payment.status` with values such as `APPROVED` and `DECLINED` is safe. An order id, customer id, or payment id creates one time series per value.

The instrument names below are `order.created` and `payment.recorded`. The unit `{order}` or `{payment}` marks a count of those items. Export uses the same OTLP metric pipeline as the runtime metrics. No separate Collector destination is required.

### How does Java record them?

Depend on the API, in the version that matches the SDK inside the agent or the Spring Boot starter:

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <version>1.65.0</version>
</dependency>
```

```java
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

Meter orderMeter = GlobalOpenTelemetry.getMeter("com.example.order");
LongCounter ordersCreated = orderMeter.counterBuilder("order.created")
        .setDescription("Orders inserted")
        .setUnit("{order}")
        .build();

// after the order insert succeeds
ordersCreated.add(1);

Meter paymentMeter = GlobalOpenTelemetry.getMeter("com.example.payment");
LongCounter paymentsRecorded = paymentMeter.counterBuilder("payment.recorded")
        .setDescription("Payments inserted")
        .setUnit("{payment}")
        .build();

// after the payment insert succeeds
paymentsRecorded.add(1, Attributes.of(
        AttributeKey.stringKey("payment.status"), "APPROVED"));
```

Build the counters during application startup, after the agent has installed the SDK. A Spring bean created during context refresh is late enough. `GlobalOpenTelemetry.getMeter` returns a no-op meter when no SDK is installed, and a counter obtained from that no-op meter stays a no-op.

### How does Python record them?

`opentelemetry-distro` already depends on the API. `opentelemetry-instrument` installs the meter provider before the application runs.

```python
from opentelemetry import metrics

order_meter = metrics.get_meter("com.example.order")
orders_created = order_meter.create_counter(
    "order.created",
    unit="{order}",
    description="Orders inserted",
)

# after the order insert succeeds
orders_created.add(1)

payment_meter = metrics.get_meter("com.example.payment")
payments_recorded = payment_meter.create_counter(
    "payment.recorded",
    unit="{payment}",
    description="Payments inserted",
)

# after the payment insert succeeds
payments_recorded.add(1, {"payment.status": "APPROVED"})
```

Create the counters once, and hold them for the life of the process. `metrics.get_meter` is a no-op until `opentelemetry-instrument`, or an embedded SDK, has set the global meter provider.

### How does Node.js record them?

`@opentelemetry/api` is the only package the application imports. The register hook from `@opentelemetry/auto-instrumentations-node` installs the meter provider.

```javascript
const { metrics } = require("@opentelemetry/api");

const orderMeter = metrics.getMeter("com.example.order");
const ordersCreated = orderMeter.createCounter("order.created", {
  description: "Orders inserted",
  unit: "{order}",
});

// after the order insert succeeds
ordersCreated.add(1);

const paymentMeter = metrics.getMeter("com.example.payment");
const paymentsRecorded = paymentMeter.createCounter("payment.recorded", {
  description: "Payments inserted",
  unit: "{payment}",
});

// after the payment insert succeeds
paymentsRecorded.add(1, { "payment.status": "APPROVED" });
```

Create the counters after the register hook has run. Requiring the API at module scope is safe when `NODE_OPTIONS` loads the hook first. A counter created before any meter provider exists stays a no-op.

### What name does the metrics backend store?

The OTLP name is the instrument name, `order.created` or `payment.recorded`. A Prometheus-compatible store rewrites each dot to an underscore, so the series are `order_created` and `payment_recorded`. Some stores also append `_total` because the instrument is a counter, which produces `order_created_total` and `payment_recorded_total`.

`payment.status` is a label on `payment_recorded`. The service name is the `job` label when the backend maps `service.name` that way.

```promql
sum by (job) (order_created)
sum by (payment_status) (payment_recorded)
```

Use the `_total` names in that query when the store adds the counter suffix. The attribute key `payment.status` is stored as `payment_status`.
