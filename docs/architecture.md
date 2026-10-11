# Architecture

This document describes the current repository and its accepted architectural
constraints. [ADRs](adr/README.md) explain the decisions behind those constraints.

## Current implementation state

The repository currently contains a build/quality baseline only. There are no
registered production modules, Spring runtime APIs, or declared Spring Framework
compatibility versions. The first planned production module is
`jsonapi-java-spring-webmvc`; the adapter boundaries below are accepted constraints,
not claims that its behavior is already implemented.

## Product and dependency boundary

`jsonapi-java-spring` is a separate companion product to
[jsonapi-java](https://github.com/kazforge/jsonapi-java). Core owns framework-neutral
JSON:API semantics and runtimes; this repository owns Spring-specific integration.
The products have independent versions and release trains. An independent release
boundary does not imply that Spring publication automation or published artifacts
already exist here.

Spring production modules must consume supported, released public core artifacts
from Maven Central as external consumers. They must not depend on a sibling source
checkout, core project dependencies, local composite builds, `mavenLocal()`, source
substitution, or unsupported/internal core packages. The local `build-logic` included
build is build tooling, not a core source dependency.

See [ADR 0001](adr/0001-separate-spring-companion-product.md).

## WebMVC adapter and runtime boundary

The planned WebMVC adapter integrates a caller-supplied public
`com.kazforge.jsonapi.api.JsonApi` runtime. Applications choose and configure the
Jackson 2 or Jackson 3 implementation; the adapter must not detect a Jackson major
or own an `ObjectMapper`/`JsonMapper` abstraction.

The adapter depends only on required public neutral core artifacts, primarily
`jsonapi-java-api` and public query/core contracts where justified. Jackson-major
imports (`com.fasterxml.jackson.*`, `tools.jackson.*`), core Jackson-specific adapter
packages, and core internal packages are outside this boundary.

Spring owns HTTP/framework adaptation, not JSON:API mapping semantics, persistence,
authorization, transactions, or query execution. This is a responsibility boundary,
not a description of currently available endpoint features.

## Compatibility contract

Spring Framework is the direct compatibility contract. Spring Boot is not a
production dependency of the WebMVC adapter, and no Boot compatibility is currently
claimed. Jackson-major neutrality must hold independently of the application runtime.

The first module implementation must select and document a concrete minimum Spring
Framework baseline, supported core baseline, and currently verified Spring lines.
CI must verify the minimum and current supported lines without publishing a newer
test-only Framework version as the consumer minimum. No versions are selected by
this documentation change. Future consumer compatibility details have one canonical
owner in the Spring documentation; this document and ADRs should link to that source
rather than duplicate version tables.

See [ADR 0002](adr/0002-neutral-webmvc-adapter-boundary.md).

## Repository and module ownership

| Location | Current responsibility |
| --- | --- |
| `settings.gradle.kts` | Module membership and dependency repository policy; no production modules are included yet. |
| Root build | Quality/build orchestration and Sonar configuration; no production sources. |
| `build-logic` | Reusable formatting and Java library quality conventions. |
| `gradle/libs.versions.toml` | Shared build/dependency versions. |
| `.github` | Repository CI and quality-gate automation. |
| `docs` | Current maintainer architecture and decision records. |

`jsonapi-java-spring-webmvc` is the first planned adapter and should initially own
its own integration code. Do not create a speculative common/`spring-core` module;
extract shared responsibilities only after multiple real adapters demonstrate a
stable need. Boot and WebFlux modules must likewise wait for concrete implemented
requirements, not be added for structural symmetry.

## Maintenance

An implementation PR that changes durable architecture, supported compatibility,
module ownership, release/product boundaries, or another repository-level contract
must update the relevant current-state documentation in the same PR. Create or
supersede an ADR when the decision warrants a durable rationale. Routine fixes do
not each require an ADR. See [Contributing](../CONTRIBUTING.md#repository-documentation).
