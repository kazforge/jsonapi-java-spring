# Architecture

`jsonapi-java-spring` is a separate companion product to
[jsonapi-java](https://github.com/kazforge/jsonapi-java). Core owns framework-neutral
JSON:API semantics and runtimes; this repository owns Spring-specific integration.

`jsonapi-java-spring-webmvc` owns JSON:API HTTP adaptation: it provides an opt-in
`@JsonApiEndpoint` marker whose handlers get JSON:API 1.1 request and response media
negotiation, operation-aware request/query context, and safe protocol and body-validation errors,
without affecting ordinary Spring MVC endpoints. [ADRs](adr/README.md) record durable decisions.

## Repository structure

| Location | Current responsibility |
| --- | --- |
| `settings.gradle.kts` | Module membership and dependency repositories. |
| Root build | Build/quality orchestration and Sonar configuration. |
| `build-logic` | Reusable formatting and Java library quality conventions. |
| `jsonapi-java-spring-webmvc` | Neutral WebMVC integration: media negotiation, request/query context, resource DTO, presence-aware PATCH and relationship-linkage binding, validation/protocol/application errors, and explicit document responses. |
| `jsonapi-java-spring-boot-starter` | Boot servlet auto-configuration, Jackson 3 runtime and validation provider. |
| `examples/boot-webmvc` | Unpublished runnable Boot application and embedded HTTP integration tests. |
| `examples/jackson3-webmvc` | Non-published application wiring and real Jackson 3 consumer smoke tests. |
| `gradle/libs.versions.toml` | Shared build/dependency versions. |
| `.github` | Repository CI and quality-gate automation. |
| `docs/release.md` | Independent versioning and signed Central publication. |
| `docs` | Architecture and decision records. |

## Dependency boundary

Dependencies resolve from Maven Central; the Plugin Portal also supplies build tooling.
`build-logic` is a local included build for conventions, not a runtime module.
Spring integration's core dependency boundary is released public neutral APIs, not
core source or internal packages.

The WebMVC module exposes `jsonapi-java-api:0.5.1`, `jsonapi-java-query:0.5.1`, Spring WebMVC,
the Servlet 6.1 API, and the Jakarta Validation 3.1 API. Core 0.5.1 is the supported Core baseline.
The adapter's production and test dependencies contain no Jackson runtime. A Jakarta Validation
provider is used in tests and in the separate non-published consumer example, never in the adapter
runtime. The example uses `jsonapi-java-jackson3:0.5.1` from Maven Central and supplies the neutral
`JsonApi` runtime and the validator. No source substitution or local Core build is used.

## Request boundary

`@JsonApiEndpoint` stays in `com.kazforge.jsonapi.spring.webmvc`; media negotiation lives
in `.media`, request context in `.request`, error translation in `.error`, explicit document
response transport in `.document`, and the PATCH value extractor in `.validation`. Production
packages are null-marked.

Boot-managed MVC gets the following wiring from the starter. With standalone MVC,
applications register `JsonApiMediaNegotiationInterceptor` before
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
`resources().readCreateWithSources`. DTO responses use `writeOne`; typed collections use `writeMany`,
with the current request's representation selection passed per operation. `JsonApiResponse<T>` adds
optional top-level meta, links, or an explicit JSON:API object without replacing the application
value. Unwrapped responses remain ordinary resource documents. Relationship endpoints and ordinary
unmarked handlers are not claimed. Applications retain ownership of persistence, query execution,
authorization, route identity, and domain validation policy. Resource and collection values are
non-null; plain null or bodyless returns remain Spring bodyless responses.

For resource PATCH, the same converter binds `@RequestBody` typed PATCH DTOs through
`JsonApi.patches().bindPatchWithSources`, or an explicit `PatchCommand<T>` through `bindCommand`. One
neutral document read performs update validation using application-supplied, request-scoped endpoint
identity before binding. Declare `@JsonApiPatchTarget` on the matched method or supply a
trusted identity callback. Missing identity fails closed. Core owns presence and PATCH semantics; Spring owns
type resolution and HTTP adaptation. See [ADR 0007](adr/0007-presence-aware-resource-patch-binding.md)
for the decision.

The converter returns the DTO from Core directly to Spring, so controllers keep plain
`@Valid @RequestBody` parameters. The source snapshot belongs to the current dispatch and is
usable only for the same DTO instance. Redispatch does not inherit it. Custom runtimes must
support the source-aware operations for conventional create and typed PATCH; a failed or
unsupported operation does not fall back to another read or bind.

For `RELATIONSHIP` endpoints, applications register `JsonApiRelationshipHttpMessageConverter`
ahead of Spring defaults. Its controller signature determines cardinality, never the incoming JSON.
To-one PATCH uses `@RequestBody JsonApiToOneRelationship`, a non-null value containing an identifier
or null for a clear operation. This preserves Spring's required-body check: explicit `data:null`
is a supplied value, while a missing body fails before invocation. To-many PATCH replacement,
POST additions, and DELETE removals use `@RequestBody List<ResourceIdentifier>`. Empty lists
remain empty. Raw, wildcard, unresolved, other target types, and unsupported method/cardinality
combinations fail as endpoint configuration errors, not client errors.

Each body is read once through `JsonApi.relationships().readToOne` or `readToMany`. The released
Core runtime distinguishes absent data from explicit null, enforces identifier shape and validity,
and rejects resource-only identifier members. No resource-create/update usage or endpoint-identity
callback is involved. Core preserves identifier `id`, supported `lid`, and identifier meta.
Neither this path nor its errors participate in source-aware resource DTO validation.

Conventional linkage responses use `ResourceIdentifier`, `List<ResourceIdentifier>`, or
`JsonApiToOneRelationship` for explicit null linkage. The converter delegates to the same neutral
relationship facet for minimal linkage documents. Ordinary null remains bodyless, as do
application-owned 204 responses. Status and headers stay application-owned. Invalid request
documents and cardinality mismatches reuse the safe 400 resolver; runtime/configuration and
response failures remain server errors. See [ADR 0009](adr/0009-operation-aware-relationship-linkage-binding.md).

The linkage-only path cannot fulfill nonempty requested `include` paths and rejects them with
the existing query diagnostic/error pipeline, before mutation binding or response serialization.
Applications retain query support policy; advanced relationship responses use explicit document
transport for links, meta, or included resources. That transport is not subject to the conventional
converter's include restriction. There is no automatic DTO hydration or adapter-owned mutation.

Only the raw servlet query string is passed to the public query parser, with an absent string
treated as empty. The parser owns decoding, syntax, ordering, immutable snapshots, and diagnostic
attribution. Servlet body/form parameters are not merged. Parsed query state is cached on the
request by raw query input for redispatch; operation metadata is refreshed for each handler.
Ordinary handlers clear the dispatch context. Representation selection is the query's existing
neutral value, not a separate Spring model or effective authorization decision.

Controllers can read opaque filter, page, and custom values from `JsonApiRequestContext.query()`
or accept a typed query DTO. Register `JsonApiQueryParamsArgumentResolver` and annotate the
controller parameter with `@JsonApiQueryParams`. Bind exact decoded names using Spring's
`@BindParam` on record components or the parameters of a single public constructor. Only those
inputs are converted; no setter or nested property path is bound. An optional unannotated
`JsonApiQuery` constructor parameter receives the original sort and representation selection.
`@Valid` uses the application's MVC validator. Query violations identify supplied URL parameters
where known, never body pointers. See [ADR 0011](adr/0011-explicit-constructor-query-binding.md)
for cardinality and failure behavior.

Applications define what filters mean, how to paginate results, and which links and meta to
return. There is no shared filter or pagination model. See [ADR 0010](adr/0010-keep-filter-and-page-semantics-application-owned.md).
Applications own query execution, endpoint support policy, authorization, and route identity.
Parser exceptions retain their neutral diagnostics; the protocol resolver translates them
without treating exception messages as public contracts.

## Protocol errors and document transport

Register `JsonApiProtocolExceptionResolver` with a caller-supplied `JsonApi` runtime before
Spring's default status handling; application handlers may precede it. It checks the selected
handler's endpoint marker because request context may not exist when parsing fails. Negotiation
uses Spring's typed media exceptions, retaining default status handling without this resolver.

Query, neutral document-read, known resource-input, and `MethodArgumentNotValidException` failures
produce 400 responses; endpoint identity conflicts produce 409 and negotiation produces 406/415.
Unknown supplied PATCH members are client-input failures; invalid PATCH DTO declarations remain server errors.
Errors reuse core `ErrorObject`, `ErrorSource`, and
`JsonApiDocument` values with fixed titles and diagnostic codes. Exception details and unverified
document pointers are omitted. Unrelated mapping, configuration, and application exceptions are not
classified as client errors.

### Body validation

Standalone applications choose and configure their Jakarta Validation provider. Explicitly register
`PatchPresenceValueExtractor` with that provider for PATCH type-use constraints and cascading:
omitted wrappers emit no value, while supplied values (including explicit null) are validated.
The Boot starter supplies the same extractor through the Jakarta service loader. Class
constraints can inspect presence. This rule does not exempt missing children of a supplied
atomic replacement, and `included` resources are not independently validated as beans.

For a `MethodArgumentNotValidException` on the matched create or typed PATCH body, the resolver
emits one error per violation or binding error: `INVALID_FIELD` / “Invalid field value” or
`INVALID_RESOURCE` / “Invalid resource”. It never includes Spring error codes, messages, or
rejected values. Generated validation errors omit the optional per-error `status`; the default
resolver still sends HTTP 400. The resolver prefers Jakarta path nodes and consults Core's source
snapshot for exact document-relative pointers. Unknown paths may stop at an existing mapped
ancestor; absent fields do not get guessed pointers. Root object errors can address `/data`.
Relationships address linkage, not `included`. Other validation failures on opted-in handlers
retain the generic `INVALID_REQUEST` error. Unmarked MVC and controller-owned `BindingResult`
handling are unchanged.

### Application errors

Controllers and application-owned exception advice return `ResponseEntity<JsonApiErrorResponse>`
(or `@ResponseStatus` with `JsonApiErrorResponse`) containing Core `ErrorObject` values and an
optional `DocumentEnvelope`. `JsonApiErrorHttpMessageConverter` checks the originally matched
`@JsonApiEndpoint` handler—covering both resource and relationship endpoints without requiring an
annotated advice class or parsed query context—and delegates to `JsonApiDocumentResponseWriter`.
Spring owns the HTTP status and headers; error object `status` members are neither inferred nor
rewritten. See [ADR 0008](adr/0008-explicit-webmvc-application-error-responses.md).
To override the default body-validation response (for example, with HTTP 422), place Spring's
`@ExceptionHandler` resolver before `JsonApiProtocolExceptionResolver` and the protocol resolver
before Spring's remaining defaults. Scope advice to the application's endpoints. Use
`JsonApiErrorResponse.fromValidation(request, exception)` to reuse the same safe attributed body
with a different status in `ResponseEntity`. If the factory returns empty, advice can rethrow
the original exception to let the remaining resolvers handle it.

### Shared document transport

`JsonApiDocumentResponseWriter` accepts a neutral document, explicit HTTP status and servlet
response for advanced and error responses. Raw reads require an explicit `DocumentReadContext`
through the runtime. Serialization delegates to `JsonApi.documents()` for documents,
`JsonApi.relationships()` for linkage and `JsonApi.resources()` for conventional DTOs, so Core's
resource-write version default applies only to resource responses while explicit document
envelopes are written as supplied.

All response paths serialize fully before writing. The resource and linkage converters share
buffered output handling, skip committed responses and reset uncommitted output mode while
restoring status and headers; the advanced writer likewise
preserves headers. A resource GET/HEAD with a denied or unknown requested include returns
400 with `source.parameter=include`. This write-time check is not pre-mutation validation.
Other serialization failures are logged and produce a generic 500, or bodyless 500 if error
serialization also fails. Transport I/O is not retried. Responses use bare
`application/vnd.api+json` and merge `Vary: Accept`, even for 406 errors.

Explicit Gradle dependencies, compilation, behavioral tests and existing CI verify this scope.
No custom dependency or class-file scanners are used. The adapter exposes no Jackson-specific API.

## Compatibility

WebMVC and the Boot starter opt into the publication convention. Examples remain unpublished.
The starter supports Boot 4.1.x from 4.1.2, using Boot's managed Jackson 3 rather than
pinning a second version. See [ADR 0012](adr/0012-boot-managed-webmvc-starter.md)
for registration, validator and runtime backoff, and application-owned policy.
[ADR 0006](adr/0006-independent-spring-release-lifecycle.md) records independent
version semantics, signed publication and the explicit no-BOM decision.

The supported Spring line is 7.0, with a 7.0.9 minimum, Java 21 and Servlet 6.1.
Production compilation and the published WebMVC API dependency use this same minimum.
Applications should use the latest patched 7.0.x release; the supported minimum is not a
promise of ongoing security fixes. Standard tests select minimum or current stable 7.0
with `springTestVersion`; both run in the CI matrix.
Only the current test target advances with newer supported patches; it does not raise the
published dependency baseline. Spring 6.2 and Spring 7.1 milestones are not supported targets.

The standalone consumer example separately uses the catalog's current Spring patch.
CI's minimum leg runs the full build. Its current leg tests the adapter on the current
Framework patch and the Boot example on the current supported Boot patch.
