---
title: "JSON:API 1.1 for Spring WebMVC"
description: "Choose the Spring Boot starter or standalone WebMVC adapter for JSON:API 1.1 servlet endpoints."
---

# JSON:API 1.1 for Spring WebMVC {#jsonapi-java-spring}

Use `@JsonApiEndpoint` to serve [JSON:API 1.1](https://jsonapi.org/format/1.1/) from selected
Spring MVC controllers or methods. The adapter handles media negotiation, request binding and
response documents through [jsonapi-java Core](https://jsonapi.kazforge.com/core/0.5.1/).
Other endpoints keep their usual Spring MVC behavior.

## Release status {#release-status}

<!-- x-release-please-start-version -->

Current release: **[0.7.0][current-release]**.

[current-release]: https://github.com/kazforge/jsonapi-java-spring/releases/tag/v0.7.0

<!-- x-release-please-end -->

Both Spring artifacts are on [Maven Central](https://central.sonatype.com/namespace/com.kazforge)
and are versioned independently of Core. Before 1.0, minor releases may introduce breaking changes;
patch releases do not.

## Choose an artifact {#choose-an-artifact}

| Artifact (`com.kazforge`) | When to use it |
| --- | --- |
| `jsonapi-java-spring-boot-starter` | **Spring Boot servlet MVC:** the starter includes the matching adapter and configures it with Boot's Jackson 3 mapper. |
| `jsonapi-java-spring-webmvc` | **Standalone WebMVC:** supply your own `JsonApi` runtime and register the adapter in your MVC configuration. No Boot or Jackson dependency is required by the adapter itself. |

## Compatibility {#compatibility}

| Requirement | Verified baseline |
| --- | --- |
| Java | 21+ |
| Servlet API | Jakarta Servlet 6.1 |
| Validation API | Jakarta Validation 3.1 |
| Spring Boot (starter) | **4.1.x** (minimum **4.1.2**) |
| Spring Framework (standalone WebMVC) | **7.0.x** (minimum **7.0.9**) |
| Verified Core baseline (`jsonapi-java-*`) | **0.5.1** (included transitively) |
| Jackson 3 runtime (`jsonapi-java-jackson3`) | `jackson-databind` **3.1.7+** |

## Application boundaries {#application-boundaries}

Your application owns the routes, authorization, persistence and query execution. You decide
which relationships clients may include and load the related resources they can see.

## Follow the guide {#follow-the-guide}

Start with [Getting started](getting-started.md) to create and read an article. Then add
[collections and partial updates](resources-and-patch.md), [queries and compound documents](queries-and-representation.md),
or [relationship linkage](relationships.md). For reference, see [Validation and errors](validation-and-errors.md)
and [Configuration](configuration.md).
