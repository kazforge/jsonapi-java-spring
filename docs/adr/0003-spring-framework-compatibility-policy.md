# ADR 0003: Spring Framework compatibility policy

Status: Accepted

Date: 2026-10-11

## Context

The first WebMVC module needs a sustainable framework compatibility contract independent
of its compatibility-test versions. Spring 7.0 is the current production line; Spring 6.2
open-source support ended in June 2026, and Spring 7.1 is not yet generally available.

## Decision

Support Spring Framework 7.0 with a 7.0.9 minimum (revised 2026-10-17 before the first
public release), Java 21 and Servlet 6.1. Use the same minimum for production compilation
and the published WebMVC API dependency; test minimum and current stable 7.0 patches
without exporting current-test dependencies to consumers. Updating the floor or supported
lines requires an explicit
maintainer decision; current-test patch updates may be automated.

## Consequences

One neutral artifact supports the declared line without exporting the compatibility-test
version as a consumer requirement. The 7.0.9 minimum replaces the original 7.0.0 floor to
include security fixes available before the first release. Applications should still use
current patched Spring releases, not treat the minimum as an ongoing security guarantee.
Spring 6.2 support is not promised.
Future stable Spring lines require an explicit compatibility evaluation before inclusion.
