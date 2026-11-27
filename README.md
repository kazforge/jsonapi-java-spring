# jsonapi-java-spring

The Spring MVC companion to [jsonapi-java](https://github.com/kazforge/jsonapi-java) publishes
two artifacts on Maven Central, both targeting Core **0.5.1**:

- **`jsonapi-java-spring-boot-starter`** — Recommended for Spring Boot **4.1.x** (minimum **4.1.2**)
  servlet MVC applications. Configures the adapter, Jackson 3 runtime, and PATCH validation.
- **`jsonapi-java-spring-webmvc`** — Jackson-neutral HTTP adapter for standalone Spring Framework
  **7.0.x** (minimum **7.0.9**) servlet MVC applications. Supply a `JsonApi` runtime and register
  the adapter in your MVC configuration.

Applications retain ownership of controllers, persistence, authorization, query execution, and
representation policy. Unannotated Spring MVC endpoints keep their ordinary behavior.

## Documentation

The [release-owned Spring WebMVC guide](docs/site/index.md) covers compatibility, Spring Boot and
standalone WebMVC setup, and endpoint usage. Released versions are published to the
[JSON:API for Java documentation portal](https://jsonapi.kazforge.com/) from verified software
release tags, not from `main`.

See [repository architecture and decisions](docs/README.md) and
[release readiness](docs/release.md).

Two non-published consumer examples exercise real-runtime HTTP integration against published Core
0.5.1:

- [`examples/boot-webmvc`](examples/boot-webmvc) — Runnable Spring Boot application and embedded
  HTTP integration tests using `jsonapi-java-spring-boot-starter`.
- [`examples/jackson3-webmvc`](examples/jackson3-webmvc/README.md) — Explicit standalone
  `WebMvcConfigurer` wiring and MockMvc integration tests using `jsonapi-java-spring-webmvc` and
  `jsonapi-java-jackson3`.

## Development

Install JDK 21 and use the committed wrapper:

```shell
./gradlew clean build
./gradlew spotlessApply
./gradlew spotlessCheck
```

`build` includes formatting checks and validation of the reusable build conventions. See
[CONTRIBUTING.md](CONTRIBUTING.md) for the module quality policy and
[docs/security.md](docs/security.md) for vulnerability triage and merge protection.

Licensed under [Apache-2.0](LICENSE).
