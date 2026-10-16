# ADR 0006: Independent Spring release lifecycle

Status: Accepted

Date: 2026-10-16

## Context

Spring integration needs repeatable publication without coupling its release number
to Core or publishing application examples.

## Decision

The Spring product has one repository-owned version in `gradle.properties`, starting
at 0.1.0. Its only published artifact is `com.kazforge:jsonapi-java-spring-webmvc`.
The application example and build conventions are not published. Core 0.4.0 remains
the supported neutral API/query baseline, not the Spring release number.

Use Conventional Commits: `feat` increments minor; `fix`, `perf`, and non-breaking
`revert` increment patch. Internal-only docs, build, CI, test, refactoring and chore
changes do not independently trigger a release. A `!` header or `BREAKING CHANGE:`
footer increments minor before 1.0 and major thereafter. Breaking changes must be
explicit in release notes and never ship in a patch. Use conventional squash titles
or conventional individual commits when rebasing; review the resulting changelog.

release-please owns release PRs, the changelog, manifest, root version and `v<version>`
tags. Merging a release PR is the release decision. Do not publish snapshots or
automatically create snapshot PRs. Published versions are immutable.

There is no dependency-management BOM: one independently consumable Spring module
does not create an alignment problem. Reconsider only when multiple Spring modules
create a demonstrated consumer alignment need. A CycloneDX supply-chain SBOM is
evidence, not a dependency-management BOM artifact.

Keep product configuration and orchestration in this repository. Sign publications
with OpenPGP, publish a production-dependency SBOM, and attest primary JAR provenance
and SBOM association using GitHub OIDC. Central validation rehearsals must use
user-managed deployments and drop them without publication.

## Consequences

Spring can release independently while retaining an explicit supported Core baseline.
Maintainers own credentials, release approval, deployment recovery and public
availability verification. Automation is not evidence of feature readiness.
