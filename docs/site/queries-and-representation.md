---
title: "Queries and representation"
description: "Bind typed filter and pagination DTOs with @JsonApiQueryParams, configure IncludePolicy for compound documents, and apply sparse fieldsets."
---

# Bind typed query parameters and configure compound documents {#queries-and-representation}

## Typed query parameter binding (`@JsonApiQueryParams`) {#typed-query-parameter-binding}

On an `@JsonApiEndpoint` handler, use `@JsonApiQueryParams` to bind the URL query string to a
record or single-public-constructor class. Form and body parameters are excluded. Use Spring's
`@BindParam` for exact parameter names, including brackets, and `@Valid` to validate the result.

### Filtering, numbered pagination, and sort {#filtering-numbered-pagination-and-sort}

Replace the [`GET /articles` collection handler](resources-and-patch.md#collections-and-bodyless-responses)
with `search` below to accept filters and page numbers on that same route.

```java
import com.kazforge.jsonapi.query.JsonApiQuery;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiQueryParams;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.BindParam;
import org.springframework.web.bind.annotation.GetMapping;

public enum Status { DRAFT, PUBLISHED }

public record ArticleQuery(
    @BindParam("filter[status]") @Nullable Status status,
    @BindParam("page[number]") @NotNull @Min(1) Integer number,
    @BindParam("page[size]") @NotNull @Min(1) Integer size,
    JsonApiQuery requested) {}

// Inside @RestController @RequestMapping("/articles") @JsonApiEndpoint ArticleController:
@GetMapping
public List<Article> search(@Valid @JsonApiQueryParams ArticleQuery query) {
    return articleService.search(query);
}
```

For this request:

```http
GET /articles?filter[status]=PUBLISHED&page[number]=2&page[size]=20&sort=-title
Accept: application/vnd.api+json
```

- `query.status()` binds to `Status.PUBLISHED`, `query.number()` to `2`, and `query.size()` to `20`.
- The optional, unannotated `JsonApiQuery` component (`query.requested()`) receives the parsed query:
  - `query.requested().sort()` exposes the ordered `List<SortField>` (here
    `SortField.descending("title")`).
  - `query.requested().selection()` exposes the requested `RepresentationSelection`
    (`includePaths()` and `fieldsets()`).
- Your application service executes the query and may return `JsonApiResponse<List<Article>>` to
  attach pagination `links` and `meta` (see
  [Resources and PATCH](resources-and-patch.md#top-level-metadata-and-links)).

### Cursor pagination and repeated parameters {#cursor-pagination-and-repeated-parameters}

Add a separate `GET /articles/feed` handler for cursor pagination. Here `List<Status>` accepts
several `filter[status]` values, while `page[after]` accepts a single opaque cursor:

```java
public record ArticleFeedQuery(
    @BindParam("filter[status]") List<Status> statuses,
    @BindParam("page[after]") @Nullable String after) {}

@GetMapping("/feed")
public List<Article> feed(@Valid @JsonApiQueryParams ArticleFeedQuery query) {
    return articleService.feed(query);
}
```

```http
GET /articles/feed?filter[status]=DRAFT&filter[status]=PUBLISHED&page[after]=art-42
Accept: application/vnd.api+json
```

`query.statuses()` receives `List.of(Status.DRAFT, Status.PUBLISHED)` in request order, and
`query.after()` receives `"art-42"`. Lists retain duplicates; a single value is not split on commas.

### Constructor and conversion rules {#constructor-and-conversion-rules}

- **Target type:** Use a Java `record` (bound via its canonical constructor) or a class with **one**
  public constructor. No setters or nested property paths are bound; dots and brackets in
  `@BindParam` are literal parameter names.
- **Declared inputs:** Every constructor parameter must carry `@BindParam("<name>")` (for
  `filter[...]`, `page[...]`, or custom opaque parameters), except at most one unannotated
  `JsonApiQuery` parameter. Do not declare `include`, `sort`, `fields`, or `fields[...]` with
  `@BindParam`; read those from `JsonApiQuery`.
- **Supported value types:** Use boxed simple reference types convertible by Spring MVC's
  `ConversionService` (such as `String`, `Integer`, `Long`, `Boolean`, `UUID`, or an `enum`—never
  primitives like `int` or `Class`), or `List<T>` of a concrete simple reference type.
- **Missing and empty values:** Missing scalars bind to `null`; missing `List<T>` parameters bind to
  `List.of()`. Explicit empty strings (`?page[after]=`) bind to `""` for `String` parameters, while
  non-`String` types must convert to a non-null result.
- **Protocol errors (`400 Bad Request`):**
  - Sending multiple occurrences for a scalar parameter fails with `INVALID_PARAMETER_CARDINALITY`.
  - Sending an opaque query parameter not declared on the DTO fails with
    `UNSUPPORTED_QUERY_PARAMETER`.
  - Conversion failures and `@Valid` violations fail with `INVALID_QUERY_PARAMETER` (see
    [Validation and errors](validation-and-errors.md#query-parameter-validation)).

When an endpoint needs custom query binding rules, accept `JsonApiRequestContext` directly as a
handler parameter (see [Configuration](configuration.md#inspecting-request-context-directly)).

## Include related resources {#include-related-resources}

Core's default `RepresentationPolicy` uses `IncludePolicy.denyAll()`. Even when an `Article` carries
a non-null `Person("7", "Ada")`, the default runtime writes only the relationship linkage
(`"type": "people", "id": "7"`) and rejects `?include=author`. The library never automatically
enables all-relationship include traversal or loads entities from a database.

To permit `?include=author` on `articles`, configure a `RepresentationPolicy` with an explicit
`IncludePolicy`:

=== "Spring Boot"

    Expose a `RepresentationPolicy` bean. `JsonApiWebMvcAutoConfiguration` automatically applies it
    when building the default `JsonApi` runtime from Boot's `JsonMapper`:

    ```java
    import com.kazforge.jsonapi.representation.IncludePolicy;
    import com.kazforge.jsonapi.representation.RelationshipAllowance;
    import com.kazforge.jsonapi.representation.RepresentationPolicy;
    import java.util.Set;
    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;

    @Configuration(proxyBeanMethods = false)
    public class JsonApiPolicyConfig {
        @Bean
        public RepresentationPolicy representationPolicy() {
            return RepresentationPolicy.defaults()
                .withIncludePolicy(IncludePolicy.allowing(Set.of(
                    RelationshipAllowance.of("articles", "author"))));
        }
    }
    ```

=== "Standalone WebMVC"

    Pass the `RepresentationPolicy` to `JsonApiJackson3.builder(mapper)` when creating your
    `JsonApi` bean:

    ```java
    @Bean
    public static JsonApi jsonApi() {
        var policy = RepresentationPolicy.defaults()
            .withIncludePolicy(IncludePolicy.allowing(Set.of(
                RelationshipAllowance.of("articles", "author"))));
        return JsonApiJackson3.builder(JsonMapper.builder().build())
            .representationPolicy(policy)
            .build();
    }
    ```

Now consider a request that combines filtering, pagination, `include=author`, and sparse fieldsets
(`fields[articles]` and `fields[people]`):

```http
GET /articles?filter[status]=PUBLISHED&page[number]=1&page[size]=20&include=author&fields[articles]=title,author&fields[people]=name
Accept: application/vnd.api+json
```

When `articleService.search(query)` loads the authorized `Person("7", "Ada")` on each returned
`Article`, `JsonApiResourceHttpMessageConverter` automatically passes the request's
`RepresentationSelection` to Core's writer and produces the compound document:

```json
{
  "data": [
    {
      "type": "articles",
      "id": "42",
      "attributes": {
        "title": "Working with JSON:API"
      },
      "relationships": {
        "author": {
          "data": {
            "type": "people",
            "id": "7"
          }
        }
      }
    }
  ],
  "included": [
    {
      "type": "people",
      "id": "7",
      "attributes": {
        "name": "Ada"
      }
    }
  ]
}
```

There is no manual serialization step in the controller. See Core's
[Query and representation guide](https://jsonapi.kazforge.com/core/0.5.1/query-and-representation/)
and [Representation policy configuration](https://jsonapi.kazforge.com/core/0.5.1/configuration/#representation-policy)
for multi-hop includes, `FieldPolicy`, and traversal depth and count limits.

## Read-time vs. write-time `include` errors {#read-time-vs-write-time-include-errors}

Core evaluates `include` paths against `RepresentationPolicy` when serializing the response DTO:

- **Resource `GET` and `HEAD`:** An unknown or disallowed `include` path produces `400 Bad Request`
  with code `DISALLOWED_INCLUDE_PATH` and `source.parameter: "include"`.
- **Resource `POST` and `PATCH`:** The same failure produces `500 Internal Server Error`
  (`RESPONSE_SERIALIZATION_FAILED`): the mutation may already have succeeded. If an include
  restriction should prevent the mutation, check the query before changing state.

**Next:** Continue to [Relationship linkage](relationships.md) to expose dedicated to-one and
to-many `/relationships/...` endpoints.
