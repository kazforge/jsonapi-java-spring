# ADR 0001: Separate Spring companion product

Status: Accepted

Date: 2026-10-11

## Context

Spring integration has framework compatibility and release concerns distinct from
the framework-neutral `jsonapi-java` library.

## Decision

Keep Spring integration in the separate `jsonapi-java-spring` companion repository
with independent versions and a release train separate from core.

## Consequences

Core remains framework-neutral, and Spring can evolve without lockstep core releases.
The companion product owns its release lifecycle and compatibility with released core
versions; matching product version numbers do not imply compatibility.
