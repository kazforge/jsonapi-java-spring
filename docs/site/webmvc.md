---
title: WebMVC setup
description: Wire jsonapi-java-spring-webmvc into an existing Spring MVC application
---

# WebMVC setup

Use an existing Spring Framework 7 Servlet-based MVC application. The companion
does not create a server or supply Spring Boot auto-configuration. Start with
the [Spring installation and dependencies](index.md#installation-and-dependencies).

## Register the adapter

Provide a `JsonApi` runtime in the application (Jackson 3 is the verified
consumer choice):

```java
@Bean
public static JsonApi jsonApi() {
    return JsonApiJackson3.jsonApi(JsonMapper.builder().build());
}
```

In the application's `WebMvcConfigurer`, register the following Spring
integration hooks:

1. Add `JsonApiMediaNegotiationInterceptor` **before**
   `JsonApiRequestContextInterceptor` in `addInterceptors`.
2. Register `JsonApiResourceHttpMessageConverter(runtime)` through
   `configureMessageConverters(HttpMessageConverters.ServerBuilder)` using
   `addCustomConverter`.
3. Register `JsonApiRequestContextArgumentResolver` in
   `addArgumentResolvers`.
4. Register `JsonApiProtocolExceptionResolver(runtime)` **before** Spring's
   default exception resolvers in `extendHandlerExceptionResolvers`.
5. Expose `JsonApiMediaNegotiationAdvice` as a Spring bean.

Here, `runtime` is the injected, application-supplied `JsonApi` bean.
For exact imports and registration code, see the release-matched
`ConsumerConfiguration` in the example linked below.

## Opt in per controller

Annotate JSON:API controllers or methods with `@JsonApiEndpoint`. Do **not**
set `consumes` or `produces` on their request mappings: Spring evaluates
those mapping conditions before the adapter's JSON:API media negotiation.

The example's `ArticleController` uses normal `@RequestBody` DTOs,
`ResponseEntity` and `List<Article>` responses. Its POST handler accepts:

```http
POST /articles
Content-Type: application/vnd.api+json
Accept: application/vnd.api+json

{"data":{"type":"articles","attributes":{"title":"Hello"}}}
```

The example responds with `201 Created`, a `Location` header and a
JSON:API resource document. The application owns entity IDs and persistence.

The adapter handles JSON:API media negotiation and known protocol/input errors;
it does not execute queries or automatically manage relationships. Unannotated
Spring MVC handlers keep their ordinary behavior.

The complete wiring and MockMvc scenarios are available in the
release-matched example:

<!-- x-release-please-start-version -->

[Tested Jackson 3 WebMVC consumer](https://github.com/kazforge/jsonapi-java-spring/tree/v0.1.0/examples/jackson3-webmvc)

<!-- x-release-please-end -->

For resource annotations, PATCH, relationships and query semantics, use the
[framework-neutral jsonapi-java guide](https://jsonapi.kazforge.com/core/0.4.0/).
