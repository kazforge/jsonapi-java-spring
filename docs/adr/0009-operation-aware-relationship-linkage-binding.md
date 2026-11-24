# ADR 0009: Operation-aware relationship linkage binding

Status: Accepted

Date: 2026-11-24

## Context

Relationship URLs operate on identifiers, not resource DTOs. HTTP method and controller
cardinality must select replacement, addition, or removal semantics independently of JSON
shape. Spring treats a null converter result as a missing required body and skips converters
for ordinary null responses. Passing Java null through the conventional body pipeline would
therefore conflate explicit linkage clearing with missing input or bodyless output.

## Decision

Add a separate `JsonApiRelationshipHttpMessageConverter` gated by the existing
`@JsonApiEndpoint(primaryDataContext = RELATIONSHIP)` dispatch context. Keep resource create,
resource PATCH, related-resource URLs, and ordinary MVC unchanged.

Use `JsonApiToOneRelationship(ResourceIdentifier identifier)` with a nullable component as
the explicit to-one PATCH contract and as the minimal opt-in for null linkage responses.
The wrapper itself is non-null, so the standard required-body check still applies. Non-null
to-one responses can return `ResourceIdentifier` directly. To-many requests and responses
use fully resolved `List<ResourceIdentifier>` declarations; PATCH replaces, POST adds, and
DELETE removes. Other request target/method combinations are configuration failures.
Do not introduce an any-cardinality body model, a new annotation, or a second endpoint framework.

Require released Core 0.5.1 from Maven Central. Delegate once to the neutral relationship
facet, which rejects absent data, wrong cardinality, invalid identifiers, and resource-only
members without a Spring JSON parser. Do not use resource create/update validation usages
or extend source-aware resource DTO validation into relationship operations. Preserve
Core identifier metadata and identity rules; Spring does not resolve identifiers.

Serialize minimal linkage through the relationship facet and buffer before writing.
Preserve application-owned HTTP status and headers, ordinary null returns, and bodyless 204s.
Reuse existing media negotiation, query context, and safe protocol/application error handling.
Client shape failures produce 400; endpoint/runtime and response failures remain server errors.

Conventional binding cannot hydrate requested includes. Reject nonempty include paths through
the existing query error pipeline, before reading mutation bodies or writing linkage.
Applications retain support policy and may select the existing explicit document transport for
advanced responses containing links, meta, or included resources. Public guide additions belong
to the feature's software release, not a revision of a previous release.

## Consequences

Controllers retain ordinary `@RequestBody` and `ResponseEntity` processing with one small
nullable-linkage value. A supplied clear is distinguishable from an absent required body.
No Jackson-major coupling, DTO registry, persistence mutation, route discovery, or client/Atomic
Operations API is introduced. Applications remain responsible for authorization, related types,
existence, mutation semantics, query execution, and response status.
