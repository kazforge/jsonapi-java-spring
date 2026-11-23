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
4. Register `JsonApiProtocolExceptionResolver(runtime)` **after** Spring's
   `@ExceptionHandler` resolver (so application advice can override errors),
   but **before** the remaining default resolvers in `extendHandlerExceptionResolvers`.
5. Expose `JsonApiMediaNegotiationAdvice` as a Spring bean.

Here, `runtime` is the injected, application-supplied `JsonApi` bean.
For application-owned error responses, also register `JsonApiErrorHttpMessageConverter(runtime)`
with `addCustomConverter` so controllers and exception handlers can return
`ResponseEntity<JsonApiErrorResponse>`.
For exact imports and registration code, see the release-matched
`ConsumerConfiguration` in the example linked below.

Create and typed resource PATCH binding require the source-aware Core 0.5.0 operations.
Custom `JsonApi` runtimes must implement them; an unsupported operation fails as a server
configuration error rather than silently retrying without source attribution.

## Opt in per controller

Annotate JSON:API controllers or methods with `@JsonApiEndpoint`. Do **not**
set `consumes` or `produces` on their request mappings: Spring evaluates
those mapping conditions before the adapter's JSON:API media negotiation.

The example's `ArticleController` uses normal `@Valid @RequestBody` DTOs,
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

[Tested Jackson 3 WebMVC consumer](https://github.com/kazforge/jsonapi-java-spring/tree/v0.4.0/examples/jackson3-webmvc)

<!-- x-release-please-end -->

## Application errors

With `JsonApiErrorHttpMessageConverter(runtime)` registered as above, return
`ResponseEntity<JsonApiErrorResponse>` containing Core `ErrorObject` values:

```java
import com.kazforge.jsonapi.core.model.ErrorObject;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import com.kazforge.jsonapi.spring.webmvc.error.JsonApiErrorResponse;
import java.util.NoSuchElementException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@JsonApiEndpoint
class ApplicationErrorController {
    @GetMapping("/articles/missing")
    public ResponseEntity<JsonApiErrorResponse> notFound() {
        return ResponseEntity.status(404)
            .body(JsonApiErrorResponse.of(ErrorObject.ofTitle("Article not found")));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<JsonApiErrorResponse> handleMissingArticle() {
        return notFound();
    }
}
```

The handler uses the same response when this controller throws `NoSuchElementException`.
Application-owned `@RestControllerAdvice` can return it too; the originally matched handler
must be opted in with `@JsonApiEndpoint`.

The application owns the HTTP status and error content. The adapter writes the supplied errors
as a JSON:API error document; it does not infer or rewrite error `status` members from the HTTP status.

## Resource PATCH

Use a typed DTO with `PatchPresence<T>` members for an opted-in resource PATCH handler:

```java
@JsonApiResource(type = "articles")
public record ArticlePatch(
    @JsonApiId String id,
    @JsonApiAttribute PatchPresence<String> title) {}

@JsonApiEndpoint
@PatchMapping("/articles/{id}")
public ResponseEntity<Void> update(@Valid @RequestBody ArticlePatch patch) {
    articleService.update(patch.id(), patch.title());
    return ResponseEntity.noContent().build();
}
```

`PatchPresence` is `com.kazforge.jsonapi.patch.PatchPresence`; resource annotations are in
`com.kazforge.jsonapi.annotation`. The application service owns authorization and applying changes:
`Omitted` means leave the property unchanged, while `Present` carries a supplied value, possibly null.

Replace the converter registration above with one that resolves the addressed resource from the
route. For this application's `/articles/{id}` endpoint:

```java
builder.addCustomConverter(new JsonApiResourceHttpMessageConverter(runtime, request -> {
    var variables = (Map<?, ?>) request.getAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    return new EndpointIdentity("articles",
        Objects.requireNonNull((String) Objects.requireNonNull(variables).get("id")));
}));
```

Here `builder` is Spring's `HttpMessageConverters.ServerBuilder`, `EndpointIdentity` is
`com.kazforge.jsonapi.core.validation.EndpointIdentity`, and `HandlerMapping` is
`org.springframework.web.servlet.HandlerMapping`; `Map` and `Objects` are from `java.util`.
For multiple resource routes, resolve the appropriate type and ID in this application callback;
never take them from the request body. A missing callback or null result is a server configuration
error. The callback is not used for POST or responses.

```http
PATCH /articles/42
Content-Type: application/vnd.api+json

{"data":{"type":"articles","id":"42","attributes":{"title":null}}}
```

This binds `title` as `Present(null)`; omitting `title` binds `Omitted`. A body type/id that conflicts
with the route produces 409. Invalid update documents and unknown supplied PATCH members produce
400 through the protocol resolver. `@Valid` enables Spring's normal body validation.

Infrastructure handlers can explicitly declare `@RequestBody PatchCommand<Article>` instead.
Its resource type must be fully resolved; raw commands, wildcards, unresolved generics, and bulk
targets are configuration errors. Neither path applies mutations or projects `included` resources
into application state. Dedicated relationship-linkage operations remain separate from resource PATCH.

## Request-body validation

Use `@Valid @RequestBody` (`jakarta.validation.Valid`) for JSON:API resource create
and typed PATCH DTOs. The
application supplies its Jakarta Validation provider; the adapter does not bundle one.
On an opted-in controller, failed body validation defaults to HTTP **400** with
`application/vnd.api+json` and safe `INVALID_FIELD` / `INVALID_RESOURCE` errors.
Core 0.5.0 supplies request-local source attribution for logical Java property paths,
including renamed and nested members. A pointer identifies a supplied member (or a
proven supplied ancestor); when no safe location exists, the error omits its pointer.
Rejected values, constraint messages and internal details are not returned.

For type-use constraints or cascading on `PatchPresence<T>`, register the provided
`PatchPresenceValueExtractor` with the application's validator. In a
`WebMvcConfigurer` using `LocalValidatorFactoryBean`:

```java
import com.kazforge.jsonapi.spring.webmvc.validation.PatchPresenceValueExtractor;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@Override
public LocalValidatorFactoryBean getValidator() {
    var validator = new LocalValidatorFactoryBean();
    validator.setConfigurationInitializer(configuration ->
        configuration.addValueExtractor(new PatchPresenceValueExtractor()));
    return validator;
}
```

For example, `PatchPresence<@NotBlank String>` validates supplied values, including
explicit `null`; omitted members are not emitted by the extractor. Class-level
constraints may still check the PATCH DTO, and a supplied atomic replacement
does not exempt its missing nested members from validation.

Application advice can override the default status while reusing the same attributed
errors. Keep Spring's `@ExceptionHandler` resolver ahead of the JSON:API protocol
resolver, as in the release-matched example:

```java
import com.kazforge.jsonapi.spring.webmvc.error.JsonApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ArticleController.class)
class ValidationAdvice {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<JsonApiErrorResponse> invalid(
        HttpServletRequest request, MethodArgumentNotValidException failure)
        throws MethodArgumentNotValidException {
        var errors = JsonApiErrorResponse.fromValidation(request, failure)
            .orElseThrow(() -> failure);
        return ResponseEntity.status(422).body(errors);
    }
}
```

`fromValidation` only applies to the same successfully bound create or typed PATCH
body in the current dispatch. It returns empty for unrelated validation failures.
Generated validation errors omit their optional per-error `status` member; the
application owns the HTTP response status. Ordinary Spring MVC endpoints are unchanged.

For resource annotations, detailed PATCH, relationships and query semantics, use the
[framework-neutral jsonapi-java guide](https://jsonapi.kazforge.com/core/0.5.0/).
