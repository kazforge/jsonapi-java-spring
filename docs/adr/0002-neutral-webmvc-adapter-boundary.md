# ADR 0002: Neutral WebMVC adapter boundary

Status: Accepted

Date: 2026-10-11

## Context

Core exposes a public backend-neutral `JsonApi` contract implemented by its Jackson 3
runtime. Spring HTTP integration does not need to own JSON processing
or select the application's runtime.

## Decision

The WebMVC adapter accepts a caller-supplied `com.kazforge.jsonapi.api.JsonApi`
runtime and remains Jackson-major-neutral. It owns HTTP/framework adaptation; core
owns JSON:API semantics. Its direct framework contract is Spring Framework, not Boot.

## Consequences

Applications construct and configure the supported Jackson 3 runtime. The adapter couples
to public neutral Core contracts rather than a Jackson implementation, without duplicating
Core behavior or requiring Boot. The neutral boundary is not a promise of Jackson 2 support.
