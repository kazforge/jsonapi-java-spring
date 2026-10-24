# Changelog

## [0.1.1](https://github.com/kazforge/jsonapi-java-spring/compare/v0.1.0...v0.1.1) (2026-10-24)


### Bug Fixes

* **deps:** update dependency com.google.errorprone:error_prone_core to v2.51.0 ([9468d2c](https://github.com/kazforge/jsonapi-java-spring/commit/9468d2c1bcadfefc5d8bc60933427f61a4897b36))
* **deps:** update dependency com.uber.nullaway:nullaway to v0.14.3 ([b1c464c](https://github.com/kazforge/jsonapi-java-spring/commit/b1c464c8006104411899ef64e0b1b646a1e8e552))
* **deps:** update dependency org.springframework:spring-framework-bom to v7.0.10 ([b1ae3cd](https://github.com/kazforge/jsonapi-java-spring/commit/b1ae3cd71aaf3ace03d9572f8f1df9bef11c30d1))

## 0.1.0 (2026-10-18)

First public release of **jsonapi-java-spring**, the opt-in JSON:API 1.1
WebMVC companion to [jsonapi-java](https://github.com/kazforge/jsonapi-java).

This is a pre-1.0 release intended for early real-world use and feedback.
The public API may evolve before 1.0; minor releases may introduce breaking
changes, while patch releases do not.

### Highlights

- **Opt-in JSON:API endpoints:** `@JsonApiEndpoint` adds JSON:API media
  negotiation, including 406/415 handling and `Vary: Accept`, without
  changing ordinary Spring MVC handlers.
- **Resource DTOs:** Bind JSON:API POST create requests, including resources
  without client-supplied IDs, and write single resources or collections
  through standard Spring MVC controllers.
- **Request and query context:** Access operation-aware request/query
  information and forward representation choices to Core. Applications
  remain responsible for executing queries and selecting data.
- **Protocol errors:** Translate known request, query and input failures
  into safe JSON:API error documents.
- **Jackson-neutral integration:** The WebMVC adapter accepts an
  application-provided `JsonApi` runtime. A tested Jackson 3 consumer
  demonstrates explicit wiring.

### Compatibility

- Java 21+, Spring Framework 7.0.x (minimum 7.0.9), Jakarta Servlet 6.1.
- Spring artifact: `com.kazforge:jsonapi-java-spring-webmvc:0.1.0`.
- Neutral Core API/query baseline: `0.4.0`, brought in transitively.
  Applications add their JSON runtime separately (verified with
  `jsonapi-java-jackson3:0.4.0`).
- Spring and Core have independent versions and release lifecycles.

There is no migration from an earlier public Spring release. Spring Boot
auto-configuration, WebFlux, built-in PATCH or relationship-linkage binding,
application/domain-error conveniences and persistence are not part of 0.1.0.

See the [WebMVC guide](https://github.com/kazforge/jsonapi-java-spring/blob/v0.1.0/docs/site/index.md)
for setup and an example.
