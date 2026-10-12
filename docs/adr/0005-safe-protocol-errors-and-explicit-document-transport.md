# ADR 0005: Safe protocol errors and explicit document transport

Status: Accepted

Date: 2026-10-12

## Context

Negotiation and query preparation can fail before controller execution or request context
construction. A controller-only error advice cannot be the foundation's sole error boundary.
The neutral runtime already owns JSON serialization and public error/document values.

## Decision

Use an explicitly registered, endpoint-scoped `HandlerExceptionResolver` for typed media,
query and document-read failures. Use fixed titles and diagnostic codes, retaining decoded
query parameter attribution but never exception messages. Classify these as 406/415 or 400;
do not classify arbitrary mapping, validation or application failures as request errors.

Share a neutral document response writer with explicit advanced callers. It accepts a
caller-supplied `JsonApi` runtime and public `JsonApiDocument`, not a new Spring document model.
Raw request reads require explicit `DocumentReadContext` through the runtime; no generic raw
request binding is added. Conventional DTO binding and application error helpers remain separate.

Serialize before response mutation. Output failures at this serialization boundary are safe
500 responses, falling back to bodyless 500 if error serialization also fails. Log serialization
failures server-side; reset uncommitted output mode while preserving headers so previously selected
servlet writers cannot change the UTF-8 representation. Do not retry transport I/O or replace
committed output. JSON:API error responses use the JSON:API media type even for 406 and merge
`Vary: Accept`.

## Consequences

The completed public foundation comprises the endpoint marker, media interceptor/advice,
request context/interceptor/argument resolver, protocol resolver, and explicit document writer.
Applications register these seams explicitly and choose/configure their neutral runtime.
Runtime document defaults are preserved by delegation. Rely on explicit neutral Gradle dependencies,
compilation, behavioral tests and existing CI rather than custom boundary scanners or extra build
infrastructure. Spring 7.0.0 minimum, current-test 7.0.9, Java 21 and Servlet 6.1 remain unchanged.
This extends ADRs 0002 and 0004 without changing their request semantics.
