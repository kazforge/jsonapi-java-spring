---
title: WebMVC setup
description: Wire jsonapi-java-spring-webmvc into an existing Spring MVC application
---

# WebMVC setup

Use an existing Spring Framework 7 Servlet-based MVC application. The companion
does not create a server or supply Spring Boot auto-configuration. Start with
the [Spring 0.1.0 dependencies](index.md#dependencies).

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
The [complete WebMVC configuration](https://github.com/kazforge/jsonapi-java-spring/blob/v0.1.0/examples/jackson3-webmvc/src/main/java/com/kazforge/jsonapi/example/ConsumerConfiguration.java)
is the verified source for exact imports and registration code.

## Opt in per controller

Annotate JSON:API controllers or methods with `@JsonApiEndpoint`. Do **not**
set `consumes` or `produces` on their request mappings: Spring evaluates
those mapping conditions before the adapter's JSON:API media negotiation.

The [example controller](https://github.com/kazforge/jsonapi-java-spring/blob/v0.1.0/examples/jackson3-webmvc/src/main/java/com/kazforge/jsonapi/example/ArticleController.java)
uses normal `@RequestBody` DTOs, `ResponseEntity` and `List<Article>`
responses. For example, its POST handler accepts:

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

See the [tested Jackson 3 consumer](https://github.com/kazforge/jsonapi-java-spring/tree/v0.1.0/examples/jackson3-webmvc)
for the complete wiring and MockMvc scenarios. For resource annotations, PATCH,
relationships and query semantics, use the
[framework-neutral jsonapi-java guide](https://jsonapi.kazforge.com/).
