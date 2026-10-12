# Architecture

`jsonapi-java-spring` is a separate companion product to
[jsonapi-java](https://github.com/kazforge/jsonapi-java). Core owns framework-neutral
JSON:API semantics and runtimes; this repository owns Spring-specific integration.

`jsonapi-java-spring-webmvc` owns JSON:API HTTP adaptation: it provides an opt-in
`@JsonApiEndpoint` marker whose handlers get JSON:API 1.1 request and response media
negotiation and operation-aware request/query context, without affecting ordinary Spring MVC
endpoints. [ADRs](adr/README.md) record
durable decisions.

## Repository structure

| Location | Current responsibility |
| --- | --- |
| `settings.gradle.kts` | Module membership and dependency repositories. |
| Root build | Build/quality orchestration and Sonar configuration. |
| `build-logic` | Reusable formatting and Java library quality conventions. |
| `jsonapi-java-spring-webmvc` | Neutral WebMVC adapter: opt-in media negotiation and request/query context. |
| `gradle/libs.versions.toml` | Shared build/dependency versions. |
| `.github` | Repository CI and quality-gate automation. |
| `docs` | Architecture and decision records. |

## Dependency boundary

Dependencies resolve from Maven Central; the Plugin Portal also supplies build tooling.
`build-logic` is a local included build for conventions, not a runtime module.
Spring integration's core dependency boundary is released public neutral APIs, not
core source or internal packages.

The WebMVC module exposes `jsonapi-java-api:0.2.0`, `jsonapi-java-query:0.2.0`, Spring WebMVC, and the Servlet 6.1 API
as API dependencies.
Production and test dependencies contain no Jackson runtime.

## Request boundary

`@JsonApiEndpoint` stays in `com.kazforge.jsonapi.spring.webmvc`; media negotiation lives
in `.media` and request context in `.request`. Both implementation packages are null-marked.

Applications register `JsonApiMediaNegotiationInterceptor` before
`JsonApiRequestContextInterceptor`, plus `JsonApiMediaNegotiationAdvice` as a bean and
`JsonApiRequestContextArgumentResolver` through `WebMvcConfigurer#addArgumentResolvers`.
Controllers can receive `JsonApiRequestContext`; later response handling can use its
request-attribute accessor. No request-scoped runtime configuration or thread-local is used.

Context carries the actual HTTP method, neutral `PrimaryDataContext`, and `JsonApiQuery`.
The endpoint annotation defaults to `RESOURCE`; a method annotation replaces controller
metadata, including defaults. Related-resource URLs are resource endpoints, whereas linkage
URLs select `RELATIONSHIP`. Relationship POST/PATCH/DELETE must not be treated as resource
create/update/delete solely from the verb. No body validation or binding occurs here.

Only the raw servlet query string is passed to the public query parser, with an absent string
treated as empty. The parser owns decoding, syntax, ordering, immutable snapshots, and diagnostic
attribution. Servlet body/form parameters are not merged. Parsed query state is cached on the
request by raw query input for redispatch; operation metadata is refreshed for each handler.
Ordinary handlers clear the dispatch context. Representation selection is the query's existing
neutral value, not a separate Spring model or effective authorization decision.

Page/filter/custom values remain opaque. Applications own query execution, endpoint support
policy, authorization, and route identity. Parser exceptions propagate unchanged; foundation
protocol error rendering remains follow-up work, not a default error response in this slice.

## Compatibility

The supported Spring line is 7.0, with a 7.0.0 compatibility floor, Java 21 and Servlet 6.1.
Use the current patched Spring release in applications; the floor describes API compatibility.
Production compilation stays at the floor. Standard tests select minimum or current stable
7.0 (initially 7.0.9) with `springTestVersion`; both run in the CI matrix. Current versions
are test-only. Spring 6.2 and Spring 7.1 milestones are not supported targets.
