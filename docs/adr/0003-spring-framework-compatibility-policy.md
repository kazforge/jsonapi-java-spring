# ADR 0003: Spring Framework compatibility policy

Status: Accepted

Date: 2026-10-11

## Context

The first WebMVC module needs a sustainable framework compatibility contract independent
of its compatibility-test versions. Spring 7.0 is the current production line; Spring 6.2
open-source support ended in June 2026, and Spring 7.1 is not yet generally available.

## Decision

Support Spring Framework 7.0 with a fixed 7.0.0 API compatibility floor, Java 21 and Servlet
6.1. Keep production dependencies at the floor; test minimum and current stable 7.0 patches
without exporting current-test dependencies to consumers. Updating the floor or supported
lines requires an explicit
maintainer decision; current-test patch updates may be automated.

## Consequences

One neutral artifact supports the declared line without exporting the compatibility-test
version as a consumer requirement. Applications should use current patched Spring releases,
not interpret the compatibility floor as security guidance. Spring 6.2 support is not promised.
Future stable Spring lines require an explicit compatibility evaluation before inclusion.
