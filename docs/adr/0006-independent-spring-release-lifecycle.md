# ADR 0006: Independent Spring release lifecycle

Status: Accepted

Date: 2026-10-16

## Context

Spring needs its own release lifecycle without publishing application examples or
coupling version numbers to Core.

## Decision

Use one root version, starting at 0.1.0, for the Spring product. Only
`com.kazforge:jsonapi-java-spring-webmvc` is published; the Jackson 3 example is not.
Core API/query 0.4.0 is the supported dependency baseline, not Spring's release number.

Conventional Commits drive releases: `feat` increments minor; `fix`, `perf` and
non-breaking `revert` increment patch. Internal-only changes do not trigger releases.
Breaking changes (`!` or `BREAKING CHANGE:`) increment minor before 1.0 and major
thereafter, and must appear explicitly in release notes, never in a patch release.

Adapt Core's repository-local release-please, signing, SBOM/provenance and Central
publication flow. No snapshot PRs or shared automation platform are introduced.

Do not publish a dependency-management BOM for one consumable Spring module.
Reconsider only when multiple modules create a real consumer version-alignment need.
A CycloneDX SBOM is supply-chain evidence, not a dependency-management BOM.

## Consequences

Spring releases independently with signed artifacts and supply-chain evidence.
Maintainers review release PRs and verify publication; automation does not establish
feature readiness. Published versions are immutable.
