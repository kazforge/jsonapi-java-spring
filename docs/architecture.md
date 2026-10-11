# Architecture

`jsonapi-java-spring` is a separate companion product to
[jsonapi-java](https://github.com/kazforge/jsonapi-java). Core owns framework-neutral
JSON:API semantics and runtimes; this repository owns Spring-specific integration.

The repository currently provides the build baseline; production modules are not yet
present. [ADRs](adr/README.md) record the product and WebMVC runtime decisions.

## Repository structure

| Location | Current responsibility |
| --- | --- |
| `settings.gradle.kts` | Module membership and dependency repositories. |
| Root build | Build/quality orchestration and Sonar configuration. |
| `build-logic` | Reusable formatting and Java library quality conventions. |
| `gradle/libs.versions.toml` | Shared build/dependency versions. |
| `.github` | Repository CI and quality-gate automation. |
| `docs` | Architecture and decision records. |

## Dependency boundary

Dependencies resolve from Maven Central; the Plugin Portal also supplies build tooling.
`build-logic` is a local included build for conventions, not a runtime module.
Spring integration's core dependency boundary is released public neutral APIs, not
core source or internal packages.
