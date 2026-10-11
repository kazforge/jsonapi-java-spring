# ADR 0001: Separate Spring companion product

Status: Accepted

Date: 2026-10-11

## Context

The released `jsonapi-java` library owns framework-neutral JSON:API contracts and
runtimes. Spring integration has framework compatibility and delivery concerns
that should not determine core's module structure or release schedule.

## Decision

Keep Spring integration in the separate `jsonapi-java-spring` companion repository
with independent versions and a release train separate from core.

Spring modules consume supported, released public core artifacts from Maven Central
as external consumers. Do not use core project dependencies, sibling checkouts,
local core composite builds, `mavenLocal()`, source substitution, or core internal
packages. Repository-local build tooling is not part of this runtime boundary.

The repository split is already established. This decision does not claim that
Spring runtime modules, publication automation, or public Spring artifacts exist yet.

## Consequences

Core remains framework-neutral, and Spring can evolve without lockstep core releases.
Spring must own and verify its supported core and framework compatibility rather than
rely on source-level coupling or matching version numbers. Changes needing new core
contracts require a public core release before Spring consumes them.

The companion repository owns its build, documentation, and release lifecycle;
release automation details are separate work, not prescribed by this decision.
