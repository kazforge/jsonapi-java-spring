# ADR 0004: Operation-aware request and query context

Status: Accepted

Date: 2026-10-12

## Context

Later resource, PATCH, and relationship bindings need request-local operation information
and representation selection. HTTP methods alone cannot distinguish resource creation from
relationship additions. The released neutral query module already owns query syntax and values.

## Decision

Expose an immutable servlet-request context containing Spring's HTTP method, neutral
`PrimaryDataContext`, and neutral `JsonApiQuery`. The existing endpoint marker selects the
role, defaulting to resource; method metadata replaces controller metadata. Related-resource
URLs remain resource endpoints. Prepare context after media negotiation and inject it through
a dedicated argument resolver; downstream adapters can retrieve the same request attribute.

Delegate the raw servlet query string to `JsonApiQueryParser.parseRaw`, normalizing absence to
empty. This avoids merging form/body parameters and preserves wire occurrence order without
adapter-owned decoding. Cache query results by raw input on the request, including across async
redispatch, but refresh operation metadata per handler. No thread-local or mutable runtime
configuration carries request state.

Reuse the parser's `RepresentationSelection`, opaque page/filter/custom values, and diagnostics.
Do not introduce Spring query models, persistence semantics, endpoint support policy, body
binding, or error rendering. Public neutral artifacts remain the dependency boundary.

## Consequences

Applications register the interceptor and resolver explicitly, choose endpoint roles, and own
query execution and support/authorization policy. Future binding can distinguish operation
families without misusing resource validation on linkage endpoints. Query failures preserve
diagnostic and parameter attribution for later safe protocol error translation. This decision
extends ADR 0002 without changing Jackson neutrality or the compatibility baseline.
