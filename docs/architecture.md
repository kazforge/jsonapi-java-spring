# Architecture

`jsonapi-java-spring` is a separate companion product to
[jsonapi-java](https://github.com/kazforge/jsonapi-java). Core owns framework-neutral
JSON:API semantics and runtimes; this repository owns Spring-specific integration.

`jsonapi-java-spring-webmvc` establishes the module and dependency baseline, with no
runtime behavior or public integration API. [ADRs](adr/README.md) record durable decisions.

## Repository structure

| Location | Current responsibility |
| --- | --- |
| `settings.gradle.kts` | Module membership and dependency repositories. |
| Root build | Build/quality orchestration and Sonar configuration. |
| `build-logic` | Reusable formatting and Java library quality conventions. |
| `jsonapi-java-spring-webmvc` | Neutral WebMVC module and Spring-version baseline. |
| `gradle/libs.versions.toml` | Shared build/dependency versions. |
| `.github` | Repository CI and quality-gate automation. |
| `docs` | Architecture and decision records. |

## Dependency boundary

Dependencies resolve from Maven Central; the Plugin Portal also supplies build tooling.
`build-logic` is a local included build for conventions, not a runtime module.
Spring integration's core dependency boundary is released public neutral APIs, not
core source or internal packages.

The WebMVC module exposes `jsonapi-java-api:0.2.0`, Spring WebMVC, and the Servlet 6.1 API
as API dependencies.
Production and test dependencies contain no Jackson runtime; architecture boundary tests
arrive with the WebMVC implementation. Publication configuration and real consumer
integration tests are separate work.

## Compatibility

The supported Spring line is 7.0, with a 7.0.0 compatibility floor, Java 21 and Servlet 6.1.
Use the current patched Spring release in applications; the floor describes API compatibility.
Production compilation stays at the floor. Standard tests select minimum or current stable
7.0 (initially 7.0.9) with `springTestVersion`; both run in the CI matrix. Current versions
are test-only. Spring 6.2 and Spring 7.1 milestones are not supported targets.
