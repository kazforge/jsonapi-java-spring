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

## Spring 0.1.0 compatibility

| Requirement | Verified baseline |
| --- | --- |
| Java | 21+ |
| Spring Framework | 7.0.x; minimum **7.0.9** |
| Servlet API | Jakarta Servlet 6.1 |
| jsonapi-java neutral API/query | **0.4.0** |
| Consumer runtime | Jackson 3 through `jsonapi-java-jackson3:0.4.0` |

The **Spring adapter is Jackson-neutral**. Applications supply a neutral
`JsonApi` runtime; the verified consumer example constructs it with Jackson 3.
Spring Boot auto-configuration, WebFlux and persistence integrations are not
included. Use a currently patched Spring 7.0.x release; the minimum is not a
promise of future security updates.

## Dependencies

For the first Spring **0.1.0** release, add the WebMVC companion and the
application's Jackson 3 runtime from Maven Central:

```kotlin
dependencies {
    implementation("com.kazforge:jsonapi-java-spring-webmvc:0.1.0")
    implementation("com.kazforge:jsonapi-java-jackson3:0.4.0")
}
```

The WebMVC artifact exposes the neutral Core API/query dependencies and Spring
WebMVC; the Jackson 3 runtime is **application-owned**. No separate Spring BOM
or Boot starter is published.

The versioned guide is promoted to the public portal only **after** the Spring
software release has been published to Maven Central and verified from a clean
consumer.

**Next:** [Configure a WebMVC endpoint](webmvc.md).
