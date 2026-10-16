# Spring release readiness

Spring is independently versioned and released. Core artifacts are published separately;
the Spring release must consume an already published baseline from Maven Central.
This document does not configure Spring publication or establish a shared release train.

## Verified consumer baseline

The non-published [Jackson 3 WebMVC example](../examples/jackson3-webmvc/README.md)
verifies Core **0.4.0**: neutral API/query dependencies and the real Jackson 3 runtime
use the same catalog version. Its context-backed MockMvc tests exercise ID-less and
explicit-ID POST create, DTO/list responses, JSON:API negotiation, status/headers,
protocol errors, and unmarked MVC behavior. This is one consumer baseline, not a
multi-Core-version compatibility promise.

## Before a public Spring 0.x release

- Run `./gradlew clean spotlessCheck build` to verify the adapter at its minimum Spring
  version and the consumer at its separately fixed current Spring version.
- Run `./gradlew :jsonapi-java-spring-webmvc:clean :jsonapi-java-spring-webmvc:build -PspringTestVersion=current`
  to verify adapter forward compatibility without another consumer matrix leg.
- Check resolved consumer dependencies with
  `./gradlew :examples:jackson3-webmvc:dependencies --configuration runtimeClasspath`.
  Core artifacts must resolve to 0.4.0 from Maven Central, without `mavenLocal()` or substitution.
- Check adapter dependencies and eventual publication metadata: only neutral Core
  contracts belong in the Spring artifact; the example and Jackson runtime must not leak
  into it. Publication metadata verification belongs to the separate publishing work.
- Require repository formatting, compiler/nullness, tests, coverage, and CI Sonar gates.

The adapter remains at the Spring 7.0.0 production compatibility floor. Applications
should use the current supported patch. Neither this example nor Core's version
determines the Spring artifact's release number.
