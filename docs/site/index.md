---
title: Spring WebMVC
description: JSON:API 1.1 HTTP integration for Spring Framework 7
---

# Spring WebMVC

`jsonapi-java-spring-webmvc` adds opt-in JSON:API 1.1 handling to Spring MVC
controllers: media negotiation, request/query context, resource DTO binding and
protocol errors. Ordinary MVC endpoints are unaffected.

This is a companion to the framework-neutral
[jsonapi-java guide](https://jsonapi.kazforge.com/). Spring and Core have
**independent release trains**. Your application still owns controllers,
persistence, authorization and query execution.

## Compatibility

| Requirement | Verified baseline |
| --- | --- |
| Java | 21+ |
| Spring Framework | 7.0.x; minimum **7.0.9** |
| Servlet API | Jakarta Servlet 6.1 |
| jsonapi-java API/query | Included transitively; compatible Core baseline below |
| Consumer runtime | Jackson 3, application-supplied |

The **Spring adapter is Jackson-neutral**. Applications supply a neutral
`JsonApi` runtime; the verified consumer example constructs it with Jackson 3.
Spring Boot auto-configuration, WebFlux and persistence integrations are not
included. Use a currently patched Spring 7.0.x release; the minimum is not a
promise of future security updates.

## Dependencies

Add the released WebMVC companion from Maven Central:

<!-- x-release-please-start-version -->

```kotlin
dependencies {
    implementation("com.kazforge:jsonapi-java-spring-webmvc:0.1.0")
}
```

<!-- x-release-please-end -->

WebMVC brings the neutral Core API and query libraries **transitively**;
do not add them again. It deliberately does **not** include a JSON runtime.
For the verified Jackson 3 setup, also add this line to your
`dependencies` block:

```kotlin
implementation("com.kazforge:jsonapi-java-jackson3:0.4.0")
```

The Jackson 3 version above is this Spring release's verified Core baseline.
Update it only when Spring deliberately adopts a newer Core baseline;
Spring and Core are independently versioned. See the
[Core getting-started guide](https://jsonapi.kazforge.com/getting-started/)
for the neutral runtime setup. No separate Spring BOM or Boot starter is published.

The versioned guide is promoted to the public portal only **after** the Spring
software release has been published to Maven Central and verified from a clean
consumer.

**Next:** [Configure a WebMVC endpoint](webmvc.md).
