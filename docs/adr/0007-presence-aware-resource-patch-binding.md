# ADR 0007: Presence-aware resource PATCH binding

Status: Accepted

Date: 2026-10-21

## Context

Core exposes backend-neutral binding for typed PATCH DTOs and explicit commands through
`JsonApiPatches`. Its direct `readPatch` and `readCommand` operations cannot accept per-request
endpoint identity. WebMVC resource updates must validate the document against the addressed
resource without configuring request state on the application-lifetime runtime or depending on
Jackson-specific readers.

## Decision

Extend the existing `JsonApiResourceHttpMessageConverter` for opted-in resource PATCH handlers.
The conventional controller parameter is a typed `@RequestBody` PATCH DTO whose patchable members
use `PatchPresence<T>`. An explicit, fully resolved `PatchCommand<T>` parameter selects the
infrastructure path. Raw, wildcard, unresolved, and bulk targets are configuration errors.

An application-owned constructor callback supplies Core's `EndpointIdentity` from the current
servlet request. Missing identity fails closed rather than silently disabling comparison.

Read once through `JsonApi.documents().read` using resource decoding and a request-local
`ValidationContext` selecting `UPDATE_REQUEST` and the expected endpoint identity. Then call
`JsonApi.patches().bindPatchWithSources` or `bindCommand` on that validated document, without
parsing or validating again. For typed PATCH, Spring receives the bound DTO directly; the
dispatch-scoped source snapshot supports error attribution as described in ADR 0005. Core owns
document and PATCH semantics; the existing protocol resolver owns HTTP error translation.

Applications can register the standard Jakarta `PatchPresenceValueExtractor` with their
validator. An omitted wrapper emits nothing; a present wrapper emits its value, even null.
Type-use constraints and cascading use the ordinary Bean Validation rules. Presence is not
validation policy: class constraints can inspect it, and children of supplied atomic
replacements still validate normally.

## Consequences

Spring's existing request-body processing and validation remain in use, with no additional argument
resolver or Jackson dependency. The original converter constructor still supports POST and responses;
PATCH requires identity configuration supplied by the application.

Typed presence, structured-value changes, and whole-linkage replacements retain Core contracts.
`included` participates in document validation but is not independently projected into PATCH DTOs
or commands. Neither binding path applies mutations, loads domain state, or authorizes requests.
Dedicated relationship-linkage mutations remain outside the resource binding path.
