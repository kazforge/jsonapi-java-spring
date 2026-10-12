# Architecture

`jsonapi-java-spring` is a separate companion product to
[jsonapi-java](https://github.com/kazforge/jsonapi-java). Core owns framework-neutral
JSON:API semantics and runtimes; this repository owns Spring-specific integration.

`jsonapi-java-spring-webmvc` owns JSON:API HTTP adaptation: it provides an opt-in
`@JsonApiEndpoint` marker whose handlers get JSON:API 1.1 request and response media
negotiation, operation-aware request/query context, and safe protocol error translation, without affecting ordinary Spring MVC
endpoints. [ADRs](adr/README.md) record
durable decisions.

## Repository structure

| Location | Current responsibility |
| --- | --- |
| `settings.gradle.kts` | Module membership and dependency repositories. |
| Root build | Build/quality orchestration and Sonar configuration. |
| `build-logic` | Reusable formatting and Java library quality conventions. |
| `jsonapi-java-spring-webmvc` | Neutral WebMVC foundation: media negotiation, request/query context, protocol errors, and explicit document responses. |
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
in `.media`, request context in `.request`, protocol translation in `.error`, and explicit
document response transport in `.document`. Production packages are null-marked.

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
policy, authorization, and route identity. Parser exceptions retain their neutral diagnostics;
the protocol resolver translates them without treating exception messages as public contracts.

## Protocol errors and advanced document transport

Register `JsonApiProtocolExceptionResolver` with the application's configured neutral `JsonApi`
runtime before Spring's default exception resolvers. Application exception handlers may precede
it. The resolver checks the selected handler's endpoint marker, not the request context, which
may not exist when parsing fails. Negotiation rejects through Spring's typed media exceptions.
Without the protocol resolver, Spring's normal status handling remains available.

Query and neutral document-read failures produce 400 responses; negotiation produces 406/415.
Errors use public core `ErrorObject`, `ErrorSource`, and `JsonApiDocument` values. Diagnostic codes
are stable enum names; titles are fixed. Query parameter names are retained when decoded, and
media failures identify the header. Messages, rejected values, causes, class names and source
positions are not emitted. Document pointers are omitted rather than claiming a missing or
unverified member exists. Bare mapping/core-validation and unrelated application exceptions are
not globally classified as client failures.

`JsonApiDocumentResponseWriter` is the explicit advanced response escape hatch: it accepts a
neutral document, an explicit HTTP status and a servlet response. It is not a conventional
controller envelope or DTO converter. Raw reads remain explicit runtime operations with a
`DocumentReadContext`; there is no implicit any-document request binder. Both protocol errors
and advanced responses delegate serialization to `JsonApi.documents()`, preserving runtime
document defaults. No Spring-owned serializer or runtime discovery is introduced.

Serialization completes before response mutation. Runtime serialization failures produce a
generic 500 error; if that also cannot serialize, the result is bodyless 500. Failures are logged
server-side. Before emitting uncommitted output, the writer resets the servlet output mode while
preserving existing headers; a previously selected non-UTF-8 writer cannot recode UTF-8 bytes.
Transport I/O is never retried; committed responses are not rewritten. Responses
use bare `application/vnd.api+json` and merge `Vary: Accept`, including error bodies on 406 even
when the request rejected that representation. Ordinary application/domain error conveniences,
validation mapping and resource binding remain separate future surfaces.

Compiled-class architecture tests reject Jackson, Boot, internal packages and unsupported core
helpers. The build checks production/test dependency graphs for major-specific dependencies and
non-released project substitution. Only public model values and the endpoint-role core contract
are currently used directly from core. The adapter exposes no Jackson-major-specific API.

## Compatibility

The supported Spring line is 7.0, with a 7.0.0 compatibility floor, Java 21 and Servlet 6.1.
Use the current patched Spring release in applications; the floor describes API compatibility.
Production compilation stays at the floor. Standard tests select minimum or current stable
7.0 (initially 7.0.9) with `springTestVersion`; both run in the CI matrix. Current versions
are test-only. Spring 6.2 and Spring 7.1 milestones are not supported targets.
