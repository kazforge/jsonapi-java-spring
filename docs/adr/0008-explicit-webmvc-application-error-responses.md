# ADR 0008: Explicit WebMVC application error responses

Status: Accepted

Date: 2026-11-11

## Context

Applications need to return domain errors from controllers and exception advice without assembling
raw JSON:API documents. Core already supplies complete error objects and document envelopes. Spring
already supplies explicit HTTP response status and headers, and the adapter has safe document
transport. A second error model, exception hierarchy or status inference policy is unnecessary.

## Decision

Use `ResponseEntity<JsonApiErrorResponse>` (or `@ResponseStatus` with `JsonApiErrorResponse`) as
the conventional error response. The immutable body holds Core `ErrorObject` values and a
`DocumentEnvelope`; Spring owns the HTTP status and headers. Supplied error `status` members are
preserved as-is and never inferred, populated or reconciled.

An explicitly registered, write-only `JsonApiErrorHttpMessageConverter` checks the originally
matched `@JsonApiEndpoint` handler, covering both resource and relationship endpoints as well as
unannotated exception advice without requiring a parsed request context. Ordinary unmarked handlers
are ignored, and `JsonApiResourceHttpMessageConverter` declines `JsonApiErrorResponse` regardless
of converter ordering.

The converter constructs a `JsonApiDocument` and delegates to ADR 0005's
`JsonApiDocumentResponseWriter`. Core's configured version default applies to resource writes only,
so absent error envelope members stay absent while explicit envelope members are written as
supplied.

## Consequences

Controllers, application exception advice and future validation handlers share one error-emission
path without coupling the adapter to Jackson or an application exception hierarchy. The public
addition is one record and one message converter, extending ADRs 0002 and 0005.
