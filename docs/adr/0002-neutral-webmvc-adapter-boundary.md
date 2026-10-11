# ADR 0002: Neutral WebMVC adapter boundary

Status: Accepted

Date: 2026-10-11

## Context

Core exposes a public backend-neutral `JsonApi` contract implemented by Jackson 2
and Jackson 3 runtimes. A Spring adapter should not choose the application's runtime,
duplicate core semantics, or require Boot merely to integrate with Spring Framework.
There is not yet a production adapter or evidence of shared responsibilities across
multiple Spring adapters.

## Decision

Make `jsonapi-java-spring-webmvc` the first production adapter. Integrate a
caller-supplied `com.kazforge.jsonapi.api.JsonApi` runtime through required public
neutral core artifacts, primarily `jsonapi-java-api` and public query/core contracts
where justified.

Applications select and configure their runtime. The adapter must not import
`com.fasterxml.jackson.*`, `tools.jackson.*`, core Jackson-major adapter packages,
or core internal packages; detect the Jackson major; or own an
`ObjectMapper`/`JsonMapper` abstraction.

Target Spring Framework directly, with no Boot production dependency. Select concrete
minimum and currently supported Framework lines during module implementation; verify
them in CI without turning the newest test-only version into the consumer minimum.

Keep HTTP/framework adaptation in WebMVC and neutral semantics in core. Do not create
speculative common, Boot, or WebFlux modules. Extract common code only when multiple
real adapters establish a stable shared responsibility.

These are accepted implementation constraints, not claims of an available adapter,
tested compatibility versions, or implemented HTTP behavior.

## Consequences

One adapter can work with either application-selected runtime without owning Jackson
configuration. Applications retain explicit runtime wiring responsibilities. Public
dependency boundaries and neutrality need architecture checks when the module exists.

Spring compatibility is an explicit tested contract rather than an assumption derived
from Boot versions. Future modules require concrete needs; any extraction must update
current-state architecture and record a replacement decision when warranted.
