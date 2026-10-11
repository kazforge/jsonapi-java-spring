# ADR 0002: Neutral WebMVC adapter boundary

Status: Accepted

Date: 2026-10-11

## Context

Core exposes a public backend-neutral `JsonApi` contract implemented by Jackson 2
and Jackson 3 runtimes. Spring HTTP integration does not need to own JSON processing
or select the application's runtime.

## Decision

The WebMVC adapter accepts a caller-supplied `com.kazforge.jsonapi.api.JsonApi`
runtime and remains Jackson-major-neutral. It owns HTTP/framework adaptation; core
owns JSON:API semantics. Its direct framework contract is Spring Framework, not Boot.

## Consequences

Applications choose and configure their Jackson runtime. The adapter couples to public
neutral core contracts rather than a Jackson implementation, allowing the same adapter
to serve either major without duplicating core behavior or requiring Boot.
