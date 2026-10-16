# Architecture

`jsonapi-java-spring` is a separate companion product to
[jsonapi-java](https://github.com/kazforge/jsonapi-java). Core owns framework-neutral
JSON:API semantics and runtimes; this repository owns Spring-specific integration.

`jsonapi-java-spring-webmvc` owns JSON:API HTTP adaptation: it provides an opt-in
`@JsonApiEndpoint` marker whose handlers get JSON:API 1.1 request and response media
negotiation, operation-aware request/query context, and safe protocol error translation,
without affecting ordinary Spring MVC endpoints. [ADRs](adr/README.md) record durable decisions.

## Repository structure

| Location | Current responsibility |
| --- | --- |
| `settings.gradle.kts` | Module membership and dependency repositories. |
| Root build | Build/quality orchestration and Sonar configuration. |
| `build-logic` | Reusable formatting and Java library quality conventions. |
| `jsonapi-java-spring-webmvc` | Neutral WebMVC integration: media negotiation, request/query context, resource DTO binding, protocol errors, and explicit document responses. |
| `examples/jackson3-webmvc` | Non-published application wiring and real Jackson 3 consumer smoke tests. |
| `gradle/libs.versions.toml` | Shared build/dependency versions. |
| `.github` | Repository CI and quality-gate automation. |
| `docs/release.md` | Independent versioning, signed Central publication and validation rehearsals. |
| `docs` | Architecture and decision records. |

## Dependency boundary

Dependencies resolve from Maven Central; the Plugin Portal also supplies build tooling.
`build-logic` is a local included build for conventions, not a runtime module.
Spring integration's core dependency boundary is released public neutral APIs, not
core source or internal packages.

The WebMVC module exposes `jsonapi-java-api:0.4.0`, `jsonapi-java-query:0.4.0`, Spring WebMVC, and
the Servlet 6.1 API as API dependencies. Core 0.4.0 is the sole supported Core baseline.
The adapter's production and test dependencies contain no Jackson runtime. The separate
non-published consumer example depends on `jsonapi-java-jackson3:0.4.0` from Maven Central
and the local Spring adapter. Jackson-specific construction belongs to that application,
which supplies the neutral `JsonApi` bean. No source substitution or local Core build is used.

## Request boundary

`@JsonApiEndpoint` stays in `com.kazforge.jsonapi.spring.webmvc`; media negotiation lives
in `.media`, request context in `.request`, protocol translation in `.error`, and explicit
document response transport in `.document`. Production packages are null-marked.

Applications register `JsonApiMediaNegotiationInterceptor` before
`JsonApiRequestContextInterceptor`, plus `JsonApiMediaNegotiationAdvice` as a bean and
`JsonApiRequestContextArgumentResolver` through `WebMvcConfigurer#addArgumentResolvers`.
Resource DTO binding additionally registers one `JsonApiResourceHttpMessageConverter` with Spring
7's `WebMvcConfigurer#configureMessageConverters(HttpMessageConverters.ServerBuilder)` using
`addCustomConverter`, ahead of defaults. Standard Spring request-body/response-body processing,
validation, and `ResponseEntity` handling remain in place. The converter checks the current servlet
dispatch through Spring `RequestContextHolder` and then reads its request-attribute context; it
keeps no mutable request state or library-owned thread-local. Controllers can receive
`JsonApiRequestContext` as well.

Context carries the actual HTTP method, neutral `PrimaryDataContext`, and `JsonApiQuery`.
The endpoint annotation defaults to `RESOURCE`; a method annotation replaces controller
metadata, including defaults. Related-resource URLs are resource endpoints, whereas linkage
URLs select `RELATIONSHIP`. Relationship POST/PATCH/DELETE must not be treated as resource
create/update/delete solely from the verb.

For a `RESOURCE` endpoint, POST request bodies bind once through the supplied runtime's
`resources().readCreate`. DTO responses use `writeOne`; typed collections use `writeMany`, with
the current request's representation selection passed per operation. `JsonApiResponse<T>` adds
optional top-level meta, links, or an explicit JSON:API object without replacing the application
value. Unwrapped responses remain ordinary resource documents. Relationship endpoints and ordinary
unmarked handlers are not claimed. Applications retain ownership of persistence, query execution,
authorization, route identity, and domain validation policy. Resource and collection values are
non-null; plain null or bodyless returns remain Spring bodyless responses.

Only the raw servlet query string is passed to the public query parser, with an absent string
treated as empty. The parser owns decoding, syntax, ordering, immutable snapshots, and diagnostic
attribution. Servlet body/form parameters are not merged. Parsed query state is cached on the
request by raw query input for redispatch; operation metadata is refreshed for each handler.
Ordinary handlers clear the dispatch context. Representation selection is the query's existing
neutral value, not a separate Spring model or effective authorization decision.

Page/filter/custom values remain opaque. Applications own query execution, endpoint support
policy, authorization, and route identity. Parser exceptions retain their neutral diagnostics;
the protocol resolver translates them without treating exception messages as public contracts.

## Protocol errors and document transport

Register `JsonApiProtocolExceptionResolver` with a caller-supplied `JsonApi` runtime before
Spring's default exception resolvers; application handlers may precede it. It checks the selected
handler's endpoint marker because request context may not exist when parsing fails. Negotiation
uses Spring's typed media exceptions, retaining default status handling without this resolver.

Query, neutral document-read, known resource-input, and Spring validation failures produce 400
responses; negotiation produces 406/415. Errors reuse core `ErrorObject`, `ErrorSource`, and
`JsonApiDocument` values with fixed titles and diagnostic codes. Exception details and unverified
document pointers are omitted. Unrelated mapping, configuration, and application exceptions are not
classified as client errors.

`JsonApiDocumentResponseWriter` accepts a neutral document, explicit HTTP status and servlet
response for advanced use. Raw reads require an explicit `DocumentReadContext` through the runtime.
Serialization delegates to `JsonApi.documents()` for advanced documents and `JsonApi.resources()`
for conventional DTOs, preserving runtime defaults without a Spring-owned serializer or runtime
discovery.

Both response paths serialize fully before writing. The DTO converter skips committed responses and
resets uncommitted output mode while restoring status and headers; the advanced writer likewise
preserves headers. Failures are logged and produce a generic 500, or bodyless 500 if error
serialization also fails. Transport I/O is not retried. Responses use bare
`application/vnd.api+json` and merge `Vary: Accept`, even for 406 errors.

Explicit Gradle dependencies, compilation, behavioral tests and existing CI verify this scope.
No custom dependency or class-file scanners are used. The adapter exposes no Jackson-specific API.

## Compatibility

Only WebMVC opts into the publication convention. The example remains unpublished;
its Jackson dependencies never enter publication metadata or release SBOMs.
[ADR 0006](adr/0006-independent-spring-release-lifecycle.md) records independent
version semantics, signed publication and the explicit no-BOM decision.

The supported Spring line is 7.0, with a 7.0.0 compatibility floor, Java 21 and Servlet 6.1.
Use the current patched Spring release in applications; the floor describes API compatibility.
Production compilation stays at the floor. Standard tests select minimum or current stable
7.0 (initially 7.0.9) with `springTestVersion`; both run in the CI matrix. Current versions
are test-only. Spring 6.2 and Spring 7.1 milestones are not supported targets.

The consumer example separately uses the catalog's current Spring patch for both compilation
and tests. CI's minimum leg runs the full build, including that fixed-current consumer;
the current leg runs only the adapter build. This avoids multiplying consumer tests by
the adapter's compatibility dimension. The root build still runs all modules locally.
