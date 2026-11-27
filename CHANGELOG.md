# Changelog

## [0.7.0](https://github.com/kazforge/jsonapi-java-spring/compare/v0.6.0...v0.7.0) (2026-11-27)


### Features

* **boot:** auto-configure JSON:API for servlet MVC applications ([0754337](https://github.com/kazforge/jsonapi-java-spring/commit/0754337efd70926f607de976a44aef0f87b76894))

## [0.6.0](https://github.com/kazforge/jsonapi-java-spring/compare/v0.5.0...v0.6.0) (2026-11-25)


### Features

* **webmvc:** bind typed query DTOs from the parsed request ([a0031f4](https://github.com/kazforge/jsonapi-java-spring/commit/a0031f4463e8d4db2e00f2810e22c667e703843f))

## [0.5.0](https://github.com/kazforge/jsonapi-java-spring/compare/v0.4.0...v0.5.0) (2026-11-24)


### Features

* **webmvc:** bind relationship linkage endpoints ([a3278d1](https://github.com/kazforge/jsonapi-java-spring/commit/a3278d150c021ad3fb8c1d5001756c456bf1ed00))

## [0.4.0](https://github.com/kazforge/jsonapi-java-spring/compare/v0.3.0...v0.4.0) (2026-11-23)


### Features

* **webmvc:** translate resource body validation errors ([adda9ec](https://github.com/kazforge/jsonapi-java-spring/commit/adda9ec7f7ed3da930bb58957960dffa56802378))

## [0.3.0](https://github.com/kazforge/jsonapi-java-spring/compare/v0.2.0...v0.3.0) (2026-11-16)


### Features

* **webmvc:** add application error responses ([edccb40](https://github.com/kazforge/jsonapi-java-spring/commit/edccb40fd913d9cb0e1ad05f16696953c26aaebf))

## [0.2.0](https://github.com/kazforge/jsonapi-java-spring/compare/v0.1.0...v0.2.0) (2026-11-04)

### Features

* **webmvc:** add presence-aware resource PATCH binding ([bb1d5a1](https://github.com/kazforge/jsonapi-java-spring/commit/bb1d5a15a8f4ebe0257022977f09915e928675ea))

Resource PATCH endpoints can now bind typed `PatchPresence<T>` DTOs or explicit
`PatchCommand<T>` values. The adapter validates resource identity against the endpoint;
applications still own authorization and applying changes.

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
